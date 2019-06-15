package com.lightbend.operator

import com.lightbend.operator.types.{FlinkCluster, Persistence}

import scala.collection.JavaConverters._

object Constants {

  val DEFAULT_FLINK_IMAGE = "lightbend/flink:1.8.0_scala_2.11_debian"
  val DEFAULT_TASKMANGER_MEMORY = "2048"
  val DEFAULT_JOBMANAGER_MEMORY = "1024"
  val DEFAULT_TASKMANGER_CPU = "4"
  val DEFAULT_JOBMANAGER_CPU = "2"
  val DEFAULT_TASKMANAGER_INSTANCES = "2"
  val DEFAULT_TASKMANAGER_SLOTS = "2"
  val DEFAULT_PARALLELISM = "1"
  val DEFAULT_PULL_POLICY = ""
  val OPERATOR_TYPE_MASTER_LABEL = "jobmanager"
  val OPERATOR_TYPE_WORKER_LABEL = "taskmanager"

  def getDefaultFlinkImage: String = {
    System.getenv("DEFAULT_FLINK_CLUSTER_IMAGE") match{
      case image if image != null => image
      case _ => DEFAULT_FLINK_IMAGE
    }
  }

  def getFlinkParameters(cluster: FlinkCluster): FlinkParams = {

    // Image
    val imageRef = cluster.getCustomImage match {
      case value if value != null => value
      case _ => getDefaultFlinkImage // from Constants
    }

    // Master params
    val masterParams = cluster.getMaster match {
      case master if(master != null) =>
        val memory = master.getMemory match {
          case value if value != null => value
          case _ => DEFAULT_JOBMANAGER_MEMORY
        }
        val cpu = master.getCpu match {
          case value if value != null => value
          case _ => DEFAULT_JOBMANAGER_CPU
        }
        val inputs = master.getInputs match {
          case value if (value != null) && (value.size() > 0) => value.asScala
          case _ => Seq(OPERATOR_TYPE_MASTER_LABEL)
        }
        (memory, cpu, inputs)
      case _ => (DEFAULT_JOBMANAGER_MEMORY, DEFAULT_JOBMANAGER_CPU, Seq(OPERATOR_TYPE_MASTER_LABEL))
    }

    // worker params
    val workerParams = cluster.getWorker match {
      case worker if(worker != null) =>
        val memory = worker.getMemory match {
          case value if value != null => value
          case _ => DEFAULT_TASKMANGER_MEMORY
        }
        val cpu = worker.getCpu match {
          case value if value != null => value
          case _ => DEFAULT_TASKMANGER_CPU
        }
        (memory, cpu)
      case _ => (DEFAULT_TASKMANGER_MEMORY, DEFAULT_TASKMANGER_CPU)
    }

    // Flink params
    val flinkP = cluster.getFlinkConfiguration match {
      case conf if (conf != null) =>
        val confs = conf.asScala
        ( confs.getOrElse("metric_query_port", "6170"),
          confs.getOrElse("num_taskmanagers", DEFAULT_TASKMANAGER_INSTANCES),
          confs.getOrElse("taskmanagers_slots", DEFAULT_TASKMANAGER_SLOTS),
          confs.getOrElse("pullpolicy", DEFAULT_PULL_POLICY),
          confs.getOrElse("parallelism", DEFAULT_PARALLELISM),
          confs.getOrElse("logging", null.asInstanceOf[String])
        )
      case _ => ("6170", DEFAULT_TASKMANAGER_INSTANCES, DEFAULT_TASKMANAGER_SLOTS, DEFAULT_PULL_POLICY, DEFAULT_PARALLELISM, null.asInstanceOf[String])
    }
    val check : Option[Persistence] = cluster.getCheckpointing match {
      case value if (value == null) => None
      case _ => Some(cluster.getCheckpointing)
    }
    val save : Option[Persistence] = cluster.getSavepointing match {
      case value if (value == null) => None
      case _ => Some(cluster.getSavepointing)
    }

    FlinkParams(flinkP._1, masterParams._1, workerParams._1, masterParams._2, workerParams._2, masterParams._3,
      flinkP._2.toInt, flinkP._3, imageRef, flinkP._4, flinkP._5.toInt, flinkP._6, check, save)
  }
}

case class FlinkParams(metric_query_port : String, master_memory : String, worker_memory : String,
                       master_cpu : String, worker_cpu : String, master_args : Seq[String],
                       worker_instances : Int, worker_slots : String, imageRef : String, pullPolicy : String,
                       parallelism : Int, logging : String,
                       checkpointing : Option[Persistence], savepointing : Option[Persistence])
