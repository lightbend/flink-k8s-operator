package com.lightbend.operator

import com.lightbend.operator.types.FlinkCluster

import scala.collection.mutable.Map
import MetricsHelper._
import Constants._
import org.slf4j.LoggerFactory

class RunningClusters (namespace: String){

  private val log = LoggerFactory.getLogger(classOf[RunningClusters].getName)

  log.info(s"Creating clusters map for the namespace $namespace")
  val clusters : Map[String, FlinkCluster]= Map()
  runningClusters.labels(namespace).set(0)

  def put(ci: FlinkCluster): Unit = {
    log.info(s"Adding new cluster ${ci.getName} in namespace $namespace")
    runningClusters.labels(namespace).inc()
    startedTotal.labels(namespace).inc()
    workers.labels(ci.getName, namespace).set(getFlinkParameters(ci).worker_instances)
    clusters += (ci.getName -> ci)
  }

  def update(ci: FlinkCluster): Unit = {
    log.info(s"Updating cluster ${ci.getName} in namespace $namespace")
    clusters += (ci.getName -> ci)
    workers.labels(ci.getName, namespace).set(getFlinkParameters(ci).worker_instances)
  }

  def delete(name: String): Unit = {
    log.info(s"Deleting cluster $name in namespace $namespace")
    if (clusters.contains(name)) {
      runningClusters.labels(namespace).dec()
      workers.labels(name, namespace).set(0)
      clusters -=name
    }
  }

  def getCluster(name: String): FlinkCluster = clusters.getOrElse(name, null)

  def resetMetrics(): Unit = {
    startedTotal.labels(namespace).set(0)
    clusters.keys.foreach(name => workers.labels(name, namespace).set(0))
    startedTotal.labels(namespace).set(0)
  }
}
