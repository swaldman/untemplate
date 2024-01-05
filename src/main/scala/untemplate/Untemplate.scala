package untemplate

import zio.*
import java.nio.file.{Files, Path}
import scala.collection.{immutable,mutable}
import com.mchange.codegenutil.LineSep


object Untemplate:

  // mbFullyQualifiedFunctionNameForIndexing doesn't get filled in if the file is newer than its source,
  // doesn't need regeneration
  final case class GenerationRecord(filePath : Path, fileName : String, mbFullyQualifiedFunctionNameForIndexing : Option[String]):
    def fillInFromScalaFile : ZIO[Any,Throwable,GenerationRecord] =
      ZIO.attempt {
        mbFullyQualifiedFunctionNameForIndexing match
          case Some(_) => this
          case None =>
            val contents = Files.readString(filePath, scala.io.Codec.UTF8.charSet)
            val untemplateScala = UntemplateScala.fromScalaText(contents)
            this.copy(mbFullyQualifiedFunctionNameForIndexing = Some(untemplateScala.fullyQualifiedFunctionName))
      }

  private def loadPackageSources(source: Path) = PackageSource.fromBaseDirectoryRecursive(source)

  private def fullyQualifiedFunctionToUntemplate(fqf : String) : String =
    val lastDot = fqf.lastIndexOf('.')                                    // -1 if default package
    fqf.substring(0,lastDot+1) + "Untemplate_" + fqf.substring(lastDot+1) // works for -1 too!

  private def createIndex( dest : Path, fullyQualifiedIndexName : String, untemplateNames : List[String], flatten : Boolean) : ZIO[Any, Throwable, Unit] =
    ZIO.attempt {
      val (pathElements, indexName) =
        val split = fullyQualifiedIndexName.split('.')
        split.foreach(asIdentifier) // fail if any of the components of the index name given is not a valid identifier
        if (split.size > 1)
          (split.init.toList, split.last)
        else
          (Nil, split)

      val dir = if flatten || pathElements.isEmpty then dest else dest.resolve(Path.of(pathElements.head, pathElements.tail : _*))

      Files.createDirectories(dir)

      val filename = s"UntemplateIndex_${indexName}.scala"

      val filepath = dir.resolve(filename)

      val fileContents =
        import com.mchange.codegenutil.*
        val w = new java.io.StringWriter(untemplateNames.size * 100) //XXX: multiple is hard-coded
        w.writeln( autogeneratedComment(None) )
        w.writeln(s"""package ${pathElements.mkString(".")}""")
        w.writeln()
        w.writeln(s"""val ${indexName} = scala.collection.immutable.SortedMap[String,untemplate.Untemplate[Nothing,Any]](""")
        untemplateNames.foreach { name =>
          w.indentln(1)(s""""${name}" -> ${fullyQualifiedFunctionToUntemplate(name)},""")
        }
        w.writeln(""")""")
        w.toString

      Files.writeString(filepath, fileContents, scala.io.Codec.UTF8.charSet)
    }

  private def logCantFill( oopsie : Throwable ) : ZIO[Any,Throwable,Unit] =
    ZIO.logWarningCause("Could not read fully qualified function name from nonregenerated file during indexing.", Cause.fail(oopsie))

  private def conditionallyIndex( dest : Path, generationRecords : Iterable[GenerationRecord], fullyQualifiedIndexName : Option[String], flatten : Boolean ) : ZIO[Any,Throwable,Unit] =
    fullyQualifiedIndexName match
      case Some( fullyQualifiedName ) =>
        for
          tuple                 <- ZIO.partition( generationRecords )( _.fillInFromScalaFile )
          (failures, successes) = tuple // workaround compiler issue, https://github.com/zio/zio/issues/6468
          _                     <- ZIO.foreachDiscard( failures )( logCantFill )
          _                     <- createIndex( dest, fullyQualifiedName, successes.map( _.mbFullyQualifiedFunctionNameForIndexing.get ).toList, flatten )
        yield ()
      case None => ZIO.unit

  private def createPackageDirs(dest: Path, pkgSources: Set[PackageSource]): ZIO[Any, Throwable, Unit] =
    def createPackageDir(locationPackage: LocationPackage): Unit =
      val fullPath = dest.resolve(locationPackage.toPath)
      Files.createDirectories(fullPath)

    ZIO.attemptBlocking(pkgSources.map(_.locationPackage).foreach(createPackageDir))

  private def _flattenEnsureNoDups(dest: Path, flatten: Boolean)(outputFileNames: Iterable[String]): Unit =
    if (flatten)
      val dups = outputFileNames.groupBy(identity).collect { case (x, ys) if ys.tail.nonEmpty => x }
      if (dups.nonEmpty)
        throw new NonuniqueIdentifier(
          "When flattening generation from a hierarchy of untemplate and side-scala files, duplicate " +
            "filenames were generated, which would have caused some source files to be overwritten. Duplicated file names: " +
            dups.mkString(", ")
        )

  // XXX: Not fully DRY! Added support for SideScala by just mimicking in parallel untemplate generation logic.
  //      Perhaps the logic should be abstracted out in common
  private def genSideScala( dest: Path, pkgSources: Set[PackageSource], flatten: Boolean ) : ZIO[Any, Throwable, Unit] =
    def flattenEnsureNoDups(outputFileNames: Iterable[String]): Unit = _flattenEnsureNoDups(dest,flatten)(outputFileNames)

    def generateSideScalaForPackage(sideScalaSourceName: String, pkgSource: PackageSource): ZIO[Any, Throwable, String] = // return the outfile path as string
      val destDirPath = if (flatten) dest else dest.resolve(pkgSource.locationPackage.toPath)

      def transformSideScalaSource( sss : SideScalaSource ) : Vector[String] =
        val packageHeaderLine = s"package ${pkgSource.locationPackage.dotty}"
        Vector(autogeneratedComment(Some(sss.provenance)), packageHeaderLine, "") ++ sss.lines

      val outFullPath = destDirPath.resolve(Path.of(sideScalaSourceName))

      def doGenerateWrite: ZIO[Any, Throwable, Unit] =
        for
          sideScalaSource <- pkgSource.sideScalaSource(sideScalaSourceName)
          lines = transformSideScalaSource( sideScalaSource )
          text = lines.mkString("",LineSep,LineSep)
          _ <- ZIO.attemptBlocking(Files.writeString(outFullPath, text, pkgSource.codec.charSet))
        yield ()

      def conditionalGenerate(mbSourceLastMod: Option[Long], mbDestLastMod: Option[Long]) =
        if shouldUpdate(mbSourceLastMod, mbDestLastMod) then doGenerateWrite else ZIO.unit

      val almostOut =
        for
          sourceMetadata <- pkgSource.sideScalaSourceMetadata(sideScalaSourceName)
          mbSourceLastMod = sourceMetadata.mbLastModMetaOption
          mbDestLastMod = if (Files.exists(outFullPath)) Some(Files.getLastModifiedTime(outFullPath).toMillis) else None
          _ <- conditionalGenerate(mbSourceLastMod, mbDestLastMod)
        yield ()
        
      almostOut.map( _ => outFullPath.normalize().toString() )

    def generateForPackageSource(pkgSource: PackageSource): ZIO[Any, Throwable, Iterable[String]] =
      val generations = pkgSource.sideScalaSourceNames.map(sourceName => generateSideScalaForPackage(sourceName, pkgSource))
      ZIO.collectAllPar(generations)

    ZIO.collectAllPar(pkgSources.map(generateForPackageSource)).map( _.flatten ).map(flattenEnsureNoDups)
  end genSideScala

  // XXX: Not fully DRY! Added support for SideScala by just mimicking in parallel untemplate generation logic.
  //      Perhaps the logic should be abstracted out in common
  private def genUntemplateScalaSources(dest: Path, pkgSources: Set[PackageSource], selectCustomizer : Customizer.Selector, flatten: Boolean): ZIO[Any, Throwable, Iterable[GenerationRecord]] =
    def flattenEnsureNoDups(outputFileNames: Iterable[String]): Unit = _flattenEnsureNoDups(dest,flatten)(outputFileNames)

    def generateForUntemplateInPackage(untemplateSourceName: String, pkgSource: PackageSource): ZIO[Any, Throwable, GenerationRecord] =
      val destDirPath = if (flatten) dest else dest.resolve(pkgSource.locationPackage.toPath)
      val defaultFunctionIdentifier = untemplateSourceNameToIdentifier(untemplateSourceName)

      // Not great, but a name knowable prior to compilation for last-mod check
      val outFileName =
        def dedottify( c : Char ) = if c == '.' then '-' else c
        val dedottified = untemplateSourceName.map(dedottify)
        s"${dedottified}.scala"

      val ensureNoSideScalaCollision = ZIO.attempt {
        if pkgSource.sideScalaSourceNames.contains(outFileName) then
          throw new NonuniqueIdentifier(
            s"Generated untemplate scala from '${untemplateSourceName}' would overwrite side scala source file '${outFileName}'. Please rename that side scala source file."
          )
      }

      val outFullPath = destDirPath.resolve(Path.of(outFileName))

      def doGenerateWrite: ZIO[Any, Throwable, GenerationRecord] =
        for
          untemplateSource <- pkgSource.untemplateSource(untemplateSourceName)
          untemplateScala = DefaultTranspiler(pkgSource.locationPackage, defaultFunctionIdentifier, selectCustomizer, untemplateSource, Some(untemplateSourceName))
          _ <- ZIO.collectAllDiscard( untemplateScala.warnings.map( warning => ZIO.logWarning( s"${untemplateSourceName}: ${warning.toString}" ) ) )
          _ <- ZIO.attemptBlocking(Files.writeString(outFullPath, untemplateScala.text, pkgSource.codec.charSet))
        yield (GenerationRecord(outFullPath,outFileName,Some(untemplateScala.fullyQualifiedFunctionName)))

      def conditionalGenerate(mbSourceLastMod: Option[Long], mbDestLastMod: Option[Long]) =
        if shouldUpdate(mbSourceLastMod, mbDestLastMod) then doGenerateWrite else ZIO.succeed(GenerationRecord(outFullPath,outFileName,None))

      val out =
        for
          _ <- ensureNoSideScalaCollision
          sourceMetadata <- pkgSource.untemplateSourceMetadata(untemplateSourceName)
          mbSourceLastMod = sourceMetadata.mbLastModMetaOption
          mbDestLastMod = if (Files.exists(outFullPath)) Some(Files.getLastModifiedTime(outFullPath).toMillis) else None
          genRec <- conditionalGenerate(mbSourceLastMod, mbDestLastMod)
        yield
          genRec
      out.tap ( genRec =>
        val logMessage =
          genRec.mbFullyQualifiedFunctionNameForIndexing match
            case Some(_) => s"Regenerated '${outFileName}' from '${untemplateSourceName}'"
            case None => s"'${untemplateSourceName}' known to be unchanged, no scala generated."
        ZIO.logDebug(logMessage)
      )

    def generateForPackageSource(pkgSource: PackageSource): ZIO[Any, Throwable, Seq[GenerationRecord]] =
      val generations = pkgSource.untemplateSourceNames.map(sourceName => generateForUntemplateInPackage(sourceName, pkgSource))
      ZIO.collectAllPar(generations)
      // withFileNameList.map(flattenEnsureNoDups) // wrong place for this, must be at PkgSources level!

    val allGenerations = ZIO.collectAllPar(pkgSources.map(generateForPackageSource)).map( _.flatten )
    allGenerations.map( genRecList => genRecList.map( _.fileName) ).map(flattenEnsureNoDups)
    allGenerations
  end genUntemplateScalaSources

  private def shouldUpdate(mbSourceLastMod: Option[Long], mbDestLastMod: Option[Long]): Boolean =
    (mbSourceLastMod, mbDestLastMod) match
      case (Some(sourceLastMod), Some(destLastMod)) => sourceLastMod > destLastMod
      case _ => true

  def transpileRecursive(source : Path, dest : Path, selectCustomizer : Customizer.Selector, fullyQualifiedIndexName : Option[String], flatten : Boolean): ZIO[Any, Throwable, Unit] =
    for
      pkgSources <- loadPackageSources(source)
      _ <- createPackageDirs(dest, pkgSources)
      _ <- genSideScala( dest, pkgSources, flatten )
      generationRecords <- genUntemplateScalaSources(dest, pkgSources, selectCustomizer, flatten)
      _ <- conditionallyIndex( dest, generationRecords, fullyQualifiedIndexName, flatten )
    yield ()

  def unsafeTranspileRecursive(source : Path, dest : Path, selectCustomizer : Customizer.Selector, fullyQualifiedIndexName : Option[String], flatten : Boolean) : Unit =
    Unsafe.unsafely {
      Runtime.default.unsafe.run(transpileRecursive(source, dest, selectCustomizer, fullyQualifiedIndexName, flatten)).getOrThrow()
    }

  type AnyUntemplate = Untemplate[Nothing,Any]

abstract class Untemplate[-A, +B] extends Function1[A,Result[B]]:
  def UntemplateFunction                    : untemplate.Untemplate[A,B]
  def UntemplateName                        : String
  def UntemplatePackage                     : String
  def UntemplateInputName                   : String
  def UntemplateInputTypeDeclared           : String
  def UntemplateInputTypeCanonical          : Option[String]
  def UntemplateInputDefaultArgument        : Option[_ >: A]
  def UntemplateOutputMetadataTypeDeclared  : String
  def UntemplateOutputMetadataTypeCanonical : Option[String]
  def UntemplateHeaderNote                  : String
  def UntemplateAttributes                  : immutable.Map[String,Any]
  def UntemplateFullyQualifiedName          : String = s"${UntemplatePackage}.${UntemplateName}"

  lazy val UntemplateAttributesLowerCased : LowerCased.Map = LowerCased.attributesFrom(this)

  override def toString() : String = s"untemplate.Untemplate[${UntemplateInputTypeDeclared},${UntemplateOutputMetadataTypeDeclared}]@${UntemplateFullyQualifiedName}"


