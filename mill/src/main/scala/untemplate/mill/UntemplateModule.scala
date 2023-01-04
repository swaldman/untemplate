package untemplate.mill

import mill._
import mill.define._
import mill.scalalib._
import os._
import mill.define.Source

trait UntemplateModule extends ScalaModule {
  def untemplateSource: Source = T.source {
    millSourcePath / "src" / "untemplate"
  }

  def generateUntemplateScala = T {
    //println( s">>> untemplateSource: ${untemplateSource().path.toNIO}" )
    val opts = untemplate.Main.Opts(untemplateSource().path.toNIO, T.dest.toNIO)
    untemplate.Main.unsafeDoIt(opts)
    PathRef(T.dest)
  }

  override def generatedSources = T {
    super.generatedSources() ++ filesUnder(Seq(generateUntemplateScala()), ".scala")
  }

  override def ivyDeps = T{ super.ivyDeps() ++ Agg(ivy"com.mchange::untemplate:0.0.1-SNAPSHOT") }

  // from https://github.com/vic/mill-scalaxb/blob/master/scalaxb/src/ScalaxbModule.scala
  private def filesUnder(path: Seq[PathRef], extension: String): Seq[PathRef] =
    path
      .flatMap(p => walk(p.path))
      .filter(_.toString.endsWith(extension))
      .map(PathRef(_))

}