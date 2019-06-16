package com.lightbend.operator

import scalaj.http.{Http, HttpResponse}

object GetClusterStatus {

  def getStatus(service: String): Unit = {
    val response: HttpResponse[String] = Http(s"http://$service/jobs").asString
    println(s"Execution code : ${response.code}")
    println(s"Execution result : ${response.body}")
  }

  def main(args: Array[String]): Unit = {
    getStatus("my-cluster-jobmanager-flink.fiorano.lightbend.com")
  }
}