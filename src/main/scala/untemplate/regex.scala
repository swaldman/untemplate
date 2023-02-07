package untemplate

private val EndAnchoredDelimiterRegexString = """([^\#]*)(?:\#.*)?$"""

private val UnanchoredHeaderDelimiterRegexString = """\(([^\)]*?)\)\[(.*)\]\~+\((.*?)\)\>"""
private val WarningUnanchoredHeaderDelimiterRegex = ("""(?<!\\)""" + UnanchoredHeaderDelimiterRegexString).r
private val AnchoredHeaderDelimiterRegex = ("""^"""+ UnanchoredHeaderDelimiterRegexString + EndAnchoredDelimiterRegexString).r

// XXX: WarningUnanchoredTextStartDelimiterRegex is a bit not-quite-right, '~' neg lookback prevents matching headers, but also
//      prevents matching just "~()>", which we would want matched. We can't lookback to the whole tail of the header, though,
//      because it is variable length. We only use the unanchored regexes to warn, so the effect of the ot-quite-rightness
//      will be to skip some warnings we'd rather emit. If we use the Unanchored regexes for some more serious purpose in
//      the future, we will need to revisit this.
private val UnanchoredTextStartDelimiterRegexString = """\((\s*\w*\s*)\)[\-\>]*\>"""
private val WarningUnanchoredTextStartDelimiterRegex = ("""(?<!(?:\\|\<|\~))""" + UnanchoredTextStartDelimiterRegexString).r
private val AnchoredTextStartDelimiterRegex = ("""^""" + UnanchoredTextStartDelimiterRegexString + EndAnchoredDelimiterRegexString).r

// XXX: WarningUnanchoredTextEndDelimiterRegex is a bit not-quite-right, '<|-' neg lookback prevents matching escaped long delimiters, but also
//      prevents matching just "-<()", which we would want matched. We can't lookback to the whole tail of the long regex, though,
//      because it is variable length. We only use the unanchored regexes to warn, so the effect of the ot-quite-rightness
//      will be to skip some warnings we'd rather emit. If we use the Unanchored regexes for some more serious purpose in
//      the future, we will need to revisit this.
private val UnanchoredTextEndDelimiterRegexString = """\<[\<\-]*\(\)"""
private val WarningUnanchoredTextEndDelimiterRegex = ("""(?<!(?:\\|\<|\-))""" + UnanchoredTextEndDelimiterRegexString).r
private val AnchoredTextEndDelimiterRegex = ("""^""" + UnanchoredTextEndDelimiterRegexString + EndAnchoredDelimiterRegexString).r

private val EmbeddedExpressionRegex = """(?<!\\)\<\((.+?)\)\>""".r

private val UnescapeHeaderDelimiterRegex = """\\(\([^\)]*?\)\[.*?\]\~+\(.*?\)\>)""".r
private val UnescapeTextStartDelimiterRegex = """\\(\(\s*\w*\s*\)[\-\>]*\>)""".r
private val UnescapeTextEndDelimiterRegex = """\\(\<[\<\-]*\(\))""".r
private val UnescapeEmbeddedExpressionRegex = """\\(\<\(.+?\)\>)""".r

private val UnescapeRegexes =
  UnescapeHeaderDelimiterRegex :: UnescapeTextStartDelimiterRegex :: UnescapeTextEndDelimiterRegex :: UnescapeEmbeddedExpressionRegex :: Nil

private val PackageExtractFromLineRegex = """^\s*package\s+(\S+)[\;\s]*(?://.*)?$""".r

private val UntemplateIdentifierExtractFromLineRegex = """^\s*val\s+Untemplate_(\w+)\s*\=\s*new\s+untemplate\.Untemplate\[.+$""".r