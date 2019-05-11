package com.lightbend.operator

import com.lightbend.operator.types.{FlinkCluster, Persistence}

import scala.collection.JavaConverters._

object Constants {

  val DEFAULT_FLINK_IMAGE = "lightbend/flink:1.7.2-scala_2.11"
  val DEFAULT_TASKMANGER_MEMORY = "2048"
  val DEFAULT_JOBMANAGER_MEMORY = "1024"
  val DEFAULT_TASKMANGER_CPU = "4"
  val DEFAULT_JOBMANAGER_CPU = "2"
  val DEFAULT_TASKMANAGER_INSTANCES = "2"
  val DEFAULT_TASKMANAGER_SLOTS = "2"
  val OPERATOR_TYPE_MASTER_LABEL = "jobmanager"
  val OPERATOR_TYPE_WORKER_LABEL = "taskmanager"

  def getSafeValueWithDefault[T](t:T, default: => T) : T = if(t == null) default else t

  def getDefaultFlinkImage: String = {
    getSafeValueWithDefault(System.getenv("DEFAULT_FLINK_CLUSTER_IMAGE"), DEFAULT_FLINK_IMAGE)
  }

  def getFlinkParameters(cluster: FlinkCluster): FlinkParams = {

    // Image
    val imageRef = getSafeValueWithDefault(cluster.getCustomImage, getDefaultFlinkImage)

    // Master params
    val masterParams = Option(cluster.getMaster).map{master =>
        val memory = getSafeValueWithDefault(master.getMemory, DEFAULT_JOBMANAGER_MEMORY)
        val cpu = getSafeValueWithDefault(master.getCpu, DEFAULT_JOBMANAGER_CPU)
        (memory, cpu)}.getOrElse(DEFAULT_JOBMANAGER_MEMORY -> DEFAULT_JOBMANAGER_CPU)

    // worker params
    val workerParams = Option(cluster.getWorker).map { worker =>
      val memory = getSafeValueWithDefault(worker.getMemory, DEFAULT_TASKMANGER_MEMORY)
      val cpu = getSafeValueWithDefault(worker.getCpu, DEFAULT_TASKMANGER_CPU)
      memory -> cpu
    }.getOrElse(DEFAULT_TASKMANGER_MEMORY ->  DEFAULT_TASKMANGER_CPU)


    // Flink params
    val flinkP = Option(cluster.getFlinkConfiguration).map { conf =>
      (conf.asScala.getOrElse("metric_query_port", "6170"),
        conf.asScala.getOrElse("num_taskmanagers", DEFAULT_TASKMANAGER_INSTANCES),
        conf.asScala.getOrElse("taskmanagers_slots", DEFAULT_TASKMANAGER_SLOTS))
    }.getOrElse{("6170", DEFAULT_TASKMANAGER_INSTANCES, DEFAULT_TASKMANAGER_SLOTS)}


    val check : Option[Persistence] = Option(cluster.getCheckpointing)

    val save : Option[Persistence] = Option(cluster.getSavepointing)

    FlinkParams(flinkP._1, masterParams._1, workerParams._1, masterParams._2, workerParams._2,
      imageRef, flinkP._2.toInt, flinkP._3, check, save)
  }
}

case class FlinkParams(metric_query_port : String, master_memory : String, worker_memory : String,
                       master_cpu : String, worker_cpu : String, imageRef : String, worker_instances : Int,
                       worker_slots : String, checkpointing : Option[Persistence], savepointing : Option[Persistence])
