package untemplate

import java.nio.file.{Files, Path}
import scopt.OParser
import zio.*

private val GeneratorScalaPrefix = "untemplate_"

object Main extends zio.ZIOAppDefault {
  case class Opts (
    source  : Path            = Path.of("."),
    dest    : Path            = null,
    extras  : GeneratorExtras = GeneratorExtras.empty,
    flatten : Boolean         = false
  )

  val builder = OParser.builder[Opts]
  val parser1 = {
    import builder._
    OParser.sequence(
      programName("untemplate"),
      head("untemplate", "0.0.1"),
      opt[String]('s', "source")
        .action((str, opts) => opts.copy(source = Path.of(str)))
        .valueName("<dir>")
        .optional()
        .text("path to untemplate source base directory"),
      opt[String]('d', "dest")
        .required()
        .action((str, opts) => opts.copy(dest = Path.of(str)))
        .valueName("<dir>")
        .text("path to destination directory for packages of generated scala source code"),
      opt[String]("default-input-name")
        .action( (str, opts) => opts.copy(extras = opts.extras.copy(mbDefaultInputName = Some(toIdentifier(str)))) )
        .valueName("<name>")
        .text("name for input identifier, if not specified within the template"),
      opt[String]("default-input-type")
        .action( (str, opts) => opts.copy(extras = opts.extras.copy(mbDefaultInputType = Some(str))) )
        .valueName("<type-name>")
        .text("name for input type, fully-qualified or resolvable via imports supplied here or built into the template"),
      opt[Seq[String]]("extra-imports")
        .action( (extras, opts) => opts.copy(extras = opts.extras.copy(extraImports = extras.toVector)) )
        .valueName("<import1>,<import2>,<import3>,...")
        .text("extra imports that should be included by default at the top level of templates"),
      opt[Unit]("flatten")
        .action( (_, opts) => opts.copy(flatten = true) )
        .text("places all outputs directly in dest, rather than reproducing any directory hierarchy in source")
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


  def genScalaSources(dest : Path, pkgSources : Set[PackageSource], extras : GeneratorExtras, flatten : Boolean) : ZIO[Any, Throwable, Unit] =
    def generateForGeneratorInPackage(generatorSourceName : String, pkgSource : PackageSource) : ZIO[Any, Throwable, Unit] =
      val destDirPath = if (flatten) dest else dest.resolve(path(pkgSource.pkg))
      val defaultFunctionIdentifier = generatorSourceNameToIdentifier(generatorSourceName)
      for
        generatorSource <- pkgSource.generatorSource(generatorSourceName)
        generatorScala  =  DefaultTranspiler(pkgSource.pkg,defaultFunctionIdentifier,extras,generatorSource)
        _               <- ZIO.attemptBlocking( Files.writeString(destDirPath.resolve(Path.of(s"${GeneratorScalaPrefix}${generatorScala.identifier}.scala")), generatorScala.text.toString, scala.io.Codec.UTF8.charSet) )
      yield ()
    def generateForPackageSource(pkgSource : PackageSource) : ZIO[Any,Throwable,Unit] =
      ZIO.mergeAll(pkgSource.generatorSourceNames.map( sourceName => generateForGeneratorInPackage(sourceName, pkgSource) ))( () )( (_:Unit,_:Unit) => () )
    ZIO.mergeAll(pkgSources.map(generateForPackageSource))( () )( (_:Unit,_:Unit) => () )


  def doIt( opts : Opts ) : ZIO[Any,Throwable,Unit] =
    for
      pkgSources <- loadPackageSources(opts.source)
      _          <- createPackageDirs(opts.dest, pkgSources)
      _          <- genScalaSources(opts.dest, pkgSources, opts.extras, opts.flatten)
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
