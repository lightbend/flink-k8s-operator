package com.lightbend.operator

import com.lightbend.operator.types.FlinkCluster
import scala.collection.JavaConverters._

object Constants {

  val DEFAULT_FLINK_IMAGE = "lightbend/flink:1.7.2-scala_2.11:2.0.0"
  val DEFAULT_TASKMANGER_MEMORY = "2048"
  val DEFAULT_JOBMANAGER_MEMORY = "1024"
  val DEFAULT_TASKMANGER_CPU = "4"
  val DEFAULT_JOBMANAGER_CPU = "2"
  val DEFAULT_TASKMANAGER_INSTANCES = "2"
  val DEFAULT_TASKMANAGER_SLOTS = "2"
  val OPERATOR_TYPE_MASTER_LABEL = "jobmanager"
  val OPERATOR_TYPE_WORKER_LABEL = "taskmanager"

  def getDefaultFlinkImage: String = {
    System.getenv("DEFAULT_FLINK_CLUSTER_IMAGE") match{
      case image if image != null => image
      case _ => DEFAULT_FLINK_IMAGE
    }
  }

  def getFlinkParameters(cluster: FlinkCluster): FlinkParams = {

    val metric_query_port = cluster.getFlinkConfiguration match {
      case conf if (conf != null) => conf.asScala.getOrElse("metric_query_port", "6170")
      case _ => "6170"
    }

    val master_memory = if(cluster.getMaster != null){
      cluster.getMaster.getMemory match {
      case value if value != null => value
      case _ => DEFAULT_JOBMANAGER_MEMORY
    }}
    else DEFAULT_JOBMANAGER_MEMORY

    val worker_memory = if(cluster.getWorker != null){
      cluster.getWorker.getMemory match {
      case value if value != null => value
      case _ => DEFAULT_TASKMANGER_MEMORY
    }}
    else DEFAULT_TASKMANGER_MEMORY

    val master_cpu = if(cluster.getMaster != null){
      cluster.getMaster.getCpu match {
      case value if value != null => value
      case _ => DEFAULT_JOBMANAGER_CPU
    }}
    else DEFAULT_JOBMANAGER_CPU

    val worker_cpu = if(cluster.getWorker != null){
      cluster.getWorker.getCpu match {
      case value if value != null => value
      case _ => DEFAULT_TASKMANGER_CPU
    }}
    else DEFAULT_TASKMANGER_CPU

    val worker_instances = cluster.getFlinkConfiguration match {
      case conf if (conf != null) => conf.asScala.getOrElse("num_taskmanagers", DEFAULT_TASKMANAGER_INSTANCES)
      case _ => DEFAULT_TASKMANAGER_INSTANCES
    }

    val worker_slots = cluster.getFlinkConfiguration match {
      case conf if (conf != null) => conf.asScala.getOrElse("taskmanagers_slots", DEFAULT_TASKMANAGER_SLOTS)
      case _ => DEFAULT_TASKMANAGER_SLOTS
    }

    // Image
    val imageRef = cluster.getCustomImage match {
      case value if value != null => value
      case _ => getDefaultFlinkImage // from Constants
    }

    FlinkParams(metric_query_port, master_memory, worker_memory, master_cpu, worker_cpu, imageRef, worker_instances.toInt, worker_slots)
  }
}

case class FlinkParams(metric_query_port : String, master_memory : String, worker_memory : String,
                       master_cpu : String, worker_cpu : String, imageRef : String, worker_instances : Int, worker_slots : String)
