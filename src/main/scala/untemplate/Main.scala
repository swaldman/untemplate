package untemplate

import java.nio.file.{Files, Path}
import scopt.OParser
import zio.*

object Main extends zio.ZIOAppDefault {
  case class Opts(source: Path = Path.of("."), dest: Path = null)

  val builder = OParser.builder[Opts]
  val parser1 = {
    import builder._
    OParser.sequence(
      programName("untemplate"),
      head("untemplate", "0.0.1"),
      opt[String]('s', "source")
        .action((str, c) => c.copy(source = Path.of(str)))
        .valueName("<dir>")
        .optional()
        .text("path to untemplate source base directory"),
      opt[String]('d', "dest")
        .required()
        .action((str, c) => c.copy(dest = Path.of(str)))
        .valueName("<dir>")
        .text("path to destination directory for packages of generated scala source code")
    )
  }

  def path(pkg : List[Identifier]) =
    if pkg.isEmpty then
      Path.of(".")
    else
      Path.of(pkg.head.toString, pkg.tail.map( _.toString ) : _*)

  def loadPackageSources(source: Path) = PackageSource.fromBaseDirectoryRecursive(source)

  def createPackageDirs(dest: Path, pkgSources: Set[PackageSource]): ZIO[Any, Throwable, Unit] =
    def createPackageDir(pkg : List[Identifier]) : Unit =
      val fullPath = dest.resolve(path(pkg))
      Files.createDirectories(fullPath)
    ZIO.attemptBlocking( pkgSources.map( _.pkg ).foreach(createPackageDir) )


  def genScalaSources(dest : Path, pkgSources : Set[PackageSource]) : ZIO[Any, Throwable, Unit] =
    def generateForGeneratorInPackage(generator : Identifier, pkgSource : PackageSource) : ZIO[Any, Throwable, Unit] =
      val destDirPath = dest.resolve(path(pkgSource.pkg))
      for
        generatorSource <- pkgSource.generatorSource(generator)
        generatorScala  =  DefaultTranspiler(pkgSource.pkg,generator,GeneratorExtras.empty,generatorSource)
        _               <- ZIO.attemptBlocking( Files.writeString(destDirPath.resolve(Path.of(s"${generator}.scala")), generatorScala.toString, scala.io.Codec.UTF8.charSet) )
      yield ()
    def generateForPackageSource(pkgSource : PackageSource) : ZIO[Any,Throwable,Unit] =
      ZIO.mergeAll(pkgSource.generators.map( generator => generateForGeneratorInPackage(generator, pkgSource) ))( () )( (_:Unit,_:Unit) => () )
    ZIO.mergeAll(pkgSources.map(generateForPackageSource))( () )( (_:Unit,_:Unit) => () )


  def doIt( opts : Opts ) : ZIO[Any,Throwable,Unit] =
    for
      pkgSources <- loadPackageSources(opts.source)
      _          <- createPackageDirs(opts.dest, pkgSources)
      _          <- genScalaSources(opts.dest, pkgSources)
    yield ()

  def doIt( mbOpts : Option[Opts] ) : ZIO[Any,Throwable,Unit] =
    mbOpts match
      case Some(opts) => doIt(opts)
      case None       => ZIO.unit

  def parseArgs( args : Array[String] ) : ZIO[Any,Throwable,Option[Opts]] = ZIO.attempt( OParser.parse(parser1, args, Opts()) )

  def run =
    for
      args   <- getArgs
      mbOpts <- parseArgs(args.toArray)
      _      <- doIt(mbOpts)
    yield ()




}
