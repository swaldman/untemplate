package untemplate

import scala.io.Codec
import scala.jdk.StreamConverters.StreamHasToScala
import scala.jdk.CollectionConverters.*
import java.nio.file.{Files,Path}
import zio.*

object PackageSource:
  def fromDirectory( dirPath : Path, codec : Codec = Codec.UTF8 ) : Task[PackageSource]=
    ZIO.attemptBlocking {
      val dirName = dirPath.getFileName.toString
      val pkg = toIdentifier(dirName)
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
        throw new NonuniqueIdentifier(s"""Directory '${dirName}' would generate duplicate transformers: ${(idVec.diff(idUniques)).toSet.mkString(",")}""")
      val identifiersToPaths = Map(idVec.zip(untemplatePaths): _*)
      val transformerSource: Identifier => Task[TransformerSource] =
        identifier => ZIO.attemptBlocking(asTransformerSource(Files.readAllLines(identifiersToPaths(identifier), codec.charSet).asScala.toVector))
      PackageSource(pkg, idVec, transformerSource)
    }

case class PackageSource(pkg : Identifier, transformers : Iterable[Identifier], transformerSource : Identifier => Task[TransformerSource])

/*
object PackageSource:
  class Directory( dirPath : Path ) extends PackageSource:
    override def packageName : String = toIdentifier(dirPath.getFileName.toString)
    override def transformerNames : Iterable[String] = Files.list(dirPath).toScala(Vector)


trait PackageSource:
  def packageName : String
  def transformerNames : Iterable[String]
  def sourceForTransformer( name : String ) : String
*/