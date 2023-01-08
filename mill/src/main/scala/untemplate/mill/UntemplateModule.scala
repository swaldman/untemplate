package untemplate.mill

import mill._
import mill.define._
import mill.scalalib._
import mill.define.Source

import untemplate._

// note: expect compilation as Scala 2.13!

trait UntemplateModule extends ScalaModule {
  def untemplateSource : Source = T.source {
    millSourcePath / "src" / "untemplate"
  }

  def untemplateFlatten = T {
    false
  }

  // we really want to return Customizer.Select directly here, but if we do,
  // build.sc tries to resolve Scala 3 untemplate classes, and build.sc
  // compiles without scalac option `-Ytasty-reader`, so that fails.
  //
  // we return Any, then cast, because even to return a Function1[I,O] would
  // require us to refer to untemplate classes.
  //
  // eventually mill will more easily integrate with Scala 3, and this should
  // be revisted.
  
  def untemplateSelectCustomizer : Any =
    untemplate.Customizer.NeverCustomize

  def untemplateGenerateScala = T {
    val selectCustomizer = untemplateSelectCustomizer.asInstanceOf[Customizer.Selector]
    Untemplate.unsafeTranspileRecursive(untemplateSource().path.toNIO, T.dest.toNIO, selectCustomizer, untemplateFlatten())
    PathRef(T.dest)
  }

  override def generatedSources = T {
    super.generatedSources() ++ scalaUnder( untemplateGenerateScala().path ).map(PathRef(_))
  }

  override def ivyDeps = T{ super.ivyDeps() ++ Agg(ivy"com.mchange::untemplate:0.0.1-SNAPSHOT") }

  // inspired by https://github.com/vic/mill-scalaxb/blob/master/scalaxb/src/ScalaxbModule.scala
  private def scalaUnder( path : os.Path ) : Seq[os.Path] =
    os.walk(path).filter( os.isFile ).filter( _.last.endsWith(".scala") )
}