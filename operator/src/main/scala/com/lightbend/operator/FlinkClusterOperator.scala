package com.lightbend.operator

import java.util.concurrent.atomic.AtomicBoolean

import com.lightbend.operator.types.FlinkCluster
import io.radanalytics.operator.common.{AbstractOperator, EntityInfo, Operator}
import org.slf4j.LoggerFactory
import Constants._
import io.radanalytics.operator.resource.LabelsHelper._

import scala.collection.JavaConverters._


@Operator(forKind = classOf[FlinkCluster], prefix = "lightbend.com", crd=true)
class FlinkClusterOperator extends AbstractOperator[FlinkCluster] {

  private val log = LoggerFactory.getLogger(classOf[AbstractOperator[_ <: EntityInfo]].getName)

  // Those can not created here because namespace is initiated later
  private var clusters : Option[RunningClusters] = Option.empty
  private var deployer : Option[KubernetesFlinkClusterDeployer] = Option.empty

  // Init - initialize logger
  override protected def onInit(): Unit = {
    log.info(s"${this.entityName} operator default flink image ${Constants.getDefaultFlinkImage}")
  }

  // Add event, just deploy a new cluster
  override def onAdd(cluster: FlinkCluster): Unit = {
    log.info(s"Flink operator processing add event for a cluster ${cluster.getName}")
    setDeployer
    setClusters
    val list =deployer.get.getResourceList(cluster)
    client.resourceList(list).inNamespace(namespace).createOrReplace
    clusters.get.put(cluster)
  }

  // Delete event, just delete cluster
  override def onDelete(cluster: FlinkCluster): Unit = {
    log.info(s"Flink operator processing delete event for a cluster ${cluster.getName}")
    setDeployer
    setClusters
    val name = cluster.getName
    client.services.inNamespace(namespace).withLabels(deployer.get.getDefaultLabels(name).asJava).delete
    client.replicationControllers.inNamespace(namespace).withLabels(deployer.get.getDefaultLabels(name).asJava).delete
    client.pods.inNamespace(namespace).withLabels(deployer.get.getDefaultLabels(name).asJava).delete
    clusters.get.delete(name)
  }

  // Modify event
  override protected def onModify(newCluster: FlinkCluster): Unit = {
    log.info(s"Flink operator processing modify event for a cluster ${newCluster.getName}")
    setDeployer
    setClusters
    val name = newCluster.getName
    // Verify that cluster exists
    val existingCluster = clusters.get.getCluster(name)
    if (null == existingCluster) {
      log.error(s"something went wrong, unable to scale existing cluster $name. Perhaps it wasn't deployed properly.")
      return
    }
    // Check if this is just rescale
    if (isOnlyScale(existingCluster, newCluster)) {
      log.info(s"Flink operator processing modify event for a cluster ${newCluster.getName}. Rescaling only")
      rescaleCluster(newCluster)
    }

    // Recreate cluster with new parameters
    else {
      log.info(s"Recreating cluster  $name")
      val list = deployer.get.getResourceList(newCluster)
      client.resourceList(list).inNamespace(namespace).createOrReplace
      clusters.get.update(newCluster)
    }
  }

  override protected def fullReconciliation() : Unit = {
    //        1. get all the cm/cr and call it desiredSet
    //        2. get all the clusters and call it actualSet (and update the this.clusters)
    //        3. desiredSet - actualSet = toBeCreated
    //        4. actualSet - desiredSet = toBeDeleted
    //        5. modify / scale

    if ("*".equals(namespace)) {
      log.info("Skipping full reconciliation for namespace '*' (not supported)")
      return
    }
    log.info(s"Running full reconciliation for namespace $namespace and kind $entityName..")

    setDeployer
    setClusters
    val change: AtomicBoolean = new AtomicBoolean(false)
    // Get desired clusters
    val desired = super.getDesiredSet.asScala.map(cluster => (cluster.getName -> cluster)).toMap
    // Get actual workers
    val actual = getActual

    log.debug(s"desired set: $desired")
    log.debug(s"actual: $actual")

    // Calculate to be created and deleted
    val toBeCreated = desired.keys.toList.filterNot(actual.keys.toSet)
    val toBeDeleted = actual.keys.toList.filterNot(desired.keys.toSet)

    // Report tasks
    if (!toBeCreated.isEmpty) {
      log.info(s"toBeCreated: $toBeCreated")
      change.set(true)
    }
    if (!toBeDeleted.isEmpty) {
      log.info(s"toBeDeleted: $toBeDeleted")
      change.set(true)
    }

    // add new
    toBeCreated.foreach(cluster => {
        log.info("creating cluster {}", cluster)
        onAdd(desired.get(cluster).get)
     })

    // delete old
    toBeDeleted.foreach(cluster => {
      val c = new FlinkCluster
      c.setName(cluster)
      log.info(s"deleting cluster $cluster")
      onDelete(c)
    })

    // rescale
    desired.values.foreach(cluster => {
      val desiredWorkers = getFlinkParameters(cluster).worker_instances
      val actualWorkers = actual.get(cluster.getName).getOrElse(0)
      if (desiredWorkers != actualWorkers) {
        change.set(true)
        rescaleCluster(cluster)
      }
    })

    // first reconciliation after (re)start -> update the clusters instance
    if (!fullReconciliationRun) {
      clusters.get.resetMetrics()
      desired.values.foreach(cluster => clusters.get.put(cluster))
    }

    // Log result
    if (!change.get)
      log.info("no change was detected during the reconciliation")
    MetricsHelper.reconciliationsTotal.labels(namespace).inc()
  }

  // Get amount of workers per cluster
  private def getActual: Map[String, Integer] = {
    // Get all replication controllers
    val aux1 = client.replicationControllers
    // Filter by namespace
    val aux2 = if ("*" == namespace) aux1.inAnyNamespace else aux1.inNamespace(namespace)
    // Get all task managers
    val labels = Map("server" -> "flink", "component" -> OPERATOR_TYPE_WORKER_LABEL, (prefix + OPERATOR_KIND_LABEL, entityName))
    val workerRcs = aux2.withLabels(labels.asJava).list.getItems.asScala
    // Get workers per name
    workerRcs.map(rc => rc.getMetadata.getLabels.get(prefix + entityName) -> rc.getSpec.getReplicas).toMap
  }

  // Rescale cluster
  private def rescaleCluster(newCluster: FlinkCluster) : Unit = {
    val newWorkers = getFlinkParameters(newCluster).worker_instances
    log.info(s"Cluster ${newCluster.getName} scaling to $newWorkers taskmanagers")
    client.replicationControllers.inNamespace(namespace).withName(s"${newCluster.getName}-taskmanager").scale(newWorkers)
    clusters.get.update(newCluster)
  }

  /**
    * This method verifies if any two instances of FlinkCluster are the same ones up to the number of
    * workers. This way we can call the scale instead of recreating the whole cluster.
    *
    * @param oldC the first instance of FlinkCluster we are comparing
    * @param newC the second instance of FlinkCluster we are comparing
    * @return true if both instances represent the same flink cluster but differs only in number of workers (it is safe
    *         to call scale method)
    */
  private def isOnlyScale(oldC: FlinkCluster, newC: FlinkCluster) : Boolean = {

    // Get parameters
    val oldP = getFlinkParameters(oldC)
    val newP = getFlinkParameters(newC)
    newC.getFlinkConfiguration.put("num_taskmanagers", oldP.worker_instances.toString)
    oldC == newC
  }

  // Ensure that both deployer and clusters map are created
  private def setDeployer: Unit = deployer match {
    case Some(d) => // already exists
    case _ => deployer = Some(new KubernetesFlinkClusterDeployer(client, entityName, prefix, namespace))
  }

  private def setClusters: Unit = clusters match {
    case Some(c) => // already exists
    case _ => clusters = Some(new RunningClusters(namespace))
  }
}