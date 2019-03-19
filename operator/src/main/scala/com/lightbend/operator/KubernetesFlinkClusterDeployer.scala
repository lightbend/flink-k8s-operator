package com.lightbend.operator

import scala.collection.mutable.ListBuffer
import io.fabric8.kubernetes.api.model._
import io.fabric8.kubernetes.client.KubernetesClient
import com.lightbend.operator.types.FlinkCluster
import io.radanalytics.operator.resource.LabelsHelper._

import scala.collection.JavaConverters._
import Constants._
import org.slf4j.LoggerFactory

class KubernetesFlinkClusterDeployer(client: KubernetesClient, entityName: String, prefix: String, namespace: String) {

  private val log = LoggerFactory.getLogger(classOf[KubernetesFlinkClusterDeployer].getName)
  log.info(s"Creating KubernetesFlinkClusterDeployer for the entity name $entityName, prefix$prefix, namespace $namespace")

  def getResourceList(cluster: FlinkCluster): KubernetesResourceList[_ <: HasMetadata] = client.synchronized {

    log.info(s"Creating resource list for cluster ${cluster.getName} in namespace $namespace")
    val params = getFlinkParameters(cluster)
    val masterRc = getRCforMaster(cluster, params, namespace)
    val workerRc = getRCforWorker(cluster, params, namespace)
    val masterService = getService(cluster, namespace)
    new KubernetesListBuilder().withItems(List(masterRc, workerRc, masterService).asJava).build
  }

  private def getService(cluster: FlinkCluster, namespace: String): Service = {

    val labels = getLabels(cluster, null)
    val ports = List(
      new ServicePortBuilder().withPort(6123).withName("rpc").build,
      new ServicePortBuilder().withPort(6124).withName("blob").build,
      new ServicePortBuilder().withPort(8081).withName("ui").build)

    new ServiceBuilder()
      .withNewMetadata
        .withName(s"${cluster.getName}-$OPERATOR_TYPE_MASTER_LABEL")
        .withNamespace(namespace)
        .withLabels(labels.asJava)
      .endMetadata.
      withNewSpec
        .withSelector(Map("app" -> cluster.getName, "component" -> OPERATOR_TYPE_MASTER_LABEL).asJava)
        .withPorts(ports.asJava)
      .endSpec()
      .build
  }

  private def getRCforMaster(cluster: FlinkCluster, params: FlinkParams, namespace: String): ReplicationController = {

    // Names
    val name = cluster.getName
    val podName = s"$name-$OPERATOR_TYPE_MASTER_LABEL"

    // Ports
    val ports = List(
      portBuild(6123, "rpc"),
      portBuild(6124, "blob"),
      portBuild(8081, "ui"))

    // Environment variables
    var envVars = new ListBuffer[EnvVar]()
    envVars += envBuild("CONTAINER_METRIC_PORT", params.metric_query_port)
    envVars += envBuild("JOBMANAGER_MEMORY", s"${params.master_memory}m")
    envVars += envBuild("JOB_MANAGER_RPC_ADDRESS", s"$name-$OPERATOR_TYPE_MASTER_LABEL")
    if (cluster.getEnv != null)
      cluster.getEnv.asScala.foreach(env => envVars += envBuild(env.getName, env.getValue))

    // Arguments
    var args = List(OPERATOR_TYPE_MASTER_LABEL)
    if(cluster.getMaster != null && cluster.getMaster.getInputs != null)
      args = args ++ cluster.getMaster.getInputs.asScala

    // Liveness probe
    val masterLiveness = new ProbeBuilder()
      .withHttpGet(
        new HTTPGetActionBuilder().withPath("/overview")
          .withPort(new IntOrStringBuilder().withIntVal(8081).build()).build())
      .withInitialDelaySeconds(30)
      .withPeriodSeconds(10)
      .build()

    // Limits
    val limits = Map(("cpu" -> new QuantityBuilder().withAmount(s"${params.master_cpu}000m").build()),
      ("memory" -> new QuantityBuilder().withAmount(s"${params.master_memory}Mi").build()))

    // Container
    val containerBuilder = new ContainerBuilder()
      .withImage(params.imageRef)
      .withImagePullPolicy("IfNotPresent")
      .withName(OPERATOR_TYPE_MASTER_LABEL)
      .withTerminationMessagePolicy("File")
      .withEnv(envVars.asJava)
      .withPorts(ports.asJava)
      .withLivenessProbe(masterLiveness)
      .withArgs(args.asJava)
      .withResources(new ResourceRequirementsBuilder().withLimits(limits.asJava).build())

    // Metrics
    var annotations = Map[String, String]()
    if (cluster.getMetrics) {
      annotations = annotations + (("prometheus.io/scrape" -> "true"), ("prometheus.io/port" -> "9249"))
    }

    // Labels
    var labels = getLabels(cluster, OPERATOR_TYPE_MASTER_LABEL)

    // Replication controller
    new ReplicationControllerBuilder()
      .withNewMetadata
        .withName(podName)
        .withNamespace(namespace)
      .endMetadata
      .withNewSpec
        .withReplicas(1)
        .withNewTemplate
          .withNewMetadata
            .withAnnotations(annotations.asJava)
            .withLabels(labels.asJava)
          .endMetadata
          .withNewSpec.withContainers(containerBuilder.build).endSpec
        .endTemplate
      .endSpec.build
  }

  private def getRCforWorker(cluster: FlinkCluster, params: FlinkParams, namespace: String): ReplicationController = {

    // Flink parameters
    val name = cluster.getName
    val podName = s"$name-$OPERATOR_TYPE_WORKER_LABEL"

    // Ports
    val ports = List(
      portBuild(6121, "data"),
      portBuild(6122, "rpc"),
      portBuild(6125, "query"),
      portBuild(params.metric_query_port.toInt, "metric"))

    // Environment variables
    var envVars = new ListBuffer[EnvVar]()
    envVars += envBuild("CONTAINER_METRIC_PORT", params.metric_query_port)
    envVars += envBuild("TASKMANAGER_MEMORY", s"${params.worker_memory}m")
    envVars += envBuild("TASKMANAGER_SLOTS", params.worker_slots)
    envVars += envBuild("JOB_MANAGER_RPC_ADDRESS", s"$name-$OPERATOR_TYPE_MASTER_LABEL")
    envVars += new EnvVarBuilder().withName("K8S_POD_IP").withValueFrom(
      new EnvVarSourceBuilder().withFieldRef(
        new ObjectFieldSelectorBuilder().withFieldPath("status.podIP").build()).build()).build
    if (cluster.getEnv != null)
      cluster.getEnv.asScala.foreach(env => envVars += envBuild(env.getName, env.getValue))

    // Arguments
    val args = List(OPERATOR_TYPE_WORKER_LABEL)

    // Limits
    val limits = Map(("cpu" -> new QuantityBuilder().withAmount(s"${params.worker_cpu}000m").build()),
      ("memory" -> new QuantityBuilder().withAmount(s"${params.worker_memory}Mi").build()))


    // Container
    val containerBuilder = new ContainerBuilder()
      .withImage(params.imageRef)
      .withImagePullPolicy("IfNotPresent")
      .withName(OPERATOR_TYPE_WORKER_LABEL)
      .withTerminationMessagePolicy("File")
      .withEnv(envVars.asJava)
      .withPorts(ports.asJava)
      .withArgs(args.asJava)
      .withResources(new ResourceRequirementsBuilder().withLimits(limits.asJava).build())

    // Metrics
    var annotations = Map[String, String]()
    if (cluster.getMetrics) {
      annotations = annotations + (("prometheus.io/scrape" -> "true"), ("prometheus.io/port" -> "9249"))
    }

    // Labels
    var labels = getLabels(cluster, OPERATOR_TYPE_WORKER_LABEL)

    // Replication controller
    new ReplicationControllerBuilder()
      .withNewMetadata
        .withName(podName)
        .withNamespace(namespace)
      .endMetadata
      .withNewSpec
        .withReplicas(1)
        .withNewTemplate
          .withNewMetadata
            .withAnnotations(annotations.asJava)
            .withLabels(labels.asJava)
          .endMetadata
          .withNewSpec.withContainers(containerBuilder.build).endSpec
        .endTemplate
      .endSpec.build
  }

  private def envBuild(key: String, value: String): EnvVar = new EnvVarBuilder().withName(key).withValue(value).build

  private def portBuild(port: Int, name: String): ContainerPort = new ContainerPortBuilder().withContainerPort(port).withName(name).build()

  private def getLabels(cluster: FlinkCluster, component : String) : Map[String, String] = {

    var labels = Map(("server" -> "flink"), ("app" -> cluster.getName)) ++ getDefaultLabels(cluster.getName)
    if (cluster.getLabels != null) {
      cluster.getLabels.asScala.foreach(label =>
        labels = labels + (label._1 -> label._2)
      )
    }
    if(component != null)
      labels = labels + ("component" -> component)
    labels
  }

  def getDefaultLabels(name: String): Map[String, String] = {
    Map((s"$prefix$OPERATOR_KIND_LABEL" -> entityName),(s"$prefix$entityName", name))
  }
}