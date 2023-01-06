package untemplate

import zio.*

import java.nio.file.{Files, Path}
import scala.io.Codec
import scala.jdk.CollectionConverters.*

object SingleSource:

  private def fromFile( locationPackage : LocationPackage, filePath : Path, codec : Codec ) : Task[SingleSource] =
    ZIO.attemptBlocking {
      val untemplateSourceMetadata = ZIO.attemptBlocking(UntemplateSourceMetadata(Some(Files.getLastModifiedTime(filePath).toMillis)))
      val untemplateSource = ZIO.attemptBlocking(asUntemplateSource(Files.readAllLines(filePath, codec.charSet).asScala.to(Vector)))
      SingleSource(locationPackage, filePath.getFileName.toString, untemplateSourceMetadata, untemplateSource)
    }

  def fromFile( filePath : Path, baseDirPath : Option[Path] = None, codec : Codec = Codec.UTF8 ) : Task[SingleSource] =
    for
      _ <- ZIO.attempt( require(Files.isRegularFile(filePath), s"Putative untemplate source file '${filePath}' must be a regular file.") )
      locationPackage <- LocationPackage.fromLocation(filePath, baseDirPath)
      singleSource    <- fromFile( locationPackage, filePath, codec )
    yield singleSource

case class SingleSource (
  locationPackage          : LocationPackage,
  untemplateSourceName     : String,
  untemplateSourceMetadata : Task[UntemplateSourceMetadata],
  untemplateSource         : Task[UntemplateSource]
)
