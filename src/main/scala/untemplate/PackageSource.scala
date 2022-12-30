package untemplate

import scala.io.Codec
import scala.jdk.StreamConverters.StreamHasToScala
import scala.jdk.CollectionConverters.*
import java.nio.file.{Files,Path}
import zio.*

object PackageSource:
  private val Suffix = "untemplate"
  private val DotSuffix    = "." + Suffix
  private val DotSuffixLen = DotSuffix.length

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
      val idVec = untemplatePaths.map {
        path =>
          val fname = path.getFileName.toString
          val noSuffix = fname.substring(0, fname.length - DotSuffixLen)
          toIdentifier(noSuffix)
      }
      val idUniques = idVec.distinct
      if (idVec.size != idUniques.size)
        throw new NonuniqueIdentifier(s"""Directory '${dirPath}' would define duplicate generators: ${(idVec.diff(idUniques)).toSet.mkString(",")}""")
      val identifiersToPaths = Map(idVec.zip(untemplatePaths): _*)
      val generatorSource: Identifier => Task[GeneratorSource] =
        identifier => ZIO.attemptBlocking(asGeneratorSource(Files.readAllLines(identifiersToPaths(identifier), codec.charSet).asScala.toVector))
      PackageSource(pkg, idVec, generatorSource)
    }

  def fromBaseDirectoryRecursive(baseDirPath : Path, codec : Codec = Codec.UTF8 ) : Task[Set[PackageSource]] =
    val someBdp = Some(baseDirPath)
    for
      dirPaths     <- ZIO.attemptBlocking(Files.walk(baseDirPath).toScala(List).filter(path => Files.isDirectory(path)))
      taskList     =  dirPaths.map(dirPath => fromDirectory( dirPath, someBdp, codec)) //List[Task[PackageSource]]
      pkgSourceSet <- ZIO.mergeAll(taskList)(Set.empty[PackageSource])( (set, pkgSource) => set + pkgSource ) : Task[Set[PackageSource]]
    yield
      pkgSourceSet.filter( _.generators.nonEmpty )

case class PackageSource(pkg : List[Identifier], generators : Vector[Identifier], generatorSource : Identifier => Task[GeneratorSource])