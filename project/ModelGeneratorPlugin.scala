import sbt._
import org.jsonschema2pojo._
import org.jsonschema2pojo.rules.RuleFactory
import java.io.File

import com.sun.codemodel.JCodeModel
import sbt.Opts.compile

object ModelGeneratorPlugin extends AutoPlugin {

  //override def trigger = allRequirements

  object autoImport {
    lazy val generateModel = taskKey[Unit]("Generates the Model from a JSON Schema")
    lazy val modelSchemaLocation = settingKey[String]("The source for the schema definition")
    lazy val sayHello = taskKey[Unit]("say hello")
  }

  import autoImport._

  override def projectSettings = Seq(
    generateModel := Def.taskDyn {
      Def.task {
        val schemaLocation = modelSchemaLocation.value
        generate(schemaLocation)
      }
    }.value
    
  )

  def generate(schemaResource: String)= {


    val codeModel = new JCodeModel()
    val source = new File(schemaResource).toURI.toURL
    println(s"Using source: $source")

    val outputPojoDirectory=new File("./target/generated-sources/jsonschema2pojo/")
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


