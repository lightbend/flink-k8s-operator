package com.lightbend.operator

import io.prometheus.client.{Counter, Gauge}

object MetricsHelper {
  private val PREFIX = "operator_"

  val reconciliationsTotal = Counter.build.name(PREFIX + "full_reconciliations_total")
    .help("How many times the full reconciliation has been run.")
    .labelNames("ns")
    .register

  val runningClusters = Gauge.build.name(PREFIX + "running_clusters")
    .help("Spark clusters that are currently running.")
    .labelNames("ns").register

  val workers = Gauge.build.name(PREFIX + "running_workers")
    .help("Number of workers per cluster name.")
    .labelNames("cluster", "ns").register

  val startedTotal = Gauge.build.name(PREFIX + "started_clusters_total")
    .help("Spark clusters has been started by operator.")
    .labelNames("ns").register
}
