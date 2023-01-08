package untemplate.mill

import mill._
import mill.define._
import mill.scalalib._
import mill.define.Source

import untemplate._

// note: expect compilation as Scala 2.13!

/*
If you get this:

    error while loading Customizer, Missing dependency 'Add -Ytasty-reader to scalac options to parse the TASTy in /Users/swaldman/.ivy2/local/com.mchange/untemplate_3/0.0.1-SNAPSHOT/jars/untemplate_3.jar(untemplate/Customizer.class)', required by /Users/swaldman/.ivy2/local/com.mchange/untemplate_3/0.0.1-SNAPSHOT/jars/untemplate_3.jar(untemplate/Customizer.class)
    /Users/swaldman/Dropbox/BaseFolders/development-why/gitproj/untemplate-doc/build.sc:30: Symbol 'type untemplate.Customizer.Selector' is missing from the classpath.
      This symbol is required by 'method untemplate.mill.UntemplateModule.untemplateSelectCustomizer'.
      Make sure that type Selector is in your classpath and check for conflicting dependencies with `-Ylog-classpath`.
        A full rebuild may help if 'UntemplateModule.class' was compiled against an incompatible version of untemplate.Customizer.
      object untemplatedocs extends UntemplateModule {
        ^
        Compilation Failed

Add to ~/.mill/ammonite/predefScript.sc the following snippet (thank @lolgab on discord!):

    interp.configureCompiler { c =>
      val settings = c.settings
      settings.YtastyReader.value = true
    }

*/

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

  // def untemplateSelectCustomizer : Any =
  def untemplateSelectCustomizer : untemplate.Customizer.Selector =
    untemplate.Customizer.NeverCustomize

  def untemplateGenerateScala = T {
    Untemplate.unsafeTranspileRecursive(untemplateSource().path.toNIO, T.dest.toNIO, untemplateSelectCustomizer, untemplateFlatten())
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