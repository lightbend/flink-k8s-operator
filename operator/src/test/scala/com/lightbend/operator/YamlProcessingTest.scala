package com.lightbend.operator

import com.lightbend.operator.helpers.DataHelper
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import junit.framework.TestCase
import org.junit.Test
import com.lightbend.operator.types.FlinkCluster
import io.radanalytics.operator.common.JSONSchemaReader

import scala.collection.JavaConverters._

class YamlProcessingTest extends TestCase{

  private val file1 = "./../yaml/cluster_complete.yaml"
  private val client = new DefaultKubernetesClient

  @Test
  def testParseYaml(): Unit = {

    val schema = JSONSchemaReader.readSchema(classOf[FlinkCluster])
    println(schema)

    val crd = client.customResourceDefinitions().load(file1).get()
    val cluster = DataHelper.fromCRD(crd)
    println(cluster)

    val deployer = new KubernetesFlinkClusterDeployer(client, "FlinkCluster", "lightbend.com")
    val resources = deployer.getResourceList(cluster, cluster.getNamespace, DeploymentOptions())
    resources.getItems.asScala.foreach(resource => println(resource))
  }
}
