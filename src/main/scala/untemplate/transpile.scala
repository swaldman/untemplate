package untemplate

import java.io.{Writer,StringWriter}
import scala.collection.*
import com.mchange.sc.v2.literal.StringLiteral.formatAsciiScalaStringLiteral

import com.mchange.codegenutil.*

// this code is extremely naive and inelegant.
// There must be a more concise and prettier way.
// Maybe someday

private val DefaultInputType            = "immutable.Map[String,Any]"
private val DefaultInputDefaultArg      = "immutable.Map.empty"
private val DefaultOutputMetadataType   = "Nothing"
private val BackstopInputNameIdentifier = toIdentifier("input")
private val DefaultOutputTransformer    = "identity"

private val K128 = 128 * 1024
private val K16  =  16 * 1024


private final case class TranspileData1(source : UntemplateSource, spaceNormalized : Vector[String], indentLevels : Vector[Int])
private final case class TranspileData2(last : TranspileData1, hasHeader : Boolean, mbInputName : Option[String], mbInputType : Option[String], mbInputDefaultArg : Option[String], mbOutputMetadataType : Option[String], mbOverrideUntemplateName : Option[String], textBlockInfos : Vector[TextBlockInfo])
private final case class TranspileData3(last : TranspileData2, headerInfo : Option[HeaderInfo], nonheaderBlocks : Vector[ParseBlock])

private case class HeaderInfo(mbInputName : Option[Identifier], mbInputType : Option[String], mbInputDefaultArg : Option[String], mbOutputMetadataType : Option[String], mbOverrideUntemplateName : Option[Identifier], headerBlock : ParseBlock.Code)
private final case class TextBlockInfo(functionName : Option[String], startDelimeter : Option[Int], stopDelimeter : Option[Int])

private object ParseBlock:
  final case class Text( functionIdentifier : Option[Identifier], rawTextBlock : String) extends ParseBlock
  final case class Code( text : String, lastIndent : Int ) extends ParseBlock
private sealed trait ParseBlock

private def prefixTabSpaceToSpaces(spacesPerTab : Int, line : String) : String =
  def tabspace(c : Char) = c == '\t' || c == ' '
  val (tabs, rest) = line.span(tabspace)
  val untab = " " * spacesPerTab
  def replace( b : Byte ) : String =
    if b == '\t' then untab
    else if b == ' ' then " "
    else throw new AssertionError(s"Huh? We should only be replacing spaces and tabs, found byte ${b}")
  val spacified = tabs.getBytes(scala.io.Codec.UTF8.charSet).map(replace).mkString
  spacified + rest

private def untabAndCountSpaces( gs : UntemplateSource )(using ui : UnitIndent) : TranspileData1 =
  val indents = Array.ofDim[Int](gs.lines.length)
  val oldLines = gs.lines
  val newLines = mutable.Buffer.empty[String]
  for( i <- 0 until gs.lines.length )
    val oldLine = oldLines(i)
    val untabbed = prefixTabSpaceToSpaces(ui.toInt, oldLine)
    val indent = untabbed.takeWhile(_ == ' ').length
    newLines.append(untabbed)
    indents(i) = indent
  TranspileData1(gs, newLines.toVector, indents.toVector)

private object LineDelimeter:
  // object Header:
  //   def apply(str: String) : Header = if str == null || str.isEmpty then Header(None) else Header(Some(str))
  case class Header(mbInputName : Option[String], mbInputType : Option[String], mbInputDefaultArg : Option[String], mbOutputMetadataType : Option[String], mbOverrideUntemplateName : Option[String]) extends LineDelimeter
  object Start:
    def apply(str : String) : Start = if str == null || str.isEmpty then Start(None) else Start(Some(str))
  case class Start(functionName : Option[String]) extends LineDelimeter
  case object End extends LineDelimeter
private sealed trait LineDelimeter

private def carveAroundDelimeterChar(maybeNullOrBlank : String, delimeter : Char, trim : Boolean) : Tuple2[Option[String],Option[String]] =
  val raw =
    if maybeNullOrBlank == null || maybeNullOrBlank.isEmpty then
      (None, None)
    else
      val dotIndex = maybeNullOrBlank.indexOf(delimeter)
      val len = maybeNullOrBlank.length
      val Last = len - 1
      dotIndex match
        case -1 => (Some(maybeNullOrBlank), None)
        case 0 => (None, Some(maybeNullOrBlank.substring(1)))
        case Last => (Some(maybeNullOrBlank.substring(0, len - 1)), None)
        case i => (Some(maybeNullOrBlank.substring(0, i)), Some(maybeNullOrBlank.substring(i + 1)))
  if trim then Tuple2(nonEmptyStringOption(raw(0).map(_.trim)),nonEmptyStringOption(raw(1).map(_.trim))) else raw


private def basicParse( td1 : TranspileData1 ) : TranspileData2 =
  var headerTuple : Option[Tuple2[Int,LineDelimeter.Header]] = None
  val parseTuples : mutable.SortedMap[Int,LineDelimeter] = mutable.SortedMap.empty // empty only of there are no delimeters at all

  for( i <- 0 until td1.indentLevels.length) // indentLevels.length is also the line length
    if td1.indentLevels(i) == 0 then
      td1.source.lines(i) match {
        case AnchoredHeaderDelimeterRegex(inputNameType, outputMetadataType, untemplateNameDotFunctionName) =>
          // println("SAW HEADER DELIMETER")
          val (overrideUntemplateName, functionName) = carveAroundDelimeterChar(untemplateNameDotFunctionName, '.', trim = true)
          val (inputName, inputTypeWithMbDefaultArg) = carveAroundDelimeterChar(inputNameType, ':', trim = true)
          val (inputType, inputDefaultArg)           = carveAroundDelimeterChar(inputTypeWithMbDefaultArg.getOrElse(""), '=', trim = true)
          if headerTuple == None then
            headerTuple = Some(Tuple2(i, LineDelimeter.Header(nonEmptyStringOption(inputName), nonEmptyStringOption(inputType), nonEmptyStringOption(inputDefaultArg), nonEmptyStringOption(outputMetadataType), overrideUntemplateName)))
            parseTuples += Tuple2(i, LineDelimeter.Start(functionName))
          else
            throw new ParseException(s"Duplicate header delimeter at line ${i}")
        case AnchoredTextStartDelimiterRegex(functionName) =>
          parseTuples += Tuple2(i, LineDelimeter.Start(nonEmptyStringOption(functionName)))
        case AnchoredTextEndDelimeterRegex() =>
          parseTuples += Tuple2(i, LineDelimeter.End)
        case _ => /* No match, move on */
      }

  // sanity checks
  headerTuple.foreach { case (line, ldh) =>
    if line != parseTuples.keys.head then
      throw new ParseException(
        s"The first start tuple is at (zero-indexed) line ${parseTuples.keys.head}, but header boundary is at ${line}, should be identical." +
        "Perhaps there is a start delimeter above the header delimeter. That would be bad!"
      )
  }

  // check correct alternation of types
  var lastSeen : LineDelimeter = null; // so sue me... purely an internal implementation detail
  parseTuples.foreach { case (i, delim) =>
    // println( s"Line: ${i+1}     delim: ${delim}" )
    if lastSeen == null then
      lastSeen = delim
    else
      (lastSeen, delim) match {
        case (a : LineDelimeter.Start, b : LineDelimeter.End.type)         => /* Good */
        case (a : LineDelimeter.End.type, b : LineDelimeter.Start)         => /* Good */
        case (a : LineDelimeter.Start, b : LineDelimeter.Start)            =>
          throw new ParseException(s"Line ${i+1}: Text region start requested within already started text region. Please escape untemplate delimeters in text.")
        case (a : LineDelimeter.End.type, b : LineDelimeter.End.type)       =>
          throw new ParseException(s"Line ${i+1}: Text region end requested within Scala code region.")
        case (_ : LineDelimeter.Header, _) | (_, _ : LineDelimeter.Header) =>
          throw new AssertionError(s"Line ${i+1}: There should be no LineDelimeter.Header in parseTuples!")
      }
    lastSeen = delim
  }

  // okay... apparently we have alternating sections. Let's build our output
  val (mbInputName : Option[String], mbInputType : Option[String], mbInputDefaultArg : Option[String], mbOutputMetadataType : Option[String], mbOverrideUntemplateName : Option[String]) =
    headerTuple match
      case Some(tup) =>
        val ldHeader = tup(1)
        (ldHeader.mbInputName, ldHeader.mbInputType, ldHeader.mbInputDefaultArg, ldHeader.mbOutputMetadataType, ldHeader.mbOverrideUntemplateName)
      case None =>
        (None, None, None, None, None)

  if parseTuples.nonEmpty then
    val textBlockInfos = Vector.newBuilder[TextBlockInfo]

    val headTuple = parseTuples.head
    val (groupTuples, prependHead) =
      headTuple match {
        case (_, _ : LineDelimeter.Start)    => Tuple2(parseTuples, false)
        case (_, _ : LineDelimeter.End.type) => Tuple2(parseTuples.tail, true)
        case (_, _) =>
          throw new AssertionError("There should be no LineDelimter.Header values in parseTuples.")
      }
    if prependHead then
      textBlockInfos.addOne(TextBlockInfo(None,None,Some(headTuple(0))))
    groupTuples.grouped(2).foreach { minimap =>
      val l = minimap.toList
      val (start, functionName) =
        l.head match {
          case (s, LineDelimeter.Start(fname)) => (s, fname)
          case (a,b) =>
            throw new AssertionError(s"Expected tuple of (Int,LineDelimeter.Start), found (${a},${b})")
        }
      val end =
        if (l.tail.nonEmpty) Some(l.tail.head(0))
        else None
      textBlockInfos.addOne(TextBlockInfo(functionName,Some(start),end))
    }
    TranspileData2( td1, headerTuple.nonEmpty, mbInputName, mbInputType, mbInputDefaultArg, mbOutputMetadataType, mbOverrideUntemplateName, textBlockInfos.result() )
  else
    TranspileData2( td1, false, None, None, None, None, None, Vector.empty )

private def parseBlockTextFromInfo( unmodifiedLines : Vector[String], info : TextBlockInfo ) =
  val text = (info.startDelimeter, info.stopDelimeter) match
    case (Some(before), Some(until)) => unmodifiedLines.slice(before + 1, until).mkString(LineSep)
    case (None,         Some(until)) => unmodifiedLines.slice(0, until).mkString(LineSep)
    case (Some(before), None       ) => unmodifiedLines.slice(before + 1, unmodifiedLines.size).mkString(LineSep)
    case (None,         None       ) => unmodifiedLines.mkString(LineSep)
  val functionName = info.functionName.map(toIdentifier)
  ParseBlock.Text(functionName, text)

private def collectBlocksNonEmpty( td2 : TranspileData2 ) : TranspileData3 =
  val mbInputNameIdentifier                 = td2.mbInputName.map( toIdentifier )
  val mbInputType                           = td2.mbInputType
  val mbInputDefaultArg                     = td2.mbInputDefaultArg
  val mbOutputMetadataType                  = td2.mbOutputMetadataType
  val mbOverrideUntemplateNameIdentifier     = td2.mbOverrideUntemplateName.map( toIdentifier )
  var headerBlock : Option[ParseBlock.Code] = None

  // for text, we take from unmodified UntemplateSource.
  // in other words, don't mess with tabs and spaces
  val unmodifiedLines = td2.last.source.lines

  // for code, we take from our tab-to-space-normalized lines
  val normalizedLines = td2.last.spaceNormalized
  val indents = td2.last.indentLevels

  val infos = td2.textBlockInfos
  var lastInfo : Option[TextBlockInfo] = None
  val blocksBuilder = Vector.newBuilder[ParseBlock]

  def registerCodeBlock(codeBlock: ParseBlock.Code) =
    (td2.hasHeader, headerBlock) match
      case (true, None) => headerBlock = Some(codeBlock)
      case _ => blocksBuilder.addOne(codeBlock)

  infos.foreach { info =>
    val mbPriorCodeBlock =
      (lastInfo.flatMap(_.stopDelimeter), info.startDelimeter) match
        case (Some(before), Some(until)) => Some(ParseBlock.Code(normalizedLines.slice(before+1,until).mkString("",LineSep,LineSep), if until == 0 then 0 else indents(until-1)))
        case (None,         Some(until)) => Some(ParseBlock.Code(normalizedLines.slice(0, until).mkString("",LineSep,LineSep), 0))
        case (Some(before), None       ) => throw new AssertionError(s"Interior text blocks should have start delimteres! [prior: ${lastInfo}, current: ${info}]")
        case (None,         None       ) => None // this is the first info, no start means we begin inside text

    mbPriorCodeBlock.foreach(registerCodeBlock)
    blocksBuilder.addOne(parseBlockTextFromInfo(unmodifiedLines, info))
    lastInfo = Some(info)
  }
  val mbLastCodeBlock =
    lastInfo.flatMap( _.stopDelimeter ).map { before =>
      ParseBlock.Code(normalizedLines.slice(before+1,normalizedLines.length).mkString("",LineSep,LineSep), indents.last )
    }
  mbLastCodeBlock.foreach(blocksBuilder.addOne)

  val nonheaderBlocks = blocksBuilder.result()
  val mbHeaderInfo =
    headerBlock.map(hblock => HeaderInfo(mbInputNameIdentifier, mbInputType, mbInputDefaultArg, mbOutputMetadataType, mbOverrideUntemplateNameIdentifier, hblock))

  TranspileData3( td2, mbHeaderInfo : Option[HeaderInfo], nonheaderBlocks )

private def collectBlocks( td2 : TranspileData2 ) : TranspileData3 =
  if (td2.textBlockInfos.nonEmpty) then collectBlocksNonEmpty(td2)
  else
    val unmodifiedText = td2.last.source.lines.mkString(LineSep)
    TranspileData3(td2, None, Vector(ParseBlock.Text(None, unmodifiedText)))

private def unescapeUntemplateDelimeters( s : String ) : String =
  UnescapeRegexes.foldLeft(s)( (last, regex) => regex.replaceAllIn(last, m => m.group(1) ) )

private def rawTextToSourceConcatenatedLiteralsAndExpressions( text : String ) : String =
  val sb = new StringBuilder(text.length * 2)
  val mi = EmbeddedExpressionRegex.findAllIn(text)
  var nextStart = 0
  while mi.hasNext do
    mi.next()
    val nextEnd = mi.start
    val expression = mi.group(1)
    val textBit = text.substring(nextStart, nextEnd)
    val unescapedTextBit = unescapeUntemplateDelimeters(textBit)
    sb.append(formatAsciiScalaStringLiteral(unescapedTextBit))
    sb.append(" + (")
    sb.append( expression )
    sb.append(") +")
    sb.append(LineSep)
    nextStart = mi.end
  val lastTextBit = text.substring(nextStart)
  val unescapedLastTextBit = unescapeUntemplateDelimeters(lastTextBit)
  sb.append(formatAsciiScalaStringLiteral( unescapedLastTextBit + LineSep)) //we removed the separators parsing into lines, better put 'em back!
  sb.toString

private def rawTextToBlockPrinter( innerIndent : Int, text : String ) : String =
  // we used to generate a function accepting ${inputName} : ${inputIdentifier}, but we have this anyway
  // in the closure, so there's no point in complicating things with a shadow
  val spaces = " " * innerIndent
  val stringExpression = rawTextToSourceConcatenatedLiteralsAndExpressions( text )
  s"""|new Function0[String]:
      |${spaces}def apply() : String =
      |${increaseIndent(innerIndent*2)(stringExpression)}""".stripMargin

private def rawTextToBlockPrinter( text : String )(using ui : UnitIndent) : String = rawTextToBlockPrinter( ui.toInt, text )

private final case class PartitionedHeaderBlock(packageOverride : Option[String], importsText : String, otherHeaderText : String, otherLastIndent : Int)

private def partitionHeaderBlock( text : String ) : PartitionedHeaderBlock =
  // println( s">>> headerBlockToPartition: ${text}" )
  val linesTuple = text.linesIterator.to(List).partition( _.trim.startsWith("import ") )
  val importsText = linesTuple(0).map( _.trim ).mkString("",LineSep,LineSep)
  val nonImportsTuple = linesTuple(1).partition( _.trim.startsWith("package ") )
  val packageOverride =
    val packageLines = nonImportsTuple(0)
    if packageLines.nonEmpty then
      val packageComponents =
        packageLines.map {
          case PackageExtractRegex(pkgPath) => pkgPath
          case line                         => throw new ParseException(s"Bad package declaration in header: '${line}''")
        }
      Some( joinPackageIdentifierPaths(packageComponents) )
    else
      None
  val otherHeaderText = nonImportsTuple(1).mkString("",LineSep,LineSep)
  val otherLastIndent = nonImportsTuple(1).lastOption.fold( 0 )( _.takeWhile(_ == ' ').length )
  PartitionedHeaderBlock(packageOverride, importsText, otherHeaderText, otherLastIndent)

private def transpileToWriter (
 locationPackage : LocationPackage,
 defaultUntemplateName : Identifier,
 selectCustomizer : Customizer.Selector,
 src : UntemplateSource,
 w : Writer,
 warnings : mutable.Buffer[UntemplateWarning]
) : Identifier =
  val td1 = untabAndCountSpaces( src )
  val td2 = basicParse( td1 )
  val td3 = collectBlocks( td2 )

  // println(td3)
  // println(s"headerInfo[${untemplateName}] >>> " + td3.headerInfo)

  val (mbInputName, mbInputType, mbInputDefaultArg, mbOutputMetadataType, mbOverrideUntemplateName, mbPartitionedHeaderBlock) =
    td3.headerInfo match
      case Some( HeaderInfo( mbInputName, mbInputType, mbInputDefaultArg, mbOutputMetadataType, mbOverrideUntemplateName, headerBlock ) ) => (mbInputName, mbInputType, mbInputDefaultArg, mbOutputMetadataType, mbOverrideUntemplateName, Some(partitionHeaderBlock(headerBlock.text)))
      case None                                                                                                                           => (None, None, None, None, None, None)

  val mbFromLocationPackage = if locationPackage.nonDefault then Some(locationPackage.dotty) else None
  val mbExplicitPackage = mbPartitionedHeaderBlock.flatMap(_.packageOverride)
  val resolvedPackage = (mbExplicitPackage orElse mbFromLocationPackage)

  val tentativeOutputMetadataType: String = (mbOutputMetadataType).getOrElse(DefaultOutputMetadataType)
  val resolvedUntemplateName: Identifier = (mbOverrideUntemplateName).getOrElse(defaultUntemplateName)

  val customizerKey      = Customizer.Key (
    inferredPackage      = mbFromLocationPackage,
    resolvedPackage      = resolvedPackage,
    inferredFunctionName = defaultUntemplateName.toString,
    resolvedFunctionName = resolvedUntemplateName.toString,
    outputMetadataType   = tentativeOutputMetadataType
  )
  val customizer = selectCustomizer( customizerKey )

  val inputName : Identifier = (mbInputName orElse customizer.mbDefaultInputName.map(asIdentifier)).getOrElse( BackstopInputNameIdentifier )

  val ( inputType, mbDefaultArg ) =
    val explicit   = mbInputType.map(tpe => Tuple2(tpe, mbInputDefaultArg))
    val customized = (customizer.mbDefaultInputTypeDefaultArg.map(ditda => Tuple2(ditda.inputType, ditda.mbDefaultArg)))
    val backstop   = Tuple2(DefaultInputType, Some(DefaultInputDefaultArg))
    (explicit orElse customized).getOrElse( backstop )

  val inputDefaultArgClause = mbDefaultArg.fold("")( defaultArg => " = " + defaultArg )

  // None is the default package. Customizers specify that as an empty String, so we take care to convert that to None
  val mbPackagePath = mbExplicitPackage orElse nonEmptyStringOption(customizer.mbOverrideInferredPackage) orElse mbFromLocationPackage

  val untemplateName = (mbOverrideUntemplateName orElse customizer.mbOverrideInferredFunctionName.map(asIdentifier)).getOrElse(defaultUntemplateName)

  val perhapsCustomizedOutputMetadataType = (mbOutputMetadataType orElse customizer.mbDefaultMetadataType).getOrElse(DefaultOutputMetadataType)

  //println(">>>>> perhapsCustomizedOutputMetadataType: " + perhapsCustomizedOutputMetadataType)

  val defaultMetadataValue =
    customizer.mbDefaultMetadataValue match
      case Some(expr) =>
        if expr.startsWith("Some") || expr == "None" then
          val warning = toUntemplateWarning (
            s"${untemplateName}: Customizer override of the default metadata value '${expr}' should not be 'None' or wrapped in Some(...) unless the metadata type is itself an option!"
          )
          warnings += warning
        s"Some(${expr})"
      case None => "None"

  val defaultOutputTransformer = customizer.mbDefaultOutputTransformer.getOrElse(DefaultOutputTransformer)

  val textBlocks = td3.nonheaderBlocks.collect { case b : ParseBlock.Text => b }

  mbPackagePath.foreach { dotpath =>
    w.writeln(s"package ${dotpath}")
    w.writeln()
  }
  w.writeln("import java.io.{Writer,StringWriter}")
  w.writeln("import scala.collection.*")
  w.writeln()
  if (customizer.extraImports.nonEmpty)
    customizer.extraImports.foreach { line =>
      val tl = line.trim
      if tl.startsWith("import") then
        w.writeln(tl)
      else
        w.writeln(s"import ${tl}")
    }
    w.writeln()

  mbPartitionedHeaderBlock.foreach { phb =>
    if phb.importsText.nonEmpty then
      w.writeln(phb.importsText)
      w.writeln()
  }
  val blockPrinterTups =
    (for (i <- 0 until textBlocks.length) yield (s"block${i}", textBlocks(i).functionIdentifier, rawTextToBlockPrinter( textBlocks(i).rawTextBlock ))).toVector

//  w.indentln(0)(s"private object ${helperName}:")
//  blockPrinterTups.foreach { tup =>
//    w.indentln(1)(s"private val ${tup(0)} = ${tup(2)}" )
//  }
//  w.writeln()
//  val allBPsStr =  blockPrinterTups.map(tup => tup(0)).mkString(", ")
//  w.indentln( indentLevel = 1 )(s"val BlockPrinters = Vector( $allBPsStr )")
//  w.writeln()
//  blockPrinterTups.foreach { tup =>
//    tup(1).foreach( fname => w.indentln(1)(s"val ${fname} = ${tup(0)}") )
//  }
//  w.indentln(0)(s"end ${helperName}")
//  w.writeln()
  val argList = s"(${inputName} : ${inputType}${inputDefaultArgClause})"
  val functionObjectName = s"Untemplate_${untemplateName}"
  val fullReturnType = s"untemplate.Result[${perhapsCustomizedOutputMetadataType}]"
  val embeddableDefaultArg = mbDefaultArg.fold("(None : Option[String])")(defaultArg => s"""Some("${defaultArg}")""")
  w.indentln(0)(s"val ${functionObjectName} = new untemplate.Untemplate[${inputType},${perhapsCustomizedOutputMetadataType}]:")
  w.indentln(1)( """val UntemplateFunction             = this""")
  w.indentln(1)(s"""val UntemplateName                 = "${untemplateName}"""")
  w.indentln(1)(s"""val UntemplateInputName            = "${inputName}"""")
  w.indentln(1)(s"""val UntemplateInputType            = "${inputType}"""")
  w.indentln(1)(s"""val UntemplateInputDefaultArgument = ${embeddableDefaultArg}""")
  w.indentln(1)(s"""val UntemplateOutputMetadataType   = "${perhapsCustomizedOutputMetadataType}"""")
  w.writeln()
  w.indentln(1)(s"def apply${argList} : ${fullReturnType} =")
  w.indentln(2)(untemplateBody(td3, inputName, inputType, perhapsCustomizedOutputMetadataType, defaultMetadataValue, defaultOutputTransformer, blockPrinterTups, mbPartitionedHeaderBlock))
  w.indentln(1)(s"end apply")
  w.indentln(0)(s"end ${functionObjectName}")
  w.writeln()
  w.indentln(0)(s"def ${untemplateName}${argList} : ${fullReturnType} = ${functionObjectName}( ${inputName} )")
  untemplateName

private def untemplateBody(
  td3 : TranspileData3,
  inputName : Identifier,
  inputType : String,
  outputMetadataType : String,
  defaultMetadataValue : String,
  defaultOutputTransformer : String,
  blockPrinterTups : Vector[Tuple3[String,Option[Identifier],String]],
  mbPartitionedHeaderBlock : Option[PartitionedHeaderBlock]
)(using ui : UnitIndent) : String =
  val origTextLen = td3.last.last.source.textLen

  val w = new StringWriter(K16) // XXX: Hardcoded initial capacity
  var lastIndentSpaces = 0

  def lastIndentLevel =
    lastIndentSpaces / ui.toInt + (if (lastIndentSpaces % ui.toInt) == 0 then 0 else 1)

  // setup author resources
  // w.writeln(s"import ${helperName}.*")
  // w.writeln()

  // For now I don't think this is worth the extra complexity.
  //
  // w.indentln(0)("extension (s : mutable.Map[String,Any])")
  // w.indentln(1)("def as[T](key: String): T = s(key).asInstanceOf[T]")
  // w.indentln(1)("def check[T](key: String): Option[T] = s.get(key).map(_.asInstanceOf[T])")
  // w.writeln()

  // w.writeln("val scratchpad : mutable.Map[String,Any] = mutable.Map.empty[String,Any]")
  w.writeln(s"val writer             : StringWriter = new StringWriter(${origTextLen*10})")
  w.writeln(s"var mbMetadata         : Option[${outputMetadataType}] = ${defaultMetadataValue}")
  w.writeln(s"var outputTransformer  : Function1[untemplate.Result[${outputMetadataType}],untemplate.Result[${outputMetadataType}]] = ${defaultOutputTransformer}")
  w.writeln()

  // header first
  mbPartitionedHeaderBlock.foreach { phb =>
    // println(s">>> phb: ${phb}")
    // println(s">>> phb.otherHeaderText: ${phb.otherHeaderText}")
    w.writeln( phb.otherHeaderText )
    lastIndentSpaces = phb.otherLastIndent
  }

  var textBlockCount = 0
  td3.nonheaderBlocks.foreach { block =>
    // println(s"Processing block: ${block}")
    block match
      case cblock : ParseBlock.Code =>
        // println(s">>> cblock: ${cblock}")
        // println(s""">> cblock.text.endsWith("\n"): ${cblock.text.endsWith("\n")}""")
        w.indent(0)(cblock.text) // properly includes its trailing line feed
        lastIndentSpaces = cblock.lastIndent
      case tblock : ParseBlock.Text =>
        val tup = blockPrinterTups( textBlockCount )
        tblock.functionIdentifier match
          case Some( fcnName ) =>
            w.indentln(lastIndentLevel)(s"val ${tup(0)} = ${tup(2)}" )
            w.indentln(lastIndentLevel)(s"def ${fcnName}() = ${tup(0)}()" )
          case None =>
            w.indentln(lastIndentLevel + 1)(s"val ${tup(0)} = ${tup(2)}" )
            w.indentln(lastIndentLevel + 1)(s"writer.write(block${textBlockCount}())${LineSep}")
        textBlockCount += 1
  }
  w.indentln(0)("outputTransformer( untemplate.Result( mbMetadata, writer.toString ) )")
  w.toString

private def defaultTranspile( locationPackage : LocationPackage, defaultUntemplateName : Identifier, selectCustomizer : Customizer.Selector, src : UntemplateSource ) : UntemplateScala =
  val w = new StringWriter(K16) // XXX: hardcoded initial buffer length, should we examine src?
  val warnings = mutable.Buffer.empty[UntemplateWarning]
  val untemplateName = transpileToWriter(locationPackage, defaultUntemplateName, selectCustomizer, src, w, warnings)
  UntemplateScala(untemplateName, warnings.to(Vector), w.toString)

