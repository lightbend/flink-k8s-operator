import Dependencies._

// global settings for this build

name in ThisBuild := "fdp-flink-operator"
version in ThisBuild := "0.0.1"
organization in ThisBuild := "lightbend"
scalaVersion in ThisBuild := Versions.scalaVersion
scalaVersion := "2.12.8"



// settings for a native-packager based docker project based on sbt-docker plugin
def sbtdockerAppBase(id: String)(base: String = id): Project = Project(id, base = file(base))
  .enablePlugins(sbtdocker.DockerPlugin, JavaAppPackaging)
  .settings(
    dockerfile in docker := {
      val appDir = stage.value
      val targetDir = "/operator"

      new Dockerfile {
        from("lightbend/java-scala-operator-centos:1.0.0")
        copy(appDir, targetDir, chown = "jboss:root")
        run("chmod", "-R", "777", "/operator")
        entryPoint(s"$targetDir/bin/${executableScriptName.value}")
       }
    },
    
// Set name for the image
    imageNames in docker := Seq(
      ImageName(namespace = Some(organization.value),
        repository = name.value.toLowerCase,
        tag = Some(version.value))
    ),

    buildOptions in docker := BuildOptions(cache = false)
  )

lazy val model = (project in file("model"))
  .enablePlugins(ModelGeneratorPlugin)
  .settings(
    libraryDependencies ++= Seq(jsonGenerator, abstractOperator),
    modelSchemaLocation := "./schema/flinkCluster.json",
    (compile in Compile) := ((compile in Compile) dependsOn generateModel).value
  
  )


lazy val operator = sbtdockerAppBase("fdp-flink-operator")("./operator")
  .settings(mainClass in Compile := Some("io.radanalytics.operator.Entrypoint"))
  .settings(libraryDependencies ++= Seq(junit))
  .dependsOn(model)

lazy val flinkoperator = (project in file("."))
  .aggregate(model, operator)
