import sbt._
import sbt.Keys._
import org.jsonschema2pojo._
import org.jsonschema2pojo.rules.RuleFactory
import java.io.File

import com.sun.codemodel.JCodeModel

object ModelGeneratorPlugin extends AutoPlugin {
  
  object autoImport {
    lazy val generateModel = taskKey[Unit]("Generates the Model from a JSON Schema")
    lazy val modelSchemaLocation = settingKey[String]("The source for the schema definition")
  }

  import autoImport._

  val GeneratedSrcLocation = "target/generated-sources/jsonschema2pojo/"

  override def projectSettings = Seq(
    generateModel := Def.taskDyn {
      Def.task {
        val schemaLocation = modelSchemaLocation.value
        val baseDir = baseDirectory.value
        generate(schemaLocation, baseDir)
      }
    }.value,
    unmanagedSourceDirectories in Compile += baseDirectory.value / GeneratedSrcLocation
  )

  def generate(schemaResource: String, baseDir: File)= {

    val codeModel = new JCodeModel()
    val source = new File(schemaResource).toURI.toURL

    val outputPojoDirectory=new File(baseDir, GeneratedSrcLocation)
    if (!outputPojoDirectory.exists()) {
      outputPojoDirectory.mkdirs()
    }

    val config = new DefaultGenerationConfig() {
      override val isGenerateBuilders: Boolean = true  // set config option by overriding method
    }

    val mapper = new SchemaMapper(new RuleFactory(config, new Jackson2Annotator(config), new SchemaStore()), new SchemaGenerator())
    mapper.generate(codeModel, "FlinkCluster", "com.lightbend.operator.types", source)
    codeModel.build(outputPojoDirectory)
  }
}


