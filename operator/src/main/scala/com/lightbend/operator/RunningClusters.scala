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

  def put(c: FlinkCluster): Unit = {
    log.info(s"Adding new cluster ${c.getName} in namespace $namespace")
    clusters.get(c.getName) match {
      case Some(value) => // Already exists, skip
      case _ =>
        runningClusters.labels(namespace).inc()
        startedTotal.labels(namespace).inc()
        workers.labels(c.getName, namespace).set(getFlinkParameters(c).worker_instances)
        clusters += (c.getName -> c)
    }
  }

  def update(c: FlinkCluster): Unit = {
    log.info(s"Updating cluster ${c.getName} in namespace $namespace")
    clusters += (c.getName -> c)
    workers.labels(c.getName, namespace).set(getFlinkParameters(c).worker_instances)
  }

  def delete(name: String): Unit = {
    log.info(s"Deleting cluster $name in namespace $namespace")
    clusters.contains(name) match {
      case true =>
        runningClusters.labels(namespace).dec()
        workers.labels(name, namespace).set(0)
        clusters -=name
      case _ =>
    }
  }

  def getCluster(name: String): FlinkCluster = clusters.getOrElse(name, null)

  def resetMetrics(): Unit = {
    startedTotal.labels(namespace).set(0)
    clusters.keys.foreach(name => workers.labels(name, namespace).set(0))
    startedTotal.labels(namespace).set(0)
  }
}
