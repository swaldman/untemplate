package untemplate.mill

import mill._
import mill.define._
import mill.scalalib._
import mill.define.Source

import untemplate._

// note: expect compilation as Scala 2.13!

trait UntemplateModule extends ScalaModule {

  def untemplateSource : Source = T.source {
    millSourcePath / "untemplate"
  }

  def untemplateFlatten = T {
    false
  }

  def untemplateIndexNameFullyQualified : Option[String] = None

  def untemplateSelectCustomizer : untemplate.Customizer.Selector =
    untemplate.Customizer.NeverCustomize

  def untemplateGenerateScala = T.persistent {
    Untemplate.unsafeTranspileRecursive(untemplateSource().path.toNIO, T.dest.toNIO, untemplateSelectCustomizer, untemplateIndexNameFullyQualified, untemplateFlatten())
    PathRef(T.dest)
  }

  override def generatedSources = T {
    super.generatedSources() ++ scalaUnder( untemplateGenerateScala().path ).map(PathRef(_))
  }

  override def ivyDeps = T{ super.ivyDeps() ++ Agg(ivy"com.mchange::untemplate:${untemplate.build.BuildInfo.UntemplateVersion}") }

  // inspired by https://github.com/vic/mill-scalaxb/blob/master/scalaxb/src/ScalaxbModule.scala
  private def scalaUnder( path : os.Path ) : Seq[os.Path] =
    os.walk(path).filter( os.isFile ).filter( _.last.endsWith(".scala") )
}