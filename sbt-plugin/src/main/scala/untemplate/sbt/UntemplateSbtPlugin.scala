package untemplate.sbt

import sbt._
import Keys._

import java.io.File
import scala.collection._
import scala.sys.process.Process

object UntemplateSbtPlugin extends AutoPlugin {
  override def trigger = allRequirements

  object autoImport {
    val untemplateSource           = settingKey[File]("Directory where source for untemplate packages can be found")
    val untemplateScala            = settingKey[File]("Directory into which untemplate scala files are generated")
    val untemplateDefaultInputName = settingKey[String]("The name of the input variable in generated source, if none is specified in the template")
    val untemplateDefaultInputType = settingKey[String]("The type of the input variable in generated source, if none is specified in the template")
    val untemplateExtraImports     = settingKey[Seq[String]]("Top-level import statements that should be inserted into untemplate scala")
    val untemplateCommand          = settingKey[String]("untemplate must be run as an external command by sbt, since plugins cannot interact directly with Scala 3")
  }

  import autoImport._
  //override lazy val globalSettings: Seq[Setting[_]] = Seq(
  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    untemplateSource       := (Compile / sourceDirectory).value / "untemplate",
    untemplateScala        := (Compile / sourceManaged).value / "untemplate",
    untemplateExtraImports := Nil,
    untemplateCommand      := "untemplate",
    (Compile / sourceGenerators) += Def.task {
      val srcDir  = untemplateSource.value
      val destDir = untemplateScala.value
      val mbDefaultInputName = untemplateDefaultInputName.?.value
      val mbDefaultInputType = untemplateDefaultInputType.?.value
      val commandName = untemplateCommand.value
      val commandBuff = mutable.Buffer.empty[String]
      val extraImports = untemplateExtraImports.value
      commandBuff += commandName
      commandBuff += "--source"
      commandBuff += srcDir.getAbsolutePath.toString
      commandBuff += "--dest"
      commandBuff += destDir.getAbsolutePath.toString
      mbDefaultInputName.foreach { name =>
        commandBuff += "--default-input-name"
        commandBuff += name
      }
      mbDefaultInputType.foreach { tpe =>
        commandBuff += "--default-input-type"
        commandBuff += tpe
      }
      if (extraImports.nonEmpty) {
        commandBuff += "--extra-imports"
        commandBuff += extraImports.mkString(",")
      }
      val command = commandBuff.toList
      val exit = Process(command).run().exitValue
      if (exit != 0)
        throw new Exception(s"""untemplate process failed with exit code ${exit}, parsed command was: ${command.mkString(",")}""")

      import sbt.nio.file._
      val files = FileTreeView.default.list(Glob(destDir) / ** / "untemplate_*.scala").collect {
        case (path, attributes) if attributes.isRegularFile => path.toFile
      }
      files.toSeq
    }
  )
}
