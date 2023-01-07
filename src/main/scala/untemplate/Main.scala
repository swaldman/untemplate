package untemplate

import java.nio.file.{Files, Path}
import scopt.OParser
import zio.*

private val UntemplateScalaPrefix = "untemplate_"

object Main extends zio.ZIOAppDefault:
  final case class Opts (
    source      : Path       = Path.of("."),
    dest        : Path       = null,
    customizer  : Customizer = Customizer.empty,
    flatten     : Boolean    = false
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
        .action( (str, opts) => opts.copy(customizer = opts.customizer.copy(mbDefaultInputName = Some(str))) )
        .valueName("<name>")
        .text("name for input identifier, if not specified within the template"),
      opt[String]("default-input-type")
        .action( (str, opts) => opts.copy(customizer = opts.customizer.copy(mbDefaultInputTypeDefaultArg = Some(Customizer.InputTypeDefaultArg(str,None)))) )
        .valueName("<type-name>")
        .text("name for input type, fully-qualified or resolvable via imports supplied here or built into the template"),
      opt[Seq[String]]("extra-imports")
        .action( (extras, opts) => opts.copy(customizer = opts.customizer.copy(extraImports = extras.toVector)) )
        .valueName("<import1>,<import2>,<import3>,...")
        .text("extra imports that should be included by default at the top level of templates"),
      opt[Unit]("flatten")
        .action( (_, opts) => opts.copy(flatten = true) )
        .text("places all outputs directly in dest, rather than reproducing any directory hierarchy in source")
    )
  }

  def doIt( opts : Opts ) : ZIO[Any,Throwable,Unit] =
    Untemplate.transpileRecursive(opts.source, opts.dest, _ => opts.customizer, opts.flatten)

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
