package untemplate

private val EndAnchoredDelimiterRegexString = """([^\#]*)(?:\#.*)?$"""

private val UnanchoredHeaderDelimeterRegexString = """\(([^\)]*?)\)\[(.*)\]\~+\((.*?)\)\>"""
private val UnanchoredHeaderDelimeterRegex = ("""(?<!\\)""" + UnanchoredHeaderDelimeterRegexString).r
private val AnchoredHeaderDelimeterRegex = ("""^"""+ UnanchoredHeaderDelimeterRegexString + EndAnchoredDelimiterRegexString).r

// XXX: UnanchoredTextStartDelimeterRegex is a bit not-quite-right, '~' neg lookback prevents matching headers, but also
//      prevents matching just "~()>", which we would want matched. We can't lookback to the whole tail of the header, though,
//      because it is variable length. We only use the unanchored regexes to warn, so the effect of the ot-quite-rightness
//      will be to skip some warnings we'd rather emit. If we use the Unanchored regexes for some more serious purpose in
//      the future, we will need to revisit this.
private val UnanchoredTextStartDelimeterRegexString = """\((\s*\w*\s*)\)[\-\>]*\>"""
private val UnanchoredTextStartDelimeterRegex = ("""(?<!(?:\\|\<|\~))""" + UnanchoredTextStartDelimeterRegexString).r
private val AnchoredTextStartDelimiterRegex = ("""^""" + UnanchoredTextStartDelimeterRegexString + EndAnchoredDelimiterRegexString).r

// XXX: UnanchoredTextEndDelimeterRegex is a bit not-quite-right, '<|-' neg lookback prevents matching escaped long delimeters, but also
//      prevents matching just "-<()", which we would want matched. We can't lookback to the whole tail of the long regex, though,
//      because it is variable length. We only use the unanchored regexes to warn, so the effect of the ot-quite-rightness
//      will be to skip some warnings we'd rather emit. If we use the Unanchored regexes for some more serious purpose in
//      the future, we will need to revisit this.
private val UnanchoredTextEndDelimeterRegexString = """\<[\<\-]*\(\)"""
private val UnanchoredTextEndDelimeterRegex = ("""(?<!(?:\\|\<|\-))""" + UnanchoredTextEndDelimeterRegexString).r
private val AnchoredTextEndDelimeterRegex = ("""^""" + UnanchoredTextEndDelimeterRegexString + EndAnchoredDelimiterRegexString).r

private val EmbeddedExpressionRegex = """(?<!\\)\<\((.+?)\)\>""".r

private val UnescapeHeaderDelimeterRegex = """\\(\([^\)]*?\)\[.*?\]\~+\(.*?\)\>)""".r
private val UnescapeTextStartDelimeterRegex = """\\(\(\s*\w*\s*\)[\-\>]*\>)""".r
private val UnescapeTextEndDelimeterRegex = """\\(\<[\<\-]*\(\))""".r
private val UnescapeEmbeddedExpressionRegex = """\\(\<\(.+?\)\>)""".r

private val UnescapeRegexes =
  UnescapeHeaderDelimeterRegex :: UnescapeTextStartDelimeterRegex :: UnescapeTextEndDelimeterRegex :: UnescapeEmbeddedExpressionRegex :: Nil

private val PackageExtractFromLineRegex = """^\s*package\s+(\S+)[\;\s]*(?://.*)?$""".r

private val UntemplateIdentifierExtractFromLineRegex = """^\s*val\s+Untemplate_(\w+)\s+\=untemplate\.Untemplate\[.+$""".r