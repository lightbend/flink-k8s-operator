package com.lightbend.operator.model

import org.jsonschema2pojo._
import org.jsonschema2pojo.rules.RuleFactory
import java.io.File

import com.sun.codemodel.JCodeModel


object GenerateModel {

  def main(args: Array[String]): Unit = {


    val codeModel = new JCodeModel()
    val source = this.getClass.getClassLoader.getResource("schema/flinkCluster.json")

    val outputPojoDirectory=new File("./model/target/generated-sources/jsonschema2pojo/")
    outputPojoDirectory.exists() match {
      case false => outputPojoDirectory.mkdirs()
      case _ =>
    }

    val config = new DefaultGenerationConfig() {
      override def isGenerateBuilders: Boolean = { // set config option by overriding method
        true
      }
   }

    val mapper = new SchemaMapper(new RuleFactory(config, new Jackson2Annotator(config), new SchemaStore()), new SchemaGenerator())
    mapper.generate(codeModel, "FlinkCluster", "com.lightbend.operator.types", source)
    codeModel.build(outputPojoDirectory)
  }
}
