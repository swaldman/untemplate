package untemplate.mill

import mill._
import mill.define._
import mill.scalalib._
import mill.define.Source

// note: expect compilation as Scala 2.13!

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
    super.generatedSources() ++ scalaUnder( generateUntemplateScala().path ).map(PathRef(_))
  }

  override def ivyDeps = T{ super.ivyDeps() ++ Agg(ivy"com.mchange::untemplate:0.0.1-SNAPSHOT") }

  // inspired by modified from https://github.com/vic/mill-scalaxb/blob/master/scalaxb/src/ScalaxbModule.scala
  private def scalaUnder( path : os.Path ) : Seq[os.Path] =
    os.walk(path).filter( os.isFile ).filter( _.last.endsWith(".scala") )
}