package untemplate

opaque type TransformerSource = Vector[String]
opaque type TransformerScala  = String
opaque type Transpiler        = Function1[TransformerSource,TransformerScala]
opaque type Transformer[A]    = Function2[A,String,String]

val Suffix = "untemplate"

val HeaderDelimeter    = "~[]~()>"

val TextStartDelimeter = "()>"
val TextEndDelimeter   = "<()"

private val WeakTextStartDelimeterRegexString = """\((.*?)\)\>"""
private val WeakTextStartDelimeterRegex = WeakTextStartDelimeterRegexString.r
private val TextStartDelimiterRegex = ("""^""" + WeakTextStartDelimeterRegexString + """\s*$""").r

private val WeakTextEndDelimeterRegexString = """\<\(\)"""
private val WeakTextEndDelimeterRegex = WeakTextEndDelimeterRegexString.r
private val TextEndDelimeterRegex = ("""^""" + WeakTextEndDelimeterRegexString + """\s*$""").r

private val WeakHeaderDelimeterRegexString = """\~\[(.*?)\]\~\((.*?)\)\>"""
private val WeakHeaderDelimeterRegex = WeakHeaderDelimeterRegexString.r
private val HeaderDelimeterRegex = ("""^"""+ WeakHeaderDelimeterRegexString + """\s*$""").r

private val DotSuffix    = "." + Suffix
private val DotSuffixLen = DotSuffix.length

private def asTransformerSource(vs : Vector[String]) : TransformerSource = vs
//private def lines(ts : TransformerSource) : Vector[String] = ts

extension (ts : TransformerSource )
  def lines : Vector[String] = ts


