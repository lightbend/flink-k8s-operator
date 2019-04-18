package com.lightbend.operator


import com.lightbend.operator.types.FlinkCluster
import io.radanalytics.operator.common.{AbstractOperator, EntityInfo, Operator}
import org.slf4j.LoggerFactory
import Constants._
import io.radanalytics.operator.resource.LabelsHelper._

import scala.collection.mutable.{Map => MMap}
import scala.collection.JavaConverters._


@Operator(forKind = classOf[FlinkCluster], prefix = "lightbend.com", crd=true)
class FlinkClusterOperator extends AbstractOperator[FlinkCluster] {

  private val log = LoggerFactory.getLogger(classOf[AbstractOperator[_ <: EntityInfo]].getName)

  // Those can not created here because namespace is initiated later
  private val clusters = MMap[String, RunningClusters]()       // In order to support multiple namespaces (all namespace) we need map here
  private var deployer : Option[KubernetesFlinkClusterDeployer] = Option.empty

  // Init - initialize logger
  override protected def onInit(): Unit = {
    log.info(s"${this.entityName} operator default flink image ${Constants.getDefaultFlinkImage}")
  }

  // Add event, just deploy a new cluster
  override def onAdd(cluster: FlinkCluster): Unit = {
    onAddInternal(cluster, namespace, DeploymentOptions())
  }

  private def onAddInternal(cluster: FlinkCluster, ns: String, option: DeploymentOptions) : Unit = {
    log.info(s"Flink operator processing add event for a cluster ${cluster.getName} in namespace $ns")
    val list = getDeployer().getResourceList(cluster, ns, option)
    client.resourceList(list).inNamespace(ns).createOrReplace
    getClusters(ns).put(cluster)
  }

  // Delete event, just delete cluster
  override def onDelete(cluster: FlinkCluster): Unit = {
   onDeleteInternal(cluster, namespace)
  }

  private def onDeleteInternal(cluster: FlinkCluster, ns : String): Unit = {
    log.info(s"Flink operator processing delete event for a cluster ${cluster.getName} in namespace $ns")
    val name = cluster.getName
    client.services.inNamespace(ns).withLabels(getDeployer().getDefaultLabels(name).asJava).delete
    client.replicationControllers.inNamespace(ns).withLabels(getDeployer().getDefaultLabels(name).asJava).delete
    client.pods.inNamespace(ns).withLabels(getDeployer().getDefaultLabels(name).asJava).delete
    getClusters(ns).delete(name)
  }

  // Modify event
  override protected def onModify(newCluster: FlinkCluster): Unit = {
    log.info(s"Flink operator processing modify event for a cluster ${newCluster.getName} in namespace $namespace")
    val name = newCluster.getName
    // Get existing cluster
    val existingCluster = getClusters(namespace).getCluster(name)
    existingCluster match {
      case v if (v == null) =>
        log.error(s"something went wrong, unable to modify existing cluster $name. Perhaps it wasn't deployed properly. Redeploying")
        onAddInternal(newCluster, namespace, DeploymentOptions())
      case _ =>
        isOnlyScale(existingCluster, newCluster) match {
          case true => // This is just rescale
            log.info(s"Flink operator processing modify event for a cluster ${newCluster.getName}. Rescaling only")
            rescaleCluster(newCluster, namespace)
          case _ =>     // Recreate cluster with new parameters
            log.info(s"Recreating cluster  $name")
            val list = getDeployer().getResourceList(newCluster, namespace, DeploymentOptions())
            client.resourceList(list).inNamespace(namespace).createOrReplace
            getClusters(namespace).update(newCluster)
        }
    }
  }

  override protected def fullReconciliation() : Unit = {
    //        1. get all defined crd and call it desiredSet
    //        2. get all deployed clusters and call it actualSet (and update the this.clusters)
    //        3. desiredSet - actualSet = toBeCreated
    //        4. actualSet - desiredSet = toBeDeleted
    //        5. repair / scale

    log.info(s"Running full reconciliation for namespace $namespace and kind $entityName..")

    var change = false
    // Get desired clusters
    val desired = super.getDesiredSet.asScala.map(cluster => (FullName(cluster.getName, cluster.getNamespace) -> cluster)).toMap
    // Get actual workers
    val actual = getDeployed

    log.debug(s"desired set: $desired")
    log.debug(s"actual: $actual")

    // Calculate to be created and deleted
    val toBeCreated = desired.keys.toList.filterNot(actual.keys.toSet)
    val toBeDeleted = actual.keys.toList.filterNot(desired.keys.toSet)

    // Process creation
    toBeCreated.isEmpty match {
      case true =>
      case _ => // We need to create missing ones
        log.info(s"Reconciliation - toBeCreated: $toBeCreated")
        change = true
        toBeCreated.foreach(cluster => {
          log.info(s"Reconciliation creating cluster $cluster")
          onAddInternal(desired.get(cluster).get, cluster.namespace, DeploymentOptions())
        })
    }

    // Process deletion
    toBeDeleted.isEmpty match {
      case true =>
      case _ => // We need to delete extraneous
        log.info(s"Reconciliation toBeDeleted: $toBeDeleted")
        change = true
        toBeDeleted.foreach(cluster => {
          val c = new FlinkCluster
          c.setName(cluster.name)
          log.info(s"Reconciliation deleting cluster $cluster")
          onDeleteInternal(c, cluster.namespace)
        })
    }

    // repair/ rescale
    desired.foreach(cluster => {
      val state = actual.get(cluster._1).getOrElse(Deployed(-1, false, false))
      var deployment = DeploymentOptions(false, false, false)
      if (!state.master) deployment = DeploymentOptions(true, deployment.worker, deployment.service)
      state.worker match {
        case actualWorkers if (actualWorkers > 0) => // may be rescale
          val desiredWorkers = getFlinkParameters(cluster._2).worker_instances
          desiredWorkers == actualWorkers match {
            case true => // Do nothing
            case _ => // Rescale
              change = true
              rescaleCluster(cluster._2, cluster._1.namespace)

          }
        case _ => // Recreate
          deployment = DeploymentOptions(deployment.master, true, deployment.service)
      }
      if (!state.service) deployment = DeploymentOptions(deployment.master, deployment.worker, true)
      deployment.todo() match {
        case true => // Need to repair
          onAddInternal(cluster._2, cluster._2.getNamespace, deployment)
          change = true
        case _ =>
      }
    })

    // first reconciliation after (re)start -> update the clusters instance
    if (!fullReconciliationRun) {
      val clusterList = "*" == namespace match {
        case true => clusters.values.toList
        case _ => List(getClusters(namespace))
      }
      clusterList.foreach(c => c.resetMetrics())
    }

    // Log result
    if (!change)
      log.info("No change was detected during the reconciliation")
    MetricsHelper.reconciliationsTotal.labels(namespace).inc()
  }

  // Get actually deployed clusters
  private def getDeployed: Map[FullName, Deployed] = {
    // Controllers for ns
    val controllers = ("*" == namespace) match {
      case true => client.replicationControllers.inAnyNamespace
      case _ => client.replicationControllers.inNamespace(namespace)
    }
    // services in ns
    val services = ("*" == namespace) match {
      case true => client.services.inAnyNamespace
      case _ => client.services.inNamespace(namespace)
    }
    // Create specific labels
    val mlabels = Map("server" -> "flink", "component" -> OPERATOR_TYPE_MASTER_LABEL, prefix + OPERATOR_KIND_LABEL -> entityName)
    val wlabels = Map("server" -> "flink", "component" -> OPERATOR_TYPE_WORKER_LABEL, prefix + OPERATOR_KIND_LABEL -> entityName)
    val slabels = Map("server" -> "flink", prefix + OPERATOR_KIND_LABEL -> entityName)
    // Get masters per name
    val masters = controllers.withLabels(mlabels.asJava).list.getItems.asScala.map(rc =>
      FullName(rc.getMetadata.getLabels.get(prefix + entityName), rc.getMetadata.getNamespace) -> true).toMap
    // Get workers per name
    val workers = controllers.withLabels(wlabels.asJava).list.getItems.asScala.map(rc =>
      FullName(rc.getMetadata.getLabels.get(prefix + entityName), rc.getMetadata.getNamespace) -> rc.getSpec.getReplicas.intValue()).toMap
    // Get services per name
    val mservices = services.withLabels(slabels.asJava).list.getItems.asScala.map(s =>
      FullName(s.getMetadata.getLabels.get(prefix + entityName), s.getMetadata.getNamespace) -> true).toMap
    // Combine to cluster information
    masters.keys.toSeq.union(workers.keys.toSeq).union(mservices.keys.toSeq)
      .map(key => (key -> Deployed(
          workers.get(key) match {case Some(w) => w; case _ => -1},
          masters.get(key) match {case Some(m) => m; case _ => false},
          mservices.get(key) match {case Some(s) => s; case _ => false}
      ))).toMap
  }

  // Rescale cluster
  private def rescaleCluster(newCluster: FlinkCluster, ns : String) : Unit = {
    val newWorkers = getFlinkParameters(newCluster).worker_instances
    log.info(s"Cluster ${newCluster.getName} scaling to $newWorkers taskmanagers")
    client.replicationControllers.inNamespace(ns).withName(s"${newCluster.getName}-taskmanager").scale(newWorkers)
    getClusters(ns).update(newCluster)
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

  private def getClusters(ns : String): RunningClusters = clusters.get(ns) match {
    case Some(c) => c  // already exists
    case _ =>
      val c = new RunningClusters(ns)
      clusters += (ns -> c)
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

case class DeploymentOptions(master: Boolean = true, worker : Boolean = true, service: Boolean = true){
  def todo() : Boolean = master || worker || service
}

case class Deployed(worker : Int, master: Boolean, service: Boolean)