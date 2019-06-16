package com.lightbend.operator

import com.lightbend.operator.types.{FlinkCluster, Mount}

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

object Constants {

  val DEFAULT_FLINK_IMAGE = "lightbend/flink:1.8.0_scala_2.11_debian"
  val DEFAULT_TASKMANAGER_INSTANCES = 2
  val DEFAULT_TASKMANAGER_SLOTS = 2
  val DEFAULT_TASKMANAGER_MEMORY = "2048"
  val DEFAULT_JOBMANAGER_MEMORY = "1024"
  val DEFAULT_TASKMANAGER_CPU = "4"
  val DEFAULT_JOBMANAGER_CPU = "2"
  val DEFAULT_PARALLELISM = 1
  val DEFAULT_PULL_POLICY = "IfNotPresent"
  val DEFAULT_METRIC_QUERY_PORT = "6170"
  val DEFAULT_METRICS = true
  val OPERATOR_TYPE_MASTER_LABEL = "jobmanager"
  val OPERATOR_TYPE_WORKER_LABEL = "taskmanager"
  val CHECKPOINTING_DIRECTORY = "/flink/checkpointing"
  val CHECKPOINTING_ENVIRONMENT = "CHECKPOINTDIR"
  val SAVEPOINTING_DIRECTORY = "/flink/savepointing"
  val SAVEPOINTING_ENVIRONMENT = "SAVEPOINTDIR"
  val LOGGING_CONFIG_DIRECTORY = "/flink/config/logging"
  val LOGGING_ENVIRONMENT = "LOGCONFIGDIR"
  val PARALLELISM_ENV_VAR = "PARALLELISM"
  val FLINK_CONFIG_DIR = "/opt/flink/conf"

  def getFlinkParameters(cluster: FlinkCluster): FlinkParams = {

    // Image
    val imageRef = cluster.getCustomImage match {
      case image if image != null =>
        val imagename = image.getImagename match{
          case value if value != null => value
          case _ => DEFAULT_FLINK_IMAGE
        }
        val pullpolicy = image.getPullpolicy match{
          case value if value != null => value
          case _ => DEFAULT_PULL_POLICY
        }
        (imagename, pullpolicy)
      case _ => (DEFAULT_FLINK_IMAGE, DEFAULT_PULL_POLICY)
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
          case _ => DEFAULT_TASKMANAGER_MEMORY
        }
        val cpu = worker.getCpu match {
          case value if value != null => value
          case _ => DEFAULT_TASKMANAGER_CPU
        }
        (memory, cpu)
      case _ => (DEFAULT_TASKMANAGER_MEMORY, DEFAULT_TASKMANAGER_CPU)
    }

    val mounts = new ListBuffer[Mount]

    // Flink params
    val flinkP = cluster.getFlinkConfiguration match {
      case conf if (conf != null) =>
        val metric_query_port = DEFAULT_METRIC_QUERY_PORT
        val taskmanagers = conf.getNumTaskmanagers match{
          case value if value != null => value.intValue()
          case _ => DEFAULT_TASKMANAGER_INSTANCES
        }
        val taskmanagerslots = conf.getTaskmanagerSlot match{
          case value if value != null => value.intValue()
          case _ => DEFAULT_TASKMANAGER_SLOTS
        }
        val parallelism = conf.getParallelism match{
          case value if value != null => value.intValue()
          case _ => DEFAULT_PARALLELISM
        }
        val metrics = conf.getMetrics match{
          case value if value != null => value.booleanValue()
          case _ => DEFAULT_METRICS
        }
        conf.getLogging match{
          case value if value != null =>
            mounts += new Mount().withResourcetype("CONFIGMAP").withResourcename(value)
              .withMountdirectory(LOGGING_CONFIG_DIRECTORY).withEnvname(LOGGING_ENVIRONMENT)
          case _ =>
        }
        conf.getCheckpointing match{
          case value if value != null =>
            mounts += new Mount().withResourcetype("PVC")
              .withResourcename(value).withMountdirectory(CHECKPOINTING_DIRECTORY).withEnvname(CHECKPOINTING_ENVIRONMENT)
          case _ =>
        }
        conf.getSavepointing match{
          case value if value != null =>
            mounts += new Mount().withResourcetype("PVC")
              .withResourcename(value).withMountdirectory(SAVEPOINTING_DIRECTORY).withEnvname(SAVEPOINTING_ENVIRONMENT)
          case _ =>
        }
        (metric_query_port, taskmanagers, taskmanagerslots, parallelism, metrics)

      case _ => (DEFAULT_METRIC_QUERY_PORT, DEFAULT_TASKMANAGER_INSTANCES, DEFAULT_TASKMANAGER_SLOTS, DEFAULT_PARALLELISM, DEFAULT_METRICS)
    }

    // Additional mounte
    cluster.getMounts match {
      case value if (value != null) && (value.size() > 0) =>
        value.asScala.foreach { mount =>
          if(mount.getMountdirectory != FLINK_CONFIG_DIR && ((mount.getResourcetype.equalsIgnoreCase("PVC")) ||
            (mount.getResourcetype.equalsIgnoreCase("SECRET")) ||
            (mount.getResourcetype.equalsIgnoreCase("CONFIGMAP")))){
            mounts += new Mount().withResourcetype(mount.getResourcetype).withResourcename(mount.getResourcename)
              .withMountdirectory(mount.getMountdirectory).withEnvname(mount.getEnvname)

          }
        }
      case _ =>
    }


    FlinkParams(flinkP._5, flinkP._1, masterParams._1, workerParams._1, masterParams._2, workerParams._2, masterParams._3,
      flinkP._2, flinkP._3, imageRef._1, imageRef._2, flinkP._4, mounts)
  }
}

case class FlinkParams(metrics : Boolean, metric_query_port : String, master_memory : String, worker_memory : String,
                       master_cpu : String, worker_cpu : String, master_args : Seq[String],
                       worker_instances : Int, worker_slots : Int, imageRef : String, pullPolicy : String,
                       parallelism : Int, mounts: Seq[Mount])
