package untemplate

private val WhitespaceOrCommentRegexString = """\s*(?:\#.*)?"""
private val UnanchoredHeaderDelimeterRegexString = """\((.*?)\)\[(.*)\]\~+?\((.*?)\)\>"""
private val UnanchoredHeaderDelimeterRegex = UnanchoredHeaderDelimeterRegexString.r
private val AnchoredHeaderDelimeterRegex = ("""^"""+ UnanchoredHeaderDelimeterRegexString + WhitespaceOrCommentRegexString + """$""").r

private val UnanchoredTextStartDelimeterRegexString = """\((.*?)\)[\-\>]*?\>"""
private val UnanchoredTextStartDelimeterRegex = UnanchoredTextStartDelimeterRegexString.r
private val AnchoredTextStartDelimiterRegex = ("""^""" + UnanchoredTextStartDelimeterRegexString + WhitespaceOrCommentRegexString + """$""").r

private val UnanchoredTextEndDelimeterRegexString = """\<[\<\-]*?\(\)"""
private val UnanchoredTextEndDelimeterRegex = UnanchoredTextEndDelimeterRegexString.r
private val AnchoredTextEndDelimeterRegex = ("""^""" + UnanchoredTextEndDelimeterRegexString + WhitespaceOrCommentRegexString + """$""").r

private val EmbeddedExpressionRegex = """(?<!\\)\<\((.+?)\)\>""".r

private val UnescapeHeaderDelimeterRegex = """\\(\(.*?\)\[.*?\]\~+?\(.*?\)\>)""".r
private val UnescapeTextStartDelimeterRegex = """\\(\(.*?\)[\-\>]*?\>)""".r
private val UnescapeTextEndDelimeterRegex = """\\(\<[\<\-]*?\(\))""".r
private val UnescapeEmbeddedExpressionRegex = """\\(\<\(.+?\)\>)""".r

private val UnescapeRegexes =
  UnescapeHeaderDelimeterRegex :: UnescapeTextStartDelimeterRegex :: UnescapeTextEndDelimeterRegex :: UnescapeEmbeddedExpressionRegex :: Nil

private val PackageExtractFromLineRegex = """^\s*package\s+(\S+)[\;\s]*(?://.*)?$""".r

private val UntemplateIdentifierExtractFromLineRegex = """^\s*val\s+Untemplate_(\w+)\s+\=untemplate\.Untemplate\[.+$""".r