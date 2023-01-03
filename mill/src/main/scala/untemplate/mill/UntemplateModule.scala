package untemplate.mill

import mill._
import mill.define._
import mill.scalalib._

trait UntemplateModule extends ScalaModule {
  def untemplateSource: Source = T.source {
    millSourcePath / Symbol("src") / Symbol("untemplate")
  }

  def generate = T {
    //println( s">>> untemplateSource: ${untemplateSource().path.toNIO}" )
    val opts = untemplate.Main.Opts(untemplateSource().path.toNIO, T.dest.toNIO)
    untemplate.Main.unsafeDoIt(opts)
  }
}