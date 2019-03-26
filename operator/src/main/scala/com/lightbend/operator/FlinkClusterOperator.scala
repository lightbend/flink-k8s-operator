package com.lightbend.operator

import java.util.concurrent.atomic.AtomicBoolean

import com.lightbend.operator.types.FlinkCluster
import io.radanalytics.operator.common.{AbstractOperator, EntityInfo, Operator}
import org.slf4j.LoggerFactory
import Constants._
import com.fasterxml.jackson.databind.ObjectMapper
import io.radanalytics.operator.common.crd.{InfoClass}
import io.radanalytics.operator.resource.LabelsHelper._

import scala.collection.mutable.{Map => MMap}
import scala.collection.JavaConverters._


@Operator(forKind = classOf[FlinkCluster], prefix = "lightbend.com", crd=true)
class FlinkClusterOperator extends AbstractOperator[FlinkCluster] {

  private val log = LoggerFactory.getLogger(classOf[AbstractOperator[_ <: EntityInfo]].getName)

  // Those can not created here because namespace is initiated later
  private val clusters = MMap[String, RunningClusters]()       // In order to support multiple namespaces (all namespace) we need map here, namespace cas change
  private var deployer : Option[KubernetesFlinkClusterDeployer] = Option.empty

  // Init - initialize logger
  override protected def onInit(): Unit = {
    log.info(s"${this.entityName} operator default flink image ${Constants.getDefaultFlinkImage}")
  }

  // Add event, just deploy a new cluster
  override def onAdd(cluster: FlinkCluster): Unit = {
    log.info(s"Flink operator processing add event for a cluster ${cluster.getName} in namespace $namespace")
    val list =getDeployer().getResourceList(cluster, namespace)
    client.resourceList(list).inNamespace(namespace).createOrReplace
    getClusters().put(cluster)
  }

  // Delete event, just delete cluster
  override def onDelete(cluster: FlinkCluster): Unit = {
    log.info(s"Flink operator processing delete event for a cluster ${cluster.getName} in namespace $namespace")
    val name = cluster.getName
    client.services.inNamespace(namespace).withLabels(getDeployer().getDefaultLabels(name).asJava).delete
    client.replicationControllers.inNamespace(namespace).withLabels(getDeployer().getDefaultLabels(name).asJava).delete
    client.pods.inNamespace(namespace).withLabels(getDeployer().getDefaultLabels(name).asJava).delete
    getClusters().delete(name)
  }

  // Modify event
  override protected def onModify(newCluster: FlinkCluster): Unit = {
    log.info(s"Flink operator processing modify event for a cluster ${newCluster.getName} in namespace $namespace")
    val name = newCluster.getName
    // Verify that cluster exists
    val existingCluster = getClusters().getCluster(name)
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
      val list = getDeployer().getResourceList(newCluster, namespace)
      client.resourceList(list).inNamespace(namespace).createOrReplace
      getClusters().update(newCluster)
    }
  }

  override protected def convertCr (info : InfoClass[_]): FlinkCluster = {
    log.info(s"Converting new resource - source $info")
    val name = info.getMetadata.getName
    val namespace = info.getMetadata.getNamespace
    val mapper = new ObjectMapper
    var infoSpec = mapper.convertValue(info.getSpec, classOf[FlinkCluster])
    if (infoSpec.getName == null) infoSpec.setName(name)
    if (infoSpec.getNamespace == null) infoSpec.setNamespace(namespace)
    infoSpec
  }

  override protected def fullReconciliation() : Unit = {
    //        1. get all the cm/cr and call it desiredSet
    //        2. get all the clusters and call it actualSet (and update the this.clusters)
    //        3. desiredSet - actualSet = toBeCreated
    //        4. actualSet - desiredSet = toBeDeleted
    //        5. modify / scale

    log.info(s"Running full reconciliation for namespace $namespace and kind $entityName..")

    val change: AtomicBoolean = new AtomicBoolean(false)
    // Get desired clusters
    val desired = super.getDesiredSet.asScala.map(cluster => (FullName(cluster.getName, cluster.getNamespace) -> cluster)).toMap
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
        log.info(s"creating cluster $cluster")
        val currentnamespace = namespace
        namespace = cluster.namespace
        onAdd(desired.get(cluster).get)
        namespace = currentnamespace
     })

    // delete old
    toBeDeleted.foreach(cluster => {
      val c = new FlinkCluster
      c.setName(cluster.name)
      log.info(s"deleting cluster $cluster")
      val currentnamespace = namespace
      namespace = cluster.namespace
      onDelete(c)
      namespace = currentnamespace
    })

    // rescale
    desired.foreach(cluster => {
      val desiredWorkers = getFlinkParameters(cluster._2).worker_instances
      val actualWorkers = actual.get(cluster._1).getOrElse(0)
      if (desiredWorkers != actualWorkers) {
        change.set(true)
        val currentnamespace = namespace
        namespace = cluster._1.namespace
        rescaleCluster(cluster._2)
        namespace = currentnamespace
      }
    })

    // first reconciliation after (re)start -> update the clusters instance
    if (!fullReconciliationRun) {
      val clust = getClusters()
      clust.resetMetrics()
      desired.values.foreach(cluster => clust.put(cluster))
    }

    // Log result
    if (!change.get)
      log.info("no change was detected during the reconciliation")
    MetricsHelper.reconciliationsTotal.labels(namespace).inc()
  }

  // Get amount of workers per cluster
  private def getActual: Map[FullName, Int] = {
    // Get all replication controllers
    val controllers = client.replicationControllers()
    // Filter by namespace
    val namespacedcontrollers = if ("*" == namespace) controllers.inAnyNamespace else controllers.inNamespace(namespace)
    // Get all task managers
    val labels = Map("server" -> "flink", "component" -> OPERATOR_TYPE_WORKER_LABEL, (prefix + OPERATOR_KIND_LABEL, entityName))
    // Get workers per name
    namespacedcontrollers.withLabels(labels.asJava).list.getItems.asScala.map(rc =>
      FullName(rc.getMetadata.getLabels.get(prefix + entityName), rc.getMetadata.getNamespace) -> rc.getSpec.getReplicas.intValue()).toMap
  }

  // Rescale cluster
  private def rescaleCluster(newCluster: FlinkCluster) : Unit = {
    val newWorkers = getFlinkParameters(newCluster).worker_instances
    log.info(s"Cluster ${newCluster.getName} scaling to $newWorkers taskmanagers")
    client.replicationControllers.inNamespace(namespace).withName(s"${newCluster.getName}-taskmanager").scale(newWorkers)
    getClusters().update(newCluster)
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

  private def getClusters(): RunningClusters = clusters.get(namespace) match {
    case Some(c) => c  // already exists
    case _ =>
      val c = new RunningClusters(namespace)
      clusters += (namespace -> c)
      c
  }

  private def getDeployer(): KubernetesFlinkClusterDeployer = deployer match {
    case Some(d) => d     // Already exists
    case _ =>             // Create a new one
      val d = new KubernetesFlinkClusterDeployer(client, entityName, prefix)
      deployer = Option(d)
      d
  }
}

case class FullName(name: String, namespace: String){
  def equal(other: AnyRef): Boolean = {
    other match {
      case fn : FullName => (name == fn.name) && (namespace == fn.namespace)
      case _ => false
    }
  }
}