import Versions._
import sbt._

object Dependencies {

  val abstractOperator      = "io.radanalytics"         % "abstract-operator"             % abstractOperatorVersion
  val jsonGenerator         = "org.jsonschema2pojo"     % "jsonschema2pojo-core"          % jsonGeneratorVersion
  val junit                 =  "junit"                  % "junit"                         % junitVersion              % Test
}
