package untemplate

import scala.io.Codec
import scala.jdk.StreamConverters.StreamHasToScala
import scala.jdk.CollectionConverters.*
import java.nio.file.{Files,Path}
import zio.*

object PackageSource:

  private def assertDirectories( paths : Iterable[Path] ) : Task[Unit] =
    ZIO.attemptBlocking {
      require(paths.forall(p => Files.isDirectory(p)), s"""dirPath and baseDirPath must be directories! Paths: ${paths.mkString(", ")}""")
    }

  private def fromDirectory( locationPackage : LocationPackage, dirPath : Path, codec : Codec ) : Task[PackageSource] =
    ZIO.attemptBlocking {
      val allFilePaths = Files.list(dirPath).toScala(Vector)
      val untemplatePaths = allFilePaths.filter(path => Files.isRegularFile(path) && path.toString.endsWith(DotSuffix))
      val untemplateSourceNameVec = untemplatePaths.map(_.getFileName.toString)
      val untemplateSourceNamesToPaths = Map(untemplateSourceNameVec.zip(untemplatePaths): _*)
      val untemplateSourceMetadata: String => Task[UntemplateSource.Metadata] =
        untemplateSourceName => ZIO.attemptBlocking(UntemplateSource.Metadata(Some(Files.getLastModifiedTime(untemplateSourceNamesToPaths(untemplateSourceName)).toMillis)))
      val untemplateSource: String => Task[UntemplateSource] =
        untemplateSourceName => ZIO.attemptBlocking {
          val sourcePath = untemplateSourceNamesToPaths(untemplateSourceName)
          val lines = Files.readAllLines(sourcePath, codec.charSet).asScala.to(Vector)
          UntemplateSource( sourcePath.toString, lines )
        }
      PackageSource(locationPackage, untemplateSourceNameVec, untemplateSourceMetadata, untemplateSource)
    }

  def fromDirectory( dirPath : Path, baseDirPath : Option[Path] = None, codec : Codec = Codec.UTF8 ) : Task[PackageSource] =
    for
      _               <- assertDirectories(dirPath :: baseDirPath.toList)
      locationPackage <- LocationPackage.fromLocation( dirPath, baseDirPath )
      packageSource   <- fromDirectory(locationPackage, dirPath, codec)
    yield packageSource

  def fromBaseDirectoryRecursive(baseDirPath : Path, codec : Codec = Codec.UTF8 ) : Task[Set[PackageSource]] =
    val someBdp = Some(baseDirPath)
    for
      dirPaths     <- ZIO.attemptBlocking(Files.walk(baseDirPath).toScala(List).filter(path => Files.isDirectory(path)))
      taskList     =  dirPaths.map(dirPath => fromDirectory( dirPath, someBdp, codec)) //List[Task[PackageSource]]
      pkgSourceSet <- ZIO.mergeAll(taskList)(Set.empty[PackageSource])( (set, pkgSource) => set + pkgSource ) : Task[Set[PackageSource]]
    yield pkgSourceSet.filter( _.untemplateSourceNames.nonEmpty )

case class PackageSource (
  locationPackage          : LocationPackage,
  untemplateSourceNames    : Vector[String],
  untemplateSourceMetadata : String => Task[UntemplateSource.Metadata],
  untemplateSource         : String => Task[UntemplateSource]
)