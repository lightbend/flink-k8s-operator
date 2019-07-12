package com.lightbend.operator

import com.google.gson.Gson
import scalaj.http.{Http, HttpResponse}

object GetClusterStatus {

  val gson = new Gson()

  def getStatus(service: String, port : Int = 8081): FlinkStatus = {
    val response: HttpResponse[String] = Http(s"http://$service:$port/jobs").asString
    response.isSuccess match {
      case true =>  // We got success reply
        FlinkStatus("RUNNING", gson.fromJson(response.body, classOf[FlinkJobs]).jobs)
      case _ => // Job Query failed
        FlinkStatus("FAILED", Seq.empty)
    }
  }

  def main(args: Array[String]): Unit = {
    val status = getStatus("fdp-taxiride-jobmanager-flink.fiorano.lightbend.com")
    println(s"Cluster state is - ${status.status}")
    status.jobs.foreach(job => println(s"job : id - ${job.id}; status - ${job.status}"))
  }
}

case class FlinkJob(id : String, status : String)

case class FlinkJobs(jobs : Array[FlinkJob])

case class FlinkStatus(status : String, jobs : Seq[FlinkJob])