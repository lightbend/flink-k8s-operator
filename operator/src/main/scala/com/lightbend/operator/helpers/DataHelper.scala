package com.lightbend.operator.helpers

import com.lightbend.operator.types.{FlinkCluster, NameValue, Persistence, RCSpec}
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition

import scala.collection.JavaConverters._
import java.util.{ArrayList, LinkedHashMap}

object DataHelper {

  def fromCRD(crd : CustomResourceDefinition) : FlinkCluster = {
    val cluster = new FlinkCluster()
    cluster.setName(crd.getMetadata.getName)
    cluster.setNamespace(crd.getMetadata.getNamespace)
    if(crd.getSpec != null) {
      val additionalproperties = crd.getSpec.getAdditionalProperties.asScala
      additionalproperties.get("customImage") match {
        case Some(value) =>
          cluster.setCustomImage(value.asInstanceOf[String])
        case _ =>
      }
      additionalproperties.get("metrics") match {
        case Some(value) =>
          cluster.setMetrics(value.asInstanceOf[Boolean])
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
          val props = value.asInstanceOf[LinkedHashMap[String, String]]
          var flinkProps: Map[String, String] = Map()
          props.get("num_taskmanagers") match {
            case value if (value != null) => flinkProps += ("num_taskmanagers" -> value)
            case _ =>
          }
          props.get("taskmanagers_slots") match {
            case value if (value != null) => flinkProps += ("taskmanagers_slots" -> value)
            case _ =>
          }
          props.get("pullpolicy") match {
            case value if (value != null) => flinkProps += ("pullpolicy" -> value)
            case _ =>
          }
          props.get("parallelism") match {
            case value if (value != null) => flinkProps += ("parallelism" -> value)
            case _ =>
          }
          props.get("logging") match {
            case value if (value != null) => flinkProps += ("logging" -> value)
            case _ =>
          }
          cluster.setFlinkConfiguration(flinkProps.asJava)
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

      additionalproperties.get("checkpointing") match {
        case Some(value) =>
          val pinfo = value.asInstanceOf[LinkedHashMap[String, AnyRef]]
          val persistence = new Persistence()
          pinfo.get("PVC") match {
            case pvc if (pvc != null) =>
              persistence.setPvc(pvc.asInstanceOf[String])
            case _ =>
          }
          pinfo.get("mountdirectory") match {
            case mountdirectory if (mountdirectory != null) =>
              persistence.setMountdirectory(mountdirectory.asInstanceOf[String])
            case _ =>
          }
          cluster.setCheckpointing(persistence)
        case _ =>
      }

      additionalproperties.get("savepointing") match {
        case Some(value) =>
          val pinfo = value.asInstanceOf[LinkedHashMap[String, AnyRef]]
          val persistence = new Persistence()
          pinfo.get("PVC") match {
            case pvc if (pvc != null) =>
              persistence.setPvc(pvc.asInstanceOf[String])
            case _ =>
          }
          pinfo.get("mountdirectory") match {
            case mountdirectory if (mountdirectory != null) =>
              persistence.setMountdirectory(mountdirectory.asInstanceOf[String])
            case _ =>
          }
          cluster.setSavepointing(persistence)
        case _ =>
      }

    }

    // Return result
    cluster
  }
}
