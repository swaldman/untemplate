package untemplate

import scala.io.Codec
import scala.jdk.StreamConverters.StreamHasToScala
import scala.jdk.CollectionConverters.*
import java.nio.file.{Files,Path}
import zio.*

object PackageSource:
  def fromDirectory( dirPath : Path, basePath : Option[Path] = None, codec : Codec = Codec.UTF8 ) : Task[PackageSource] =
    ZIO.attemptBlocking {
      val pkg =
        val rel = basePath.map( _.relativize(dirPath) ).getOrElse( dirPath.getFileName )
        val pieces = rel.iterator().asScala.toList
        pieces.map( piece => toIdentifier(piece.toString) )
      val allFilePaths = Files.list(dirPath).toScala(Vector)
      val untemplatePaths = allFilePaths.filter(_.toString.endsWith(DotSuffix))
      val idVec = untemplatePaths.map {
        path =>
          val fname = path.getFileName.toString
          val noSuffix = fname.substring(0, fname.length - DotSuffixLen)
          toIdentifier(noSuffix)
      }
      val idUniques = idVec.distinct
      if (idVec.size != idUniques.size)
        throw new NonuniqueIdentifier(s"""Directory '${dirName}' would generate duplicate generators: ${(idVec.diff(idUniques)).toSet.mkString(",")}""")
      val identifiersToPaths = Map(idVec.zip(untemplatePaths): _*)
      val generatorSource: Identifier => Task[GeneratorSource] =
        identifier => ZIO.attemptBlocking(asGeneratorSource(Files.readAllLines(identifiersToPaths(identifier), codec.charSet).asScala.toVector))
      PackageSource(pkg, idVec, generatorSource)
    }

case class PackageSource(pkg : List[Identifier], generators : Iterable[Identifier], generatorSource : Identifier => Task[GeneratorSource])