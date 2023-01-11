package untemplate

private val EndAnchoredDelimiterRegexString = """([^\#]*)(?:\#.*)?$"""

private val UnanchoredHeaderDelimeterRegexString = """\((.*?)\)\[(.*)\]\~+\((.*?)\)\>"""
private val UnanchoredHeaderDelimeterRegex = ("""(?<!\\)""" + UnanchoredHeaderDelimeterRegexString).r
private val AnchoredHeaderDelimeterRegex = ("""^"""+ UnanchoredHeaderDelimeterRegexString + EndAnchoredDelimiterRegexString).r

private val UnanchoredTextStartDelimeterRegexString = """\((\s*\w*\s*)\)[\-\>]*\>"""
private val UnanchoredTextStartDelimeterRegex = ("""(?<!\\)""" + UnanchoredTextStartDelimeterRegexString).r
private val AnchoredTextStartDelimiterRegex = ("""^""" + UnanchoredTextStartDelimeterRegexString + EndAnchoredDelimiterRegexString).r

private val UnanchoredTextEndDelimeterRegexString = """\<[\<\-]*\(\)"""
private val UnanchoredTextEndDelimeterRegex = ("""(?<!\\)""" + UnanchoredTextEndDelimeterRegexString).r
private val AnchoredTextEndDelimeterRegex = ("""^""" + UnanchoredTextEndDelimeterRegexString + EndAnchoredDelimiterRegexString).r

private val EmbeddedExpressionRegex = """(?<!\\)\<\((.+?)\)\>""".r

private val UnescapeHeaderDelimeterRegex = """\\(\(.*?\)\[.*?\]\~+\(.*?\)\>)""".r
private val UnescapeTextStartDelimeterRegex = """\\(\(\s*\w*\s*\)[\-\>]*\>)""".r
private val UnescapeTextEndDelimeterRegex = """\\(\<[\<\-]*\(\))""".r
private val UnescapeEmbeddedExpressionRegex = """\\(\<\(.+?\)\>)""".r

private val UnescapeRegexes =
  UnescapeHeaderDelimeterRegex :: UnescapeTextStartDelimeterRegex :: UnescapeTextEndDelimeterRegex :: UnescapeEmbeddedExpressionRegex :: Nil

private val PackageExtractFromLineRegex = """^\s*package\s+(\S+)[\;\s]*(?://.*)?$""".r

private val UntemplateIdentifierExtractFromLineRegex = """^\s*val\s+Untemplate_(\w+)\s+\=untemplate\.Untemplate\[.+$""".r