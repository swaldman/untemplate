package untemplate

import scala.jdk.CollectionConverters.*

import zio.*
import java.nio.file.{Files, Path}

object LocationPackage:
  private val EmptyStringList = "" :: Nil

  val empty = Nil

  def fromLocation(path : Path, baseDirPath : Option[Path] = None) : Task[LocationPackage] =
    ZIO.attemptBlocking {
      val pathIsDir = Files.isDirectory(path)
      val dirPath = if pathIsDir then path else path.getParent
      val rel = baseDirPath.map(_.relativize(dirPath)).getOrElse(dirPath.getFileName)
      val pieces = rel.iterator().asScala.map(_.toString).toList
      if pieces == EmptyStringList then Nil // no pkg dir shows up as an empty string, we want an empty list
      else pieces.map(piece => asIdentifier(piece))
    }

  extension (lp: LocationPackage)
    def toList: List[Identifier] = lp
    //def toString() : String = lp.mkString(".") // I'd rather just use toString() for "dotty", but the underlying list's toString is called regardless.
    def dotty: String = lp.mkString(".")
    def nonDefault: Boolean = lp.nonEmpty
    def toPath: Path = if (nonDefault) Path.of(lp.head.toString, lp.tail.map(_.toString): _*) else Path.of(".")

opaque type LocationPackage = List[Identifier]

