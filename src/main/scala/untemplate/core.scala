package untemplate

import scala.collection.*
import scala.util.matching.Regex.Match
import com.mchange.codegenutil

private val Suffix = "untemplate"
private val DotSuffix = "." + Suffix
private val DotSuffixLen = DotSuffix.length

final case class Result[+A](mbMetadata : Option[A], text : String ):
  override def toString() : String = text

opaque type UntemplateWarning = String

def toUntemplateWarning( s : String ) : UntemplateWarning = s

type Transpiler           = Function5[LocationPackage, Identifier, Customizer.Selector, UntemplateSource, Option[String], UntemplateScala]
type BlockPrinter         = Function0[String]
type OutputTransformer[A] = Function1[Result[A],Result[A]]

val DefaultTranspiler : Transpiler = defaultTranspile

// these are just examples
// val HeaderDelimiter    = "()[]~()>"

// val TextStartDelimiter = "()>"
// val TextEndDelimiter   = "<()"

// val EmbeddedExpressionDelimiter = "<()>"

object UntemplateSource:
  final case class Metadata( mbLastModMetaOption : Option[Long] )
final case class UntemplateSource( provenance : String, lines : Vector[String] ):
  lazy val textLen : Int = lines.foldLeft(0)( (accum, next) => accum + next.length ) + lines.length * codegenutil.LineSep.length

