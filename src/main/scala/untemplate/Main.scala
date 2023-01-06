package untemplate

import java.nio.file.{Files, Path}
import scopt.OParser
import zio.*

private val UntemplateScalaPrefix = "untemplate_"

object Main extends zio.ZIOAppDefault {
  final case class Opts (
    source  : Path            = Path.of("."),
    dest    : Path            = null,
    extras  : UntemplateExtras = UntemplateExtras.empty,
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

  def loadPackageSources(source: Path) = PackageSource.fromBaseDirectoryRecursive(source)

  def createPackageDirs(dest: Path, pkgSources: Set[PackageSource]): ZIO[Any, Throwable, Unit] =
    def createPackageDir(locationPackage : LocationPackage) : Unit =
      val fullPath = dest.resolve(locationPackage.toPath)
      Files.createDirectories(fullPath)
    ZIO.attemptBlocking( pkgSources.map( _.locationPackage ).foreach(createPackageDir) )


  def genScalaSources(dest : Path, pkgSources : Set[PackageSource], extras : UntemplateExtras, flatten : Boolean) : ZIO[Any, Throwable, Unit] =
    def flattenEnsureNoDups( outputFileNames : List[String] ) : Unit =
      if (flatten)
        val dups = outputFileNames.groupBy(identity).collect{ case (x, ys) if ys.tail.nonEmpty => x }
        if (dups.nonEmpty)
          throw new NonuniqueIdentifier (
            "When flattening generation from a hierarchy of untemplate files, duplicate untemplate " +
            "identifiers and therefore filenames were generated, causing some templates to be overwritten. Duplicated file names: " +
              dups.mkString(", ")
          )
    def generateForUntemplateInPackage(untemplateSourceName : String, pkgSource : PackageSource) : ZIO[Any, Throwable, String] =
      val destDirPath = if (flatten) dest else dest.resolve(pkgSource.locationPackage.toPath)
      val defaultFunctionIdentifier = untemplateSourceNameToIdentifier(untemplateSourceName)
      for
        untemplateSource <- pkgSource.untemplateSource(untemplateSourceName)
        untemplateScala  =  DefaultTranspiler(pkgSource.locationPackage,defaultFunctionIdentifier,extras,untemplateSource)
        outFileName     = s"${UntemplateScalaPrefix}${untemplateScala.identifier}.scala"
        _               <- ZIO.attemptBlocking( Files.writeString(destDirPath.resolve(Path.of(outFileName)), untemplateScala.text.toString, scala.io.Codec.UTF8.charSet) )
      yield (outFileName)
    def generateForPackageSource(pkgSource : PackageSource) : ZIO[Any,Throwable,Unit] =
      val generations = pkgSource.untemplateSourceNames.map( sourceName => generateForUntemplateInPackage(sourceName, pkgSource) )
      val withFileNameList = ZIO.mergeAll(generations)( List.empty[String] )( (accum, next) => next :: accum )
      withFileNameList.map( flattenEnsureNoDups )
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

  // for outside clients.
  // We probably want to reorganize this stuff
  def unsafeDoIt( opts : Opts ) : Unit =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(doIt(opts)).getOrThrowFiberFailure()
    }


  def parseArgs( args : Array[String] ) : ZIO[Any,Throwable,Option[Opts]] = ZIO.attempt( OParser.parse(parser1, args, Opts()) )

  def run =
    for
      args   <- getArgs
      mbOpts <- parseArgs(args.toArray)
      _      <- doIt(mbOpts)
    yield ()




}
