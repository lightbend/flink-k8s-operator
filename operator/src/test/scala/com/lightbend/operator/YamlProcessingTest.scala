package com.lightbend.operator

import io.fabric8.kubernetes.client.DefaultKubernetesClient
import junit.framework.TestCase
import org.junit.Test
import com.lightbend.operator.helpers.DataHelper
import scala.collection.JavaConverters._

class YamlProcessingTest extends TestCase{

  private val file1 = "./../yaml/cluster.yaml"
  private val client = new DefaultKubernetesClient

  @Test
  def testParseYaml1(): Unit = {

    val crd = client.customResourceDefinitions().load(file1).get()
    val cluster = DataHelper.fromCRD(crd)
    println(cluster)
    val deployer = new KubernetesFlinkClusterDeployer(client, "FlinkCluster", "lightbend.com", "namespace")
    val resources = deployer.getResourceList(cluster)
    resources.getItems.asScala.foreach(resource =>
      println(resource))
  }

}
