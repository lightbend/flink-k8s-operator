# Flink-operator


`CRD`-based approach for managing Flink clusters in Kubernetes and OpenShift.

This operator uses [abstract-operator](https://github.com/jvm-operators/abstract-operator) library.

##Building
THe operator has 2 participating projects:
* model - json definition of the model and scala code to build java classes (the reason is that SBT does not have an equivalent to [jsonschema2pojo-maven-plugin](https://github.com/joelittlejohn/jsonschema2pojo) so it is implemented as a separate project)
* operator - the actual implementation of the operator

Build can be done running command:
````
 sbt docker 
````
Docker build here leverages base image, that can be build using the following [docker file](./Dockerfile)

##Installation
To install operator use [Helm](helm/flink-operator) 

Installation parameters include operator image information:
* repository - operator docker name (lightbend/fdp-flink-operator)
* tag - operator docker tag (0.0.1)
* pullPolicy - operator docker pull policy (always)

Operator execution environment, including: 

* namespace - see below
* reconciliationInterval - how often (in seconds) the full reconciliation should be run (180)
* metrics - should we expose operator metrics for Prometheus (true)
* metricsPort - port for the metrics http server (8080)
* internalJvmMetrics - should we expose operator's internal JVM metrics (true)

Current implementation of the abstract operator currently support three options of namespace watching
(see class EntryPoint class, run method, lines 70-98)
* current namespace - namespace where operator starts, specify empty list
* all namespaces, specify "*"
* list of specific namespaces, specify comma separated list

Operator's resource requirements
* memory requirement for an operator (512Mi)
* cpu requirement for an operator (1000m)

##Basic commands
To create a cluster run something similar to 
````
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
````
Additional parameters can be added. See [example](yaml/cluster_complete.yaml)

##Seeing what is running

To see running clusters run 
````
oc get FlinkCluster
NAME         AGE
my-cluster   13m
```` 

To get the information about specific cluster, run
````
oc describe FlinkCluster my-cluster
Name:         my-cluster
Namespace:    flink
Labels:       <none>
Annotations:  <none>
API Version:  lightbend.com/v1
Kind:         FlinkCluster
Metadata:
  Cluster Name:        
  Creation Timestamp:  2019-03-20T19:00:29Z
  Generation:          1
  Resource Version:    12312782
  Self Link:           /apis/lightbend.com/v1/namespaces/flink/flinkclusters/my-cluster
  UID:                 6e16a9f4-4b42-11e9-bb33-0643529e7baa
Spec:
  Flink Configuration:
    Num _ Taskmanagers:    2
    Taskmanagers _ Slots:  2
Events:                    <none>
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
  Cluster Name:        
  Creation Timestamp:  2019-03-20T19:00:29Z
  Generation:          1
  Resource Version:    12312782
  Self Link:           /apis/lightbend.com/v1/namespaces/flink/flinkclusters/my-cluster
  UID:                 6e16a9f4-4b42-11e9-bb33-0643529e7baa
Spec:
  Flink Configuration:
    Num _ Taskmanagers:    2
    Taskmanagers _ Slots:  2
Events:                    <none>
````
To modify the cluster run the following:
````
cat <<EOF | kubectl replace -f -
> apiVersion: lightbend.com/v1
> kind: FlinkCluster
> metadata:
>   name: my-cluster
> spec:
>   flinkConfiguration:
>     num_taskmanagers: "3"
>     taskmanagers_slots: "2"
> EOF
````

To delete the cluster run the following:
````
oc delete FlinkCluster my-cluster
````

**Note** *The above CRD commands are not global, They only show the resources in a namespace that you are in.* 

##Metrics
Prometheus support is enabled via Helm chart
To see all available metrics, go to Prometheus console/graph and enter the following query
````
{app_kubernetes_io_name="flink-operator"}
````
This will return the list of all metrics produced by the operator.
You should also be able to see operator and created clusters in the lightbend console
