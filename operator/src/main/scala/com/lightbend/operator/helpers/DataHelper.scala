package com.lightbend.operator.helpers

import java.util

import com.lightbend.operator.types.{FlinkCluster, FlinkConfiguration, Image, Mount, NameValue, RCSpec}
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition

import scala.collection.JavaConverters._
import java.util.{ArrayList, LinkedHashMap}

import scala.collection.mutable.ListBuffer

object DataHelper {

  def fromCRD(crd : CustomResourceDefinition) : FlinkCluster = {
    val cluster = new FlinkCluster()
    cluster.setName(crd.getMetadata.getName)
    cluster.setNamespace(crd.getMetadata.getNamespace)
    if(crd.getSpec != null) {
      val additionalproperties = crd.getSpec.getAdditionalProperties.asScala
      additionalproperties.get("customImage") match {
        case Some(value) =>
          val props = value.asInstanceOf[LinkedHashMap[String, String]]
          val image = new Image()
          props.get("imagename") match {
            case value if (value != null) => image.setImagename(value)
            case _ =>
          }
          props.get("pullpolicy") match {
            case value if (value != null) => image.setPullpolicy(value)
            case _ =>
          }
          cluster.setCustomImage(image)
        case _ =>
      }
      additionalproperties.get("env") match {
        case Some(value) =>
          val env = value.asInstanceOf[ArrayList[LinkedHashMap[String, AnyRef]]].asScala.map(e => {
            val en = e.asInstanceOf[LinkedHashMap[String, String]]
            new NameValue().withName(en.get("name")).withValue(en.get("value"))
          })
          cluster.setEnv(env.asJava)
        case _ =>
      }
      additionalproperties.get("labels") match {
        case Some(value) =>
          val labels = value.asInstanceOf[LinkedHashMap[String, String]].asScala.map(lab => {
            (lab._1 -> lab._2)
          }).toMap
          cluster.setLabels(labels.asJava)
        case _ =>
      }
      additionalproperties.get("flinkConfiguration") match {
        case Some(value) =>
          val props = value.asInstanceOf[LinkedHashMap[String, AnyRef]]
          val flinkProps = new FlinkConfiguration()
          props.get("num_taskmanagers") match {
            case value if (value != null) => flinkProps.setNumTaskmanagers(value.asInstanceOf[Int])
            case _ =>
          }
          props.get("taskmanagers_slots") match {
            case value if (value != null) => flinkProps.setTaskmanagerSlot(value.asInstanceOf[Int])
            case _ =>
          }
          props.get("parallelism") match {
            case value if (value != null) => flinkProps.setParallelism(value.asInstanceOf[Int])
            case _ =>
          }
          props.get("metrics") match {
            case value if (value != null) => flinkProps.setMetrics(value.asInstanceOf[Boolean])
            case _ =>
          }
          props.get("logging") match {
            case value if (value != null) => flinkProps.setLogging(value.asInstanceOf[String])
            case _ =>
          }
          props.get("checkpointing") match {
            case value if (value != null) => flinkProps.setCheckpointing(value.asInstanceOf[String])
            case _ =>
          }
          props.get("savepointing") match {
            case value if (value != null) => flinkProps.setSavepointing(value.asInstanceOf[String])
            case _ =>
          }
          cluster.setFlinkConfiguration(flinkProps)
        case _ =>
      }

      additionalproperties.get("master") match {
        case Some(value) =>
          val minfo = value.asInstanceOf[LinkedHashMap[String, AnyRef]]
          val master = new RCSpec()
          minfo.get("cpu") match {
            case cpu if (cpu != null) =>
              master.setCpu(cpu.asInstanceOf[String])
            case _ =>
          }
          minfo.get("memory") match {
            case memory if (memory != null) =>
              master.setMemory(memory.asInstanceOf[String])
            case _ =>
          }
          minfo.get("inputs") match {
            case inputs if (inputs != null) =>
              master.setInputs(inputs.asInstanceOf[ArrayList[String]])
            case _ =>
          }
          cluster.setMaster(master)

        case _ =>
      }

      additionalproperties.get("worker") match {
        case Some(value) =>
          val winfo = value.asInstanceOf[LinkedHashMap[String, AnyRef]]
          val worker = new RCSpec()
          winfo.get("cpu") match {
            case cpu if (cpu != null) =>
              worker.setCpu(cpu.asInstanceOf[String])
            case _ =>
          }
          winfo.get("memory") match {
            case memory if (memory != null) =>
              worker.setMemory(memory.asInstanceOf[String])
            case _ =>
          }
          cluster.setWorker(worker)
        case _ =>
      }

      additionalproperties.get("mounts") match {
        case Some(value) =>
          val minfos = value.asInstanceOf[util.ArrayList[LinkedHashMap[String, String]]]
          val mounts = new ListBuffer[Mount]
          minfos.forEach{minfo =>
            val resourceType = minfo.get("resourcetype") match {
              case rtype if (rtype != null) => rtype
              case _ => ""
            }
            val resourceName = minfo.get("resourcename") match {
              case rname if (rname != null) => rname
              case _ => ""
            }
            val mountDirectory = minfo.get("mountdirectory") match {
              case directory if (directory != null) => directory
              case _ => ""
            }
            val envname = minfo.get("envname") match {
              case name if (name != null) => name
              case _ => ""
            }
            mounts += new Mount().withResourcetype(resourceType).withResourcename(resourceName)
              .withMountdirectory(mountDirectory).withEnvname(envname)
          }
          cluster.setMounts(mounts.asJava)
        case _ =>
      }

    }

    // Return result
    cluster
  }
}
