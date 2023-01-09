package untemplate

private val UnanchoredHeaderDelimeterRegexString = """\((.*?)\)\[(.*)\]\~+?\((.*?)\)\>"""
private val UnanchoredHeaderDelimeterRegex = UnanchoredHeaderDelimeterRegexString.r
private val AnchoredHeaderDelimeterRegex = ("""^"""+ UnanchoredHeaderDelimeterRegexString + """\s*$""").r

private val UnanchoredTextStartDelimeterRegexString = """\((.*?)\)[\-\>]*?\>"""
private val UnanchoredTextStartDelimeterRegex = UnanchoredTextStartDelimeterRegexString.r
private val AnchoredTextStartDelimiterRegex = ("""^""" + UnanchoredTextStartDelimeterRegexString + """\s*$""").r

private val UnanchoredTextEndDelimeterRegexString = """\<[\<\-]*?\(\)"""
private val UnanchoredTextEndDelimeterRegex = UnanchoredTextEndDelimeterRegexString.r
private val AnchoredTextEndDelimeterRegex = ("""^""" + UnanchoredTextEndDelimeterRegexString + """\s*$""").r

private val EmbeddedExpressionRegex = """(?<!\\)\<\((.+?)\)\>""".r

private val UnescapeHeaderDelimeterRegex = """\\(\(.*?\)\[.*?\]\~+?\(.*?\)\>)""".r
private val UnescapeTextStartDelimeterRegex = """\\(\(.*?\)[\-\>]*?\>)""".r
private val UnescapeTextEndDelimeterRegex = """\\(\<[\<\-]*?\(\))""".r
private val UnescapeEmbeddedExpressionRegex = """\\(\<\(.+?\)\>)""".r

private val UnescapeRegexes =
  UnescapeHeaderDelimeterRegex :: UnescapeTextStartDelimeterRegex :: UnescapeTextEndDelimeterRegex :: UnescapeEmbeddedExpressionRegex :: Nil

private val PackageExtractRegex = """^\s*package\s+(\S+)\s*$""".r


