package untemplate

opaque type TransformerSource = Vector[String]
opaque type TransformerScala  = String
opaque type Transpiler        = Function1[TransformerSource,TransformerScala]
opaque type Transformer[A]    = Function2[A,String,String]

val Suffix = "untemplate"

val HeaderDelimeter    = "~[]~()>"

val TextStartDelimeter = "()>"
val TextEndDelimeter   = "<()"

val WeakTestStartDelimeterRegexString = """\((.*?)\)\>"""
val WeakTestStartDelimeterRegex = WeakTestStartDelimeterRegexString.r
val TextStartDelimiterRegex = """^""" + WeakTestStartDelimeterRegexString + """\s*$""".r

val WeakHeaderDelimeterRegexString = """\~\[(.*?)\]\~\((.*?)\)\>"""
val WeakHeaderDelimeterRegex = WeakHeaderDelimeterRegexString.r
val HeaderDelimeterRegex = """^"""+ WeakHeaderDelimeterRegexString + """\s*$"""

private val DotSuffix    = "." + Suffix
private val DotSuffixLen = DotSuffix.length

private def asTransformerSource(vs : Vector[String]) : TransformerSource = vs
//private def lines(ts : TransformerSource) : Vector[String] = ts

extension (ts : TransformerSource )
  def lines : Vector[String] = ts


