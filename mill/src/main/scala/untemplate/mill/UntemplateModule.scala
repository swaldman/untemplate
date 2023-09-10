package untemplate.mill

import mill._
import mill.define._
import mill.scalalib._
import mill.define.Target

import untemplate._

// note: expect compilation as Scala 2.13!

trait UntemplateModule extends ScalaModule {

  def untemplateSource : Target[PathRef] = T.source {
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
    PathRef(T.dest) +: os.walk(T.dest).map(PathRef(_)) // we rely in generatedSources on T.dest being the head
  }

  override def generatedSources = T { // we rely on untemplateGenerateScala placing the top dir path as head
    super.generatedSources() ++ scalaUnder( untemplateGenerateScala().head.path ).map(PathRef(_))
  }

  override def ivyDeps = T{ super.ivyDeps() ++ Agg(ivy"com.mchange::untemplate:${untemplate.build.BuildInfo.UntemplateVersion}") }

  // inspired by https://github.com/vic/mill-scalaxb/blob/master/scalaxb/src/ScalaxbModule.scala
  private def scalaUnder( path : os.Path ) : Seq[os.Path] =
    os.walk(path).filter( os.isFile ).filter( _.last.endsWith(".scala") )
}
