package untemplate.mill

import mill.*
import mill.scalalib.*

import untemplate.*

trait UntemplateModule extends ScalaModule {

  import upickle.default.ReadWriter

  given ReadWriter[Identifier] = summon[ReadWriter[String]].bimap[Identifier]( id => id.toString, s => toIdentifier(s))
  given ReadWriter[LocationPackage] = summon[ReadWriter[List[Identifier]]].bimap[LocationPackage](lp => lp.toList, idl => LocationPackage(idl))

  def untemplateSourcesFolders : Seq[os.SubPath] = Seq("untemplate")

  def untemplateSourcesPrefixPackagesFromModuleDir : Map[String,String] = Map.empty

  def untemplateSourcesPrefixPackages : T[Map[os.Path,LocationPackage]] = Task:
    untemplateSourcesPrefixPackagesFromModuleDir.map( (k, v) => ( os.Path(k, moduleDir), LocationPackage.fromDotty(v) ) )

  def untemplateSources : T[Seq[PathRef]] = Task.Sources(untemplateSourcesFolders*)

  def untemplateFlatten = Task {
    false
  }

  def untemplateIndexNameFullyQualified : Option[String] = None

  def untemplateSelectCustomizer : untemplate.Customizer.Selector =
    untemplate.Customizer.NeverCustomize

  def untemplateGenerateScala = Task(persistent=true) {
    val prefixPackages = untemplateSourcesPrefixPackages().map( (k,v) => ( k.toNIO, v ) )
    Untemplate.unsafeTranspileRecursive(untemplateSources().map( _.path.toNIO ), Task.dest.toNIO, untemplateSelectCustomizer, untemplateIndexNameFullyQualified, untemplateFlatten(), prefixPackages)
    PathRef(Task.dest)
  }

  override def generatedSources = Task {
    super.generatedSources() :+ untemplateGenerateScala()
  }

  override def mvnDeps = Task{ super.mvnDeps() ++ Seq(mvn"com.mchange::untemplate:${untemplate.build.BuildInfo.UntemplateVersion}") }
}
