package untemplate

import scala.collection.*

opaque type GeneratorSource = Vector[String]
opaque type GeneratorScala  = String
opaque type Transpiler      = Function1[GeneratorSource,GeneratorScala]

type Generator[-A] = Function1[A,String]

type BlockPrinter[-A] = Function2[A,mutable.Map[String,Any],String]

val Suffix = "untemplate"

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

private val DotSuffix    = "." + Suffix
private val DotSuffixLen = DotSuffix.length

private def asGeneratorSource(vs : Vector[String]) : GeneratorSource = vs
//private def lines(ts : GeneratorSource) : Vector[String] = ts

extension (ts : GeneratorSource )
  def lines : Vector[String] = ts


