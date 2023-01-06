package untemplate

import scala.collection.*
import scala.util.matching.Regex.Match
import com.mchange.codegenutil

private val Suffix = "untemplate"
private val DotSuffix = "." + Suffix
private val DotSuffixLen = DotSuffix.length

case class Result[+A](mbMetadata : Option[A], text : String )

opaque type UntemplateSource  = Vector[String]
opaque type UntemplateWarning = String

object UntemplateExtras:
  val empty = new UntemplateExtras(None, None, Vector.empty)
case class UntemplateExtras( mbDefaultInputName : Option[Identifier], mbDefaultInputType : Option[String], extraImports : Vector[String])

case class UntemplateScala( identifier : Identifier, warning : Vector[UntemplateWarning], text : String )

type Transpiler           = Function4[LocationPackage, Identifier, UntemplateExtras, UntemplateSource, UntemplateScala]
type Untemplate[-A, +B]   = Function1[A,Result[B]]
type BlockPrinter         = Function0[String]
type OutputTransformer[A] = Function1[Result[A],Result[A]]

val DefaultTranspiler : Transpiler = defaultTranspile

// these are just examples
// val HeaderDelimeter    = "()[]~()>"

// val TextStartDelimeter = "()>"
// val TextEndDelimeter   = "<()"

// val EmbeddedExpressionDelimiter = "<()>"

case class UntemplateSourceMetadata( mbLastModMetaOption : Option[Long] )

private def asUntemplateSource(vs : Vector[String]) : UntemplateSource = vs

extension (ts : UntemplateSource )
  def lines : Vector[String] = ts
  def textLen : Int = lines.foldLeft(0)( (accum, next) => accum + next.length ) + lines.length * codegenutil.LineSep.length


