package untemplate

import scala.io.Codec
import scala.jdk.StreamConverters.StreamHasToScala
import scala.jdk.CollectionConverters.*
import java.nio.file.{Files,Path}
import zio.*

object PackageSource:
  private val EmptyStringList = "" :: Nil

  def fromDirectory( dirPath : Path, baseDirPath : Option[Path] = None, codec : Codec = Codec.UTF8 ) : Task[PackageSource] =
    ZIO.attemptBlocking {
      val pkg =
        val rel = baseDirPath.map( _.relativize(dirPath) ).getOrElse( dirPath.getFileName )
        val pieces = rel.iterator().asScala.map(_.toString).toList
        if pieces == EmptyStringList then Nil
        else pieces.map( piece => toIdentifier(piece) ) // no pkg dir shows up as an empty string, we want an empty list
      val allFilePaths = Files.list(dirPath).toScala(Vector)
      val untemplatePaths = allFilePaths.filter(path => Files.isRegularFile(path) && path.toString.endsWith(DotSuffix))
      val generatorSourceNameVec = untemplatePaths.map( _.getFileName.toString )
      val generatorSourceNamesToPaths = Map(generatorSourceNameVec.zip(untemplatePaths): _*)
      val generatorSource: String => Task[GeneratorSource] =
        generatorSourceName => ZIO.attemptBlocking(asGeneratorSource(Files.readAllLines(generatorSourceNamesToPaths(generatorSourceName), codec.charSet).asScala.toVector))
      PackageSource(pkg, generatorSourceNameVec, generatorSource)
    }

  def fromBaseDirectoryRecursive(baseDirPath : Path, codec : Codec = Codec.UTF8 ) : Task[Set[PackageSource]] =
    val someBdp = Some(baseDirPath)
    for
      dirPaths     <- ZIO.attemptBlocking(Files.walk(baseDirPath).toScala(List).filter(path => Files.isDirectory(path)))
      taskList     =  dirPaths.map(dirPath => fromDirectory( dirPath, someBdp, codec)) //List[Task[PackageSource]]
      pkgSourceSet <- ZIO.mergeAll(taskList)(Set.empty[PackageSource])( (set, pkgSource) => set + pkgSource ) : Task[Set[PackageSource]]
    yield
      pkgSourceSet.filter( _.generatorSourceNames.nonEmpty /* || scalaSourceNames.nonEmpty */ )

case class PackageSource(pkg : List[Identifier], generatorSourceNames : Vector[String], generatorSource : String => Task[GeneratorSource])