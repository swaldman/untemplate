package untemplate

import zio.*
import java.nio.file.{Files, Path}

object Untemplate:

  private def loadPackageSources(source: Path) = PackageSource.fromBaseDirectoryRecursive(source)

  private def createPackageDirs(dest: Path, pkgSources: Set[PackageSource]): ZIO[Any, Throwable, Unit] =
    def createPackageDir(locationPackage: LocationPackage): Unit =
      val fullPath = dest.resolve(locationPackage.toPath)
      Files.createDirectories(fullPath)

    ZIO.attemptBlocking(pkgSources.map(_.locationPackage).foreach(createPackageDir))


  private def genScalaSources(dest: Path, pkgSources: Set[PackageSource], selectCustomizer : Customizer.Selector, flatten: Boolean): ZIO[Any, Throwable, Unit] =
    def flattenEnsureNoDups(outputFileNames: List[String]): Unit =
      if (flatten)
        val dups = outputFileNames.groupBy(identity).collect { case (x, ys) if ys.tail.nonEmpty => x }
        if (dups.nonEmpty)
          throw new NonuniqueIdentifier(
            "When flattening generation from a hierarchy of untemplate files, duplicate untemplate " +
              "identifiers and therefore filenames were generated, causing some templates to be overwritten. Duplicated file names: " +
              dups.mkString(", ")
          )

    def generateForUntemplateInPackage(untemplateSourceName: String, pkgSource: PackageSource): ZIO[Any, Throwable, Option[String]] =
      val destDirPath = if (flatten) dest else dest.resolve(pkgSource.locationPackage.toPath)
      val defaultFunctionIdentifier = untemplateSourceNameToIdentifier(untemplateSourceName)

      // Not great, but a name knowable prior to compilation for last-mod check
      val outFileName = s"${untemplateSourceName}.scala"
      val outFullPath = destDirPath.resolve(Path.of(outFileName))

      def doGenerateWrite: ZIO[Any, Throwable, Unit] =
        for
          untemplateSource <- pkgSource.untemplateSource(untemplateSourceName)
          untemplateScala = DefaultTranspiler(pkgSource.locationPackage, defaultFunctionIdentifier, selectCustomizer, untemplateSource)
          _ <- ZIO.attemptBlocking(Files.writeString(outFullPath, untemplateScala.text.toString, scala.io.Codec.UTF8.charSet))
        yield ()

      def conditionalGenerate(mbSourceLastMod: Option[Long], mbDestLastMod: Option[Long]) =
        if shouldUpdate(mbSourceLastMod, mbDestLastMod) then doGenerateWrite.map(_ => Some(outFileName)) else ZIO.none

      val out =
        for
          sourceMetadata <- pkgSource.untemplateSourceMetadata(untemplateSourceName)
          mbSourceLastMod = sourceMetadata.mbLastModMetaOption
          mbDestLastMod = if (Files.exists(outFullPath)) Some(Files.getLastModifiedTime(outFullPath).toMillis) else None
          mbOutFileName <- conditionalGenerate(mbSourceLastMod, mbDestLastMod)
        yield
          mbOutFileName
      out.tap(opt =>
        ZIO.logDebug(opt.fold(s"'${untemplateSourceName}' known to be unchanged, no scala generated.")(ofn => s"Regenerated '${ofn}' from '${untemplateSourceName}'"))
      )

    def generateForPackageSource(pkgSource: PackageSource): ZIO[Any, Throwable, Unit] =
      val generations = pkgSource.untemplateSourceNames.map(sourceName => generateForUntemplateInPackage(sourceName, pkgSource))
      val withFileNameList = ZIO.mergeAll(generations)(Nil: List[String])((accum, next) => next.toList ::: accum)
      withFileNameList.map(flattenEnsureNoDups)

    ZIO.mergeAll(pkgSources.map(generateForPackageSource))(())((_: Unit, _: Unit) => ())

  private def shouldUpdate(mbSourceLastMod: Option[Long], mbDestLastMod: Option[Long]): Boolean =
    (mbSourceLastMod, mbDestLastMod) match
      case (Some(sourceLastMod), Some(destLastMod)) => sourceLastMod > destLastMod
      case _ => true

  def transpileRecursive(source : Path, dest : Path, selectCustomizer : Customizer.Selector, flatten : Boolean): ZIO[Any, Throwable, Unit] =
    for
      pkgSources <- loadPackageSources(source)
      _ <- createPackageDirs(dest, pkgSources)
      _ <- genScalaSources(dest, pkgSources, selectCustomizer, flatten)
    yield ()

  def unsafeTranspileRecursive(source : Path, dest : Path, selectCustomizer : Customizer.Selector, flatten : Boolean) : Unit =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(transpileRecursive(source, dest, selectCustomizer, flatten)).getOrThrowFiberFailure()
    }

abstract class Untemplate[-A, +B] extends Function1[A,Result[B]]

