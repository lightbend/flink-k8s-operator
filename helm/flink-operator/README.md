# flink-operator
CRD-based approach for managing Flink clusters and apps in Kubernetes and OpenShift.

# Installation
```
helm install flink-operator
```


The operator needs to create Service Account, Role and Role Binding. 

# Usage
Create Apache Flink Cluster:

```
cat <<EOF | kubectl create -f -
apiVersion: lightbend.com/v1
kind: FlinkCluster
metadata:
  name: my-cluster
spec:
  flinkConfiguration:
    num_taskmanagers: "2"
    taskmanagers_slots: "2"
EOF
```

Additionall parameters that can be configured for cluser are:

| Parameter                    | Description                                                  | Default                                 |
| ---------------------------- | ------------------------------------------------------------ | --------------------------------------- |
| `spec.metric'`               | enable metrics                                               | `true`                                  |
| `spec.customImage'           | image name                                                   | `lightbend/flink:1.7.2-scala_2.11`      |
| `spec.flinkConfiguration.num_taskmanagers'  | number of task managers                       | `2`                                     |
| `spec.flinkConfiguration.taskmanagers_slots'| number of slots per task manager              | `2`                                     |
| `spec.labels'                | additional labels                                            |  None                                   |
| `spec.env'                   | additional env                                               |  None                                   |
| `spec.worker.cpus'           | cpus per taskmanager                                         |  '2'                                    |
| `spec.worker.memory'         | memory per taskmanager                                       |  '2048'                                 |
| `spec.master.cpus'           | cpus per jpbmanager                                          |  '2'                                    |
| `spec.master.memory'         | memory per jobmanager                                        |  '1048'                                 |
| `spec.master.inputs'         | inputs to jobmanager jobmanager, used to start jobs at cluster creation | None                         |



### Configuration

_The following table lists the configurable parameters of the Flink operator chart and their default values._


| Parameter                    | Description                                                  | Default                                 |
| ---------------------------- | ------------------------------------------------------------ | --------------------------------------- |
| `image.repository`           | The name of the operator image                               | `lightbend/fdp-flink-operator`          |
| `image.tag`                  | The image tag representing the version of the operator       | `0.0.1`                                 |
| `image.pullPolicy`           | Container image pull policy                                  | `IfNotPresent`                          |
| `env.namespace`              | Kubernetes namespace where Flink operator watches for events. If `*` is used, it watches in all namespaces, if empty string is used, it will watch only in the same namespace the operator is deployed in.   | `""`                                    |
| `env.reconciliationInterval` | How often (in seconds) the full reconciliation should be run | `180`                                   |
| `env.metrics`                | Whether to start metrics server to be scraped by Prometheus. | `false`                                 |
| `env.metricsPort`            | The port for the metrics http server                         | `8080`                                  |
| `env.internalJvmMetrics`     | Whether to expose also internal JVM metrics?                 | `false`                                 |
| `resources.memory`           | Memory limit for the operator pod (used by K8s scheduler)    | `512Mi`                                 |
| `resources.cpu`              | Cpu limit for the operator pod (used by K8s scheduler)       | `1000m`                                 |

Specify each parameter using the `--set key=value[,key=value]` argument to `helm install` or `helm template`.
