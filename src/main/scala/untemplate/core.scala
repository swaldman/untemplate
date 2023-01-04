package untemplate

import scala.collection.*
import scala.util.matching.Regex.Match
import com.mchange.codegenutil

private val Suffix = "untemplate"
private val DotSuffix = "." + Suffix
private val DotSuffixLen = DotSuffix.length

case class Result[+A](mbMetadata : Option[A], text : String )

opaque type GeneratorSource  = Vector[String]
opaque type GeneratorWarning = String

object GeneratorExtras:
  val empty = new GeneratorExtras(None, None, Vector.empty)
case class GeneratorExtras( mbDefaultInputName : Option[Identifier], mbDefaultInputType : Option[String], extraImports : Vector[String])

case class GeneratorScala( identifier : Identifier, warning : Vector[GeneratorWarning], text : String )

type Transpiler           = Function4[List[Identifier], Identifier, GeneratorExtras, GeneratorSource, GeneratorScala]
type Generator[-A, +B]    = Function1[A,Result[B]]
type BlockPrinter         = Function0[String]
type OutputTransformer[A] = Function1[Result[A],Result[A]]

val DefaultTranspiler : Transpiler = defaultTranspile

// these are just examples
// val HeaderDelimeter    = "()[]~()>"

// val TextStartDelimeter = "()>"
// val TextEndDelimeter   = "<()"

// val EmbeddedExpressionDelimiter = "<()>"

case class GeneratorSourceMetadata( mbLastModMetaOption : Option[Long] )

private def asGeneratorSource(vs : Vector[String]) : GeneratorSource = vs

extension (ts : GeneratorSource )
  def lines : Vector[String] = ts
  def textLen : Int = lines.foldLeft(0)( (accum, next) => accum + next.length ) + lines.length * codegenutil.LineSep.length


