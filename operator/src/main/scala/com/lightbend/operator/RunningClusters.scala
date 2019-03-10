package com.lightbend.operator

import com.lightbend.operator.types.FlinkCluster
import scala.collection.mutable.Map
import MetricsHelper._
import Constants._

class RunningClusters (namespace: String){

  val clusters : Map[String, FlinkCluster]= Map()
  runningClusters.labels(namespace).set(0)

  def put(ci: FlinkCluster): Unit = {
    runningClusters.labels(namespace).inc()
    startedTotal.labels(namespace).inc()
    workers.labels(ci.getName, namespace).set(getFlinkParameters(ci).worker_instances)
    clusters += (ci.getName -> ci)
  }

  def update(ci: FlinkCluster): Unit = {
    clusters += (ci.getName -> ci)
    workers.labels(ci.getName, namespace).set(getFlinkParameters(ci).worker_instances)
  }

  def delete(name: String): Unit = {
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
