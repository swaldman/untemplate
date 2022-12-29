package untemplate

import scala.collection.*
import scala.util.matching.Regex.Match

class UntemplateException(msg : String, cause : Throwable = null) extends Exception(msg, cause)
class NonuniqueIdentifier(msg : String, cause : Throwable = null) extends UntemplateException(msg, cause)
class BadIdentifierSource(msg : String, cause : Throwable = null) extends UntemplateException(msg, cause)
class ParseException(msg : String, cause : Throwable = null) extends UntemplateException(msg, cause)

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
case class GeneratorExtras( mbDefaultInputName : Option[Identifier], mbDefaultInputType : Option[Identifier], extraImports : Vector[String])

type Transpiler       = Function4[List[Identifier], Identifier, GeneratorExtras, GeneratorSource, GeneratorScala]
type Generator[-A]    = Function1[A,String]
type BlockPrinter[-A] = Function2[A,mutable.Map[String,Any],String]

val DefaultTranspiler : Transpiler = defaultTranspile

// these are just examples
// val HeaderDelimeter    = "()[]~()>"

// val TextStartDelimeter = "()>"
// val TextEndDelimeter   = "<()"

// val EmbeddedExpressionDelimiter = "<()>"

private val UnanchoredTextStartDelimeterRegexString = """\((.*?)\)\>"""
private val UnanchoredTextStartDelimeterRegex = UnanchoredTextStartDelimeterRegexString.r
private val AnchoredTextStartDelimiterRegex = ("""^""" + UnanchoredTextStartDelimeterRegexString + """\s*$""").r

private val UnanchoredTextEndDelimeterRegexString = """\<\(\)"""
private val UnanchoredTextEndDelimeterRegex = UnanchoredTextEndDelimeterRegexString.r
private val AnchoredTextEndDelimeterRegex = ("""^""" + UnanchoredTextEndDelimeterRegexString + """\s*$""").r

private val UnanchoredHeaderDelimeterRegexString = """\((.*?)\)\[(.*?)\]\~\((.*?)\)\>"""
private val UnanchoredHeaderDelimeterRegex = UnanchoredHeaderDelimeterRegexString.r
private val AnchoredHeaderDelimeterRegex = ("""^"""+ UnanchoredHeaderDelimeterRegexString + """\s*$""").r

private val EmbeddedExpressionRegex = """\<\((.+?)\)\>""".r

private def asGeneratorSource(vs : Vector[String]) : GeneratorSource = vs

extension (ts : GeneratorSource )
  def lines : Vector[String] = ts


