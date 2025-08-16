package untemplate.mill

import mill.*
import mill.scalalib.*

import untemplate.*

trait UntemplateModule extends ScalaModule {

  def untemplateSourcesFolders : Seq[os.SubPath] = Seq("untemplate")

  def untemplateSources : T[Seq[PathRef]] = Task.Sources(untemplateSourcesFolders*)

  def untemplateFlatten = Task {
    false
  }

  def untemplateIndexNameFullyQualified : Option[String] = None

  def untemplateSelectCustomizer : untemplate.Customizer.Selector =
    untemplate.Customizer.NeverCustomize

  def untemplateGenerateScala = Task(persistent=true) {
    Untemplate.unsafeTranspileRecursive(untemplateSources().map( _.path.toNIO ), Task.dest.toNIO, untemplateSelectCustomizer, untemplateIndexNameFullyQualified, untemplateFlatten())
    PathRef(Task.dest)
  }

  override def generatedSources = Task {
    super.generatedSources() :+ untemplateGenerateScala()
  }

  override def mvnDeps = Task{ super.mvnDeps() ++ Seq(mvn"com.mchange::untemplate:${untemplate.build.BuildInfo.UntemplateVersion}") }
}
