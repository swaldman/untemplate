package untemplate

import scala.collection.*
import scala.util.matching.Regex.Match

private val Suffix = "untemplate"
private val DotSuffix = "." + Suffix
private val DotSuffixLen = DotSuffix.length

def generatorSourceNameToIdentifier( sourceName : String ) : Identifier =
  val noSuffix =
    if sourceName.endsWith(DotSuffix) then
      sourceName.substring(0, sourceName.length - DotSuffixLen)
    else
      sourceName
  toIdentifier(noSuffix)

opaque type Identifier = String

def toIdentifier( unrestrictedName : String ) : Identifier =
  val transformed =
    unrestrictedName.map {
      case '.' | '-'                              => '_'
      case c if Character.isJavaIdentifierPart(c) => c
      case c                                      =>
        throw new BadIdentifierSource(s"Cannot convert identifier '${unrestrictedName} to identifier, contains bad character '${c}'.")
    }
  val c = transformed(0)
  if !Character.isJavaIdentifierStart(c) then
    throw new BadIdentifierSource(s"Bad initial character for identifier: '${c}' in '${transformed}'.'")
  else
    transformed

opaque type GeneratorSource = Vector[String]
opaque type GeneratorScala  = String

private def toGeneratorScala( text : String ) : GeneratorScala = text

object GeneratorExtras:
  val empty = new GeneratorExtras(None, None, Vector.empty)
case class GeneratorExtras( mbDefaultInputName : Option[Identifier], mbDefaultInputType : Option[String], extraImports : Vector[String])

type Transpiler       = Function4[List[Identifier], Identifier, GeneratorExtras, GeneratorSource, Tuple2[Identifier,GeneratorScala]]
type Generator[-A]    = Function1[A,String]
type BlockPrinter[-A] = Function1[A,String]

val DefaultTranspiler : Transpiler = defaultTranspile

// these are just examples
// val HeaderDelimeter    = "()[]~()>"

// val TextStartDelimeter = "()>"
// val TextEndDelimeter   = "<()"

// val EmbeddedExpressionDelimiter = "<()>"

private def asGeneratorSource(vs : Vector[String]) : GeneratorSource = vs

extension (ts : GeneratorSource )
  def lines : Vector[String] = ts


