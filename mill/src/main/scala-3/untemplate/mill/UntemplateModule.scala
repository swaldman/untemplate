package untemplate.mill

import mill.*, scalalib.*

import untemplate.*

trait UntemplateModule extends ScalaModule {

  def untemplateSource = Task.Source:
    moduleDir / "untemplate"

  def untemplateFlatten = Task:
    false

  def untemplateIndexNameFullyQualified : Option[String] = None

  def untemplateSelectCustomizer : untemplate.Customizer.Selector =
    untemplate.Customizer.NeverCustomize

  def untemplateGenerateScala = Task(persistent=true):
    Untemplate.unsafeTranspileRecursive(untemplateSource().path.toNIO, Task.dest.toNIO, untemplateSelectCustomizer, untemplateIndexNameFullyQualified, untemplateFlatten())
    PathRef(Task.dest)

  override def generatedSources : T[Seq[PathRef]] = Task {
    super.generatedSources() :+ untemplateGenerateScala()
  }

  override def mvnDeps = Task:
    super.mvnDeps() ++ Seq(mvn"com.mchange::untemplate:${untemplate.build.BuildInfo.UntemplateVersion}")
}
