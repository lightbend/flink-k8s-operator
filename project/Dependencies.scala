import Versions._
import sbt._

object Dependencies {

  val abstractOperator      = "io.radanalytics"         % "abstract-operator"             % abstractOperatorVersion
  val scalaHTTP             = "org.scalaj"              %% "scalaj-http"                  % scalaHTTPVersion
  val gson                  = "com.google.code.gson"    % "gson"                          % gsonVersion
  val junit                 =  "junit"                  % "junit"                         % junitVersion              % Test
}
