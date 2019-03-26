package com.lightbend.operator

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util
import java.util.Arrays

import com.fasterxml.jackson.databind.ObjectMapper
import com.lightbend.operator.helpers.DataHelper
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import junit.framework.TestCase
import org.junit.Test
import com.lightbend.operator.types.FlinkCluster
import io.fabric8.kubernetes.api.model.apiextensions.{CustomResourceDefinitionBuilder, JSONSchemaProps}
import io.radanalytics.operator.common.JSONSchemaReader
import org.junit.Assert.assertEquals

import scala.collection.JavaConverters._

class YamlProcessingTest extends TestCase{

  private val file1 = "./../yaml/cluster_complete.yaml"
  private val client = new DefaultKubernetesClient

  @Test
  def testParseYaml1(): Unit = {

    val schema = JSONSchemaReader.readSchema(classOf[FlinkCluster])
    println(schema)

    val crd = client.customResourceDefinitions().load(file1).get()
    val cluster = DataHelper.fromCRD(crd)
    println(cluster)

    val deployer = new KubernetesFlinkClusterDeployer(client, "FlinkCluster", "lightbend.com")
    val resources = deployer.getResourceList(cluster, cluster.getNamespace)
    resources.getItems.asScala.foreach(resource => println(resource))
  }
}
