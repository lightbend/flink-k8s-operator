# Flink-operator


`CRD`-based approach for managing Flink clusters in Kubernetes and OpenShift.

This operator uses [abstract-operator](https://github.com/jvm-operators/abstract-operator) library.

## Building and Packaging

The operator is implemented in the operator module. The model contains both
[json definition of the CRD](schema/flinkCluster.json) and the actual
implementation code.

Building and creation of the docker image can be done running command:
````
 sbt docker 
````
This _docker build_ requires a base image that can be build using the following [docker file](./Dockerfile)

## Installation

To install the operator use [Helm](helm/flink-operator)

The following configurations is available for operator:
* Operator image information including repository - operator docker name (default - lightbend/fdp-flink-operator); tag - operator docker tag (default - 0.0.1) and pullPolicy - operator docker pull policy (default - always)
* Namespace to watch - three options supported are - empty list - namespace where the operator is installed; explicit list of namespaces, “*” - all namespace (default - “*”)
* ReconciliationInterval - how often (in seconds) the full reconciliation should be run (default is 180)
* Metrics - a boolean defining whether operator metrics is exposed to Prometheus (default - true)
* MetricsPort - port used by metrics http server (default - 8080)
* InternalJvmMetrics - a boolean defining whether operator's internal JVM metrics is available through Prometheus (default - true)
* Operator's resource requirements including memory requirement for an operator (default - 512Mi); cpu requirement for an operator (default - 1000m)
* Checkpointing configuration, including PVC name and mount directory (default none)
* Savepointing configuration, including PVC name and mount directory (default none)

## Cluster's specification

Cluster can be configured using the following components:
* customImage defines two parameters parameters:
    * imagename - name of the image to use for cluster (same image is used for both job manager and task manager) - default is `lightbend/flink:1.8.0_scala_2.11_debian`
    * pullpolicy - image pull policy - default is `IfNotPresent`
* flinkConfiguration defines cluster specific configuration
    * num_taskmanagers - number of task managers (integer) - default is `2`
    * taskmanagers_slots - number of slots per task managers (integer) - default is `2`
    * parallelism - default parallelism for Flink application (integer) - default is `1`
    * metrics - defines wheater to expose cluster's metrics via Prometheus - default `true`
    * logging - name of the configmap with the overwrites for logging (see [sample](/yaml/logging-configmap.yaml) of all the files and their data). If not specified, default Flink configuration is used 
    * checkpointing - name of the PVC used for checkpointing. If it is specified Flink HA is used, if not specified, external checkpointing is not supported and no HA is used
    * savepointing - name of the PVC used for savepointing. If it is specified savepointing is not supported. 
* master defines specification for jobmanager
    * cpu - amount of cpus per instance (string), default `"2"`
    * memory - amount of memory per instance (string), default `"1024"`
    * inputs - array of inputs used for job manager. If not specified - a session cluster is started. To start a job cluster inputs should contain         
````
    - jobcluster                                                                                                                                                       
    - name of the main job class
    - parameters
````
Note that parameter's name and value should be specified on different lines
* worker defines specification for taskmanager
    * cpu - amount of cpus per instance (string), default `"4"`
    * memory - amount of memory per instance (string), default `"2048"`
* labels - list of additional labels (key/values), see example [here](yaml/cluster_complete.yaml)
* env - list of additional environment variables (key/values), see example [here](yaml/cluster_complete.yaml)
* mounts - list of additional mounts (`PVC`, `ConfigMap`, `Secret`). Every mount is defined by the following parameters, all of which should be present:
    * resourcetype - type of mounted resource. Supported values are `PVC`, `ConfigMap`, `Secret` (not case sensitive). Any other resource type will be ignored
    * resourcename - name of the resource (the resource should exist)
    * mountdirectory - directory at which resource is mounted. If this directory is `/opt/flink/conf`, the resource will be ignored to avoid overriding Flink's native configuration. Additionally `PVC` resources are mounted as `read/write, while`, while `configMap` and `Secret` are mounted as `readdOnly`
    * envname - name used to set mountdirectory as environment variable

The following are generated environment variables    
* `logconfigdir` for logging definition files
* `checkpointdir` for checkpointing directory
* `savepointdir` for savepointing directory


## Basic commands

To create a cluster, execute the following command:
```
cat <<EOF | kubectl create -f -
apiVersion: lightbend.com/v1
kind: FlinkCluster
metadata:
  name: my-cluster
spec:
  flinkConfiguration:
    num_taskmanagers: 1
    taskmanagers_slots: 2
    parallelism: 2
    logging : "flink-logging"
    checkpointing: "flink-operator-checkpointing"
    savepointing: "flink-operator-savepointing"
  worker:
    cpu: "1"
  master:
    cpu: "1"    
  mounts:
    - resourcetype: "secret"
      resourcename: "strimzi-clients-ca-cert"
      mountdirectory: "/etc/tls-sidecar/cluster-ca-certs/"
      envname : "my-secret"
EOF
```
Additional parameters can be added as described above

By default a Flink [session cluster](https://ci.apache.org/projects/flink/flink-docs-stable/ops/deployment/kubernetes.html#flink-session-cluster-on-kubernetes) will be created
(a default argument *taskmanager* will be generated in this case).

If you want to run Flink [job cluster](https://ci.apache.org/projects/flink/flink-docs-stable/ops/deployment/kubernetes.html#flink-job-cluster-on-kubernetes) specify
*jobcluster* cluster as an input followed by the name of the main class for a job and the list of parameters.

When using a job cluster, you can additionally specify the following [parameters](https://github.com/apache/flink/tree/release-1.7/flink-container/docker#deploying-via-docker-compose):
* `PARALLELISM` - Default parallelism with which to start the job (default: 1), for example `--parallelism <parallelism>`
* `SAVEPOINT_OPTIONS` - Savepoint options to start the cluster with (default: none), for example `--fromSavepoint <SAVEPOINT_PATH> --allowNonRestoredState`

For more information on parallelism and savepoint options, see the [documentation](https://ci.apache.org/projects/flink/flink-docs-stable/ops/cli.html#usage)

---
**Note**

This operator assumes that custom images are build using [this project](https://github.com/lightbend/fdp-flink-build).
If you build your images differently, the commands for running applications will change

---

## Seeing what is running

To see running clusters, execute:

````
oc get FlinkCluster
NAME         AGE
my-cluster   13m
```` 

To get the information about specific cluster, run:

````
oc describe FlinkCluster my-cluster
Name:         my-cluster
Namespace:    flink
Labels:       <none>
Annotations:  <none>
API Version:  lightbend.com/v1
Kind:         FlinkCluster
Metadata:
  Creation Timestamp:  2019-06-16T15:21:27Z
  Generation:          1
  Resource Version:    11087658
  Self Link:           /apis/lightbend.com/v1/namespaces/flink/flinkclusters/my-cluster
  UID:                 68f50b35-904a-11e9-9719-065625d6fbaa
Spec:
  Flink Configuration:
    Checkpointing:         flink-operator-checkpointing
    Logging:               flink-logging
    Num _ Taskmanagers:    1
    Parallelism:           2
    Savepointing:          flink-operator-savepointing
    Taskmanagers _ Slots:  2
  Master:
    Cpu:  1
  Mounts:
    Envname:         my-secret
    Mountdirectory:  /etc/tls-sidecar/cluster-ca-certs/
    Resourcename:    strimzi-clients-ca-cert
    Resourcetype:    secret
  Worker:
    Cpu:  1
Events:   <none>
````
You can also get information about all running clusters running the following:
````
oc describe FlinkCluster
Name:         my-cluster
Namespace:    flink
Labels:       <none>
Annotations:  <none>
API Version:  lightbend.com/v1
Kind:         FlinkCluster
Metadata:
  Creation Timestamp:  2019-06-16T15:21:27Z
  Generation:          1
  Resource Version:    11087658
  Self Link:           /apis/lightbend.com/v1/namespaces/flink/flinkclusters/my-cluster
  UID:                 68f50b35-904a-11e9-9719-065625d6fbaa
Spec:
  Flink Configuration:
    Checkpointing:         flink-operator-checkpointing
    Logging:               flink-logging
    Num _ Taskmanagers:    1
    Parallelism:           2
    Savepointing:          flink-operator-savepointing
    Taskmanagers _ Slots:  2
  Master:
    Cpu:  1
  Mounts:
    Envname:         my-secret
    Mountdirectory:  /etc/tls-sidecar/cluster-ca-certs/
    Resourcename:    strimzi-clients-ca-cert
    Resourcetype:    secret
  Worker:
    Cpu:  1
Events:   <none>
````

To modify the cluster, run the following:
````
cat <<EOF | kubectl replace -f -
> apiVersion: lightbend.com/v1
> kind: FlinkCluster
> metadata:
>   name: my-cluster
> spec:
>   flinkConfiguration:
>     num_taskmanagers: 3
>     taskmanagers_slots: 2
> EOF
````
Keep in mind that replace command is not commulative. You need to specify all of the parameters, even if they existed in the original cluster

To delete the cluster, run the following:
````
oc delete FlinkCluster my-cluster
````

---
**Note**

The above CRD commands are not global, they only show the resources in a namespace that you are in.

---

## Metrics

Prometheus support is enabled via Helm chart
To see all available metrics, go to Prometheus console/graph and enter the following query:
````
{app_kubernetes_io_name="flink-operator"}
````
This will return the list of all metrics produced by the operator.
You should also be able to see operator and created clusters in the lightbend console

## License

Copyright (C) 2019 Lightbend Inc. (https://www.lightbend.com).

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
