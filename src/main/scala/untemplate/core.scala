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

val UnitIdentifer = toIdentifier("Unit")

opaque type GeneratorSource = Vector[String]
opaque type GeneratorScala  = String


private def toGeneratorScala( text : String ) : GeneratorScala = text

type Transpiler       = Function3[List[Identifier], Identifier, GeneratorSource, GeneratorScala]
type Generator[-A]    = Function1[A,String]
type BlockPrinter[-A] = Function2[A,Scratchpad,String]

val DefaultTranspiler : Transpiler = defaultTranspile

// these are just examples
// val HeaderDelimeter    = "~[]~()>"

// val TextStartDelimeter = "()>"
// val TextEndDelimeter   = "<()"

// val EmbeddedExpressionDelimiter = "<()>"

private val UnanchoredTextStartDelimeterRegexString = """\((.*?)\)\>"""
private val UnanchoredTextStartDelimeterRegex = UnanchoredTextStartDelimeterRegexString.r
private val AnchoredTextStartDelimiterRegex = ("""^""" + UnanchoredTextStartDelimeterRegexString + """\s*$""").r

private val UnanchoredTextEndDelimeterRegexString = """\<\(\)"""
private val UnanchoredTextEndDelimeterRegex = UnanchoredTextEndDelimeterRegexString.r
private val AnchoredTextEndDelimeterRegex = ("""^""" + UnanchoredTextEndDelimeterRegexString + """\s*$""").r

private val UnanchoredHeaderDelimeterRegexString = """\~\[(.*?)\]\~\((.*?)\)\>"""
private val UnanchoredHeaderDelimeterRegex = UnanchoredHeaderDelimeterRegexString.r
private val AnchoredHeaderDelimeterRegex = ("""^"""+ UnanchoredHeaderDelimeterRegexString + """\s*$""").r

private val EmbeddedExpressionRegex = """\<\((.+?)\)\>""".r

private val IndentIncreasePointRegex ="""(?:^|([\r\n]+))""".r
private val IndentDecreaseRegex ="""(?:^( *)|([\r\n]+ *))""".r


private def nullToBlank(s : String) = if s == null then "" else s

private def increaseIndent( spaces : Int )( block : String ) =
  if (spaces > 0)
    val indent = " " * spaces
    IndentIncreasePointRegex.replaceAllIn(block, m => nullToBlank(m.group(1)) + indent)
  else
    block

private def notNullOrElse[T]( target : T, replacement : T) =
  if target == null then target else replacement

private def decreaseIndent( spaces : Int )( block : String ) =
  def replace( m : Match ) =
    val matched = notNullOrElse( m.group(1), notNullOrElse( m.group(2), "" ) )
    val endspaces = matched.dropWhile(c => c == '\r' || c == '\n').length
    val truncate = math.min(endspaces,spaces)
    matched.substring(0, matched.length-truncate)

  if (spaces > 0)
    IndentDecreaseRegex.replaceAllIn(block, m => replace(m))
  else
    block

private val ii = increaseIndent
private val di = decreaseIndent

private def asGeneratorSource(vs : Vector[String]) : GeneratorSource = vs
//private def lines(ts : GeneratorSource) : Vector[String] = ts

extension (ts : GeneratorSource )
  def lines : Vector[String] = ts


