import Versions._
import sbt._

object Dependencies {

  val abstractOperator      = "io.radanalytics"         % "abstract-operator"             % abstractOperatorVersion
  val junit                 =  "junit"                  % "junit"                         % junitVersion              % Test
}
