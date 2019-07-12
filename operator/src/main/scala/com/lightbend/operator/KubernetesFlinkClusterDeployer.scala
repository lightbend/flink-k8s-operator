package com.lightbend.operator

import scala.collection.mutable.ListBuffer
import io.fabric8.kubernetes.api.model._
import io.fabric8.kubernetes.client.KubernetesClient
import com.lightbend.operator.types.FlinkCluster
import io.radanalytics.operator.resource.LabelsHelper._

import scala.collection.JavaConverters._
import Constants._
import org.slf4j.LoggerFactory

class KubernetesFlinkClusterDeployer(client: KubernetesClient, entityName: String, prefix: String) {

  private val log = LoggerFactory.getLogger(classOf[KubernetesFlinkClusterDeployer].getName)
//  log.info(s"Creating KubernetesFlinkClusterDeployer for the entity name $entityName, prefix $prefix")

  def getResourceList(cluster: FlinkCluster, namespace: String, options: DeploymentOptions): KubernetesResourceList[_ <: HasMetadata] = client.synchronized {

    log.info(s"Creating resource list for cluster ${cluster.getName} in namespace $namespace")
    val params = getFlinkParameters(cluster)
    var resourceList = List[HasMetadata]()
    if(options.master) resourceList = getRCforMaster(cluster, params, namespace) :: resourceList
    if(options.worker) resourceList = getRCforWorker(cluster, params, namespace) :: resourceList
    if(options.service) resourceList = getService(cluster, namespace) :: resourceList
    new KubernetesListBuilder().withItems(resourceList.asJava).build
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
    val envVars = buildEnv(cluster, params, true)

    // Arguments
    val args = params.master_args.toList

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
    val containerBuilder = cBuilder(params, envVars, ports , args, limits).withLivenessProbe(masterLiveness)

    // Mounts
    val volumes = volumesBuilder(containerBuilder, params)

    // Metrics
    var annotations = Map[String, String]()
    if (params.metrics) {
      annotations = annotations + (("prometheus.io/scrape" -> "true"), ("prometheus.io/port" -> "9249"))
    }

    // Labels
    var labels = getLabels(cluster, OPERATOR_TYPE_MASTER_LABEL)

    // Replication controller
    controllerBuilder(podName, namespace, 1, annotations, labels, containerBuilder, volumes)
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
    val envVars = buildEnv(cluster, params, false)

    // Arguments
    val args = List(OPERATOR_TYPE_WORKER_LABEL)

    // Limits
    val limits = Map(("cpu" -> new QuantityBuilder().withAmount(s"${params.worker_cpu}000m").build()),
      ("memory" -> new QuantityBuilder().withAmount(s"${params.worker_memory}Mi").build()))


    // Container
    val containerBuilder = cBuilder(params, envVars, ports , args, limits)

    // Mounts
    val volumes = volumesBuilder(containerBuilder, params)

    // Metrics
    var annotations = Map[String, String]()
    if (params.metrics) {
      annotations = annotations + (("prometheus.io/scrape" -> "true"), ("prometheus.io/port" -> "9249"))
    }

    // Labels
    val labels = getLabels(cluster, OPERATOR_TYPE_WORKER_LABEL)

    // Replication controller
    controllerBuilder(podName, namespace, params.worker_instances, annotations, labels, containerBuilder, volumes)
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

  private def buildEnv(cluster: FlinkCluster, params: FlinkParams, jobmanager : Boolean) : Seq[EnvVar] = {
    var envVars = new ListBuffer[EnvVar]()
    envVars += envBuild("CONTAINER_METRIC_PORT", params.metric_query_port)
    jobmanager match {
      case true =>
        envVars += envBuild("JOBMANAGER_MEMORY", s"${params.master_memory}m")
        envVars += envBuild("JOB_MANAGER_RPC_ADDRESS", s"${cluster.getName}-$OPERATOR_TYPE_MASTER_LABEL")
      case _ =>
        envVars += envBuild("TASKMANAGER_MEMORY", s"${params.worker_memory}m")
        envVars += envBuild("TASKMANAGER_SLOTS", params.worker_slots.toString)
        envVars += envBuild("JOB_MANAGER_RPC_ADDRESS", s"${cluster.getName}-$OPERATOR_TYPE_MASTER_LABEL")
        envVars += new EnvVarBuilder().withName("K8S_POD_IP").withValueFrom(
          new EnvVarSourceBuilder().withFieldRef(
            new ObjectFieldSelectorBuilder().withFieldPath("status.podIP").build()).build()).build

    }
    params.mounts.foreach (mount => envVars += envBuild(mount.getEnvname, mount.getMountdirectory))
    params.parallelism match {
      case p if(p != 1)  => envVars += envBuild(Constants.PARALLELISM_ENV_VAR, params.parallelism.toString)
      case _ =>
    }
    if (cluster.getEnv != null)
      cluster.getEnv.asScala.foreach(env => envVars += envBuild(env.getName, env.getValue))

    envVars
  }

  private def cBuilder(params: FlinkParams, envVars : Seq[EnvVar], ports : List[ContainerPort], args: List[String], limits: Map[String, Quantity]) : ContainerBuilder = {
    new ContainerBuilder()
      .withImage(params.imageRef)
      .withImagePullPolicy(params.pullPolicy)
      .withName(OPERATOR_TYPE_MASTER_LABEL)
      .withTerminationMessagePolicy("File")
      .withEnv(envVars.asJava)
      .withPorts(ports.asJava)
      .withArgs(args.asJava)
      .withResources(new ResourceRequirementsBuilder().withLimits(limits.asJava).build())
  }

  private def volumesBuilder(containerBuilder: ContainerBuilder, params: FlinkParams) : Seq[Volume] = {
    // Mounts
    val volumes = new ListBuffer[Volume]
    params.mounts foreach {mount =>
      val readonly = mount.getResourcetype match {
        case v if v.equalsIgnoreCase ("PVC") =>
          volumes += new VolumeBuilder ().withName (mount.getEnvname.toLowerCase).withPersistentVolumeClaim (
          new PersistentVolumeClaimVolumeSource (mount.getResourcename, false) ).build ()
          false
        case v if v.equalsIgnoreCase ("CONFIGMAP") =>
          volumes += new VolumeBuilder ().withName (mount.getEnvname.toLowerCase).withConfigMap (
          new ConfigMapVolumeSourceBuilder ().withName (mount.getResourcename).build () ).build ()
          true
        case _ =>
          volumes += new VolumeBuilder ().withName (mount.getEnvname.toLowerCase).withSecret (
          new SecretVolumeSourceBuilder ().withSecretName (mount.getResourcename).build () ).build ()
          true
      }
      containerBuilder.addToVolumeMounts (new VolumeMountBuilder ()
        .withName (mount.getEnvname.toLowerCase).withMountPath (mount.getMountdirectory).withReadOnly (readonly)
        .build () )
    }
    volumes
  }

  private def controllerBuilder(podname: String, namespace : String, replicas : Int, annotations : Map[String, String], labels : Map[String, String], containerBuilder: ContainerBuilder, volumes : Seq[Volume]) : ReplicationController = {

    new ReplicationControllerBuilder()
      .withNewMetadata
        .withName(podname)
        .withNamespace(namespace)
      .endMetadata
      .withNewSpec
        .withReplicas(replicas)
        .withNewTemplate
          .withNewMetadata
            .withAnnotations(annotations.asJava)
            .withLabels(labels.asJava)
          .endMetadata
          .withNewSpec.withContainers(containerBuilder.build).withVolumes(volumes.toList.asJava).endSpec()
        .endTemplate
      .endSpec.build
  }

  def getDefaultLabels(name: String): Map[String, String] = {
    Map((s"$prefix$OPERATOR_KIND_LABEL" -> entityName),(s"$prefix$entityName"-> name))
  }
}