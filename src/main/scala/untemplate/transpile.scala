package untemplate

import java.io.{Writer,StringWriter}
import scala.collection.*
import com.mchange.sc.v2.literal.StringLiteral.formatAsciiScalaStringLiteral
import scala.jdk.StreamConverters.StreamHasToScala

import com.mchange.codegenutil.*

// this code is extremely naive and inelegant.
// There must be a more concise and prettier way.
// Maybe someday

private val UnitIdentifier              = toIdentifier("Unit")
private val BackstopInputNameIdentifier = toIdentifier("input")

private val K100 = 100 * 1024

private final case class TranspileData1(source : GeneratorSource, spaceNormalized : Vector[String], indentLevels : Vector[Int])
private final case class TranspileData2(last : TranspileData1, hasHeader : Boolean, mbInputName : Option[String], mbInputType : Option[String], textBlockInfos : Vector[TextBlockInfo])
private final case class TranspileData3(last : TranspileData2, headerInfo : Option[HeaderInfo], nonheaderBlocks : Vector[ParseBlock])

private case class HeaderInfo(mbInputName : Option[Identifier], mbInputTypeIdentifier : Option[Identifier], headerBlock : ParseBlock.Code)
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

private def untabAndCountSpaces( gs : GeneratorSource )(using ui : UnitIndent) : TranspileData1 =
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
  case class Header(mbInputName : Option[String], mbInputType : Option[String]) extends LineDelimeter
  object Start:
    def apply(str : String) : Start = if str == null || str.isEmpty then Start(None) else Start(Some(str))
  case class Start(functionName : Option[String]) extends LineDelimeter
  case object End extends LineDelimeter
private sealed trait LineDelimeter

private def basicParse( td1 : TranspileData1 ) : TranspileData2 =
  var headerTuple : Option[Tuple2[Int,LineDelimeter.Header]] = None
  val parseTuples : mutable.SortedMap[Int,LineDelimeter] = mutable.SortedMap.empty // empty only of there are no delimeters at all

  for( i <- 0 until td1.indentLevels.length) // indentLevels.length is also the line length
    if td1.indentLevels(i) == 0 then
      td1.source.lines(i) match {
        case AnchoredHeaderDelimeterRegex(inputName, inputType, functionName) =>
          if headerTuple == None then
            headerTuple = Some(Tuple2(i, LineDelimeter.Header(Option(inputName), Option(inputType))))
            parseTuples += Tuple2(i, LineDelimeter.Start(functionName))
          else
            throw new ParseException(s"Duplicate header delimeter at line ${i}")
        case AnchoredTextStartDelimiterRegex(functionName) =>
          parseTuples += Tuple2(i, LineDelimeter.Start(functionName))
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
  val (mbInputName : Option[String], mbInputType : Option[String]) =
    headerTuple match
      case Some(tup) =>
        val ldHeader = tup(1)
        (ldHeader.mbInputName, ldHeader.mbInputType)
      case None =>
        (None, None)

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
    TranspileData2( td1, headerTuple.nonEmpty, mbInputName, mbInputType, textBlockInfos.result() )
  else
    TranspileData2( td1, false, None, None, Vector.empty )

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
  val mbInputTypeIdentifier                   = td2.mbInputType.map( toIdentifier )
  var headerBlock : Option[ParseBlock.Code] = None

  // for text, we take from unmodified GeneratorSource.
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
        case (Some(before), Some(until)) => Some(ParseBlock.Code(normalizedLines.slice(before+1,until).mkString(LineSep), if until == 0 then 0 else indents(until-1)))
        case (None,         Some(until)) => Some(ParseBlock.Code(normalizedLines.slice(0, until).mkString(LineSep), 0))
        case (Some(before), None       ) => throw new AssertionError(s"Interior text blocks should have start delimteres! [prior: ${lastInfo}, current: ${info}]")
        case (None,         None       ) => None // this is the first info, no start means we begin inside text

    mbPriorCodeBlock.foreach(registerCodeBlock)
    blocksBuilder.addOne(parseBlockTextFromInfo(unmodifiedLines, info))
    lastInfo = Some(info)
  }
  val mbLastCodeBlock =
    lastInfo.flatMap( _.stopDelimeter ).map { before =>
      ParseBlock.Code(normalizedLines.slice(before+1,normalizedLines.length).mkString(LineSep), indents.last )
    }
  mbLastCodeBlock.foreach(blocksBuilder.addOne)

  val nonheaderBlocks = blocksBuilder.result()
  val mbHeaderInfo =
    headerBlock.map(hblock => HeaderInfo(mbInputNameIdentifier, mbInputTypeIdentifier, hblock))

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
    sb.append(" + ")
    sb.append( expression )
    sb.append(" +")
    sb.append(LineSep)
    nextStart = mi.end
  val lastTextBit = text.substring(nextStart)
  val unescapedLastTextBit = unescapeUntemplateDelimeters(lastTextBit)
  sb.append(formatAsciiScalaStringLiteral( unescapedLastTextBit + LineSep)) //we removed the separators parsing into lines, better put 'em back!
  sb.toString

private def rawTextToBlockPrinter( inputName : Identifier, inputType : Identifier, innerIndent : Int, text : String ) : String =
  val spaces = " " * innerIndent
  val stringExpression = rawTextToSourceConcatenatedLiteralsAndExpressions( text )
  s"""|new Function2[${inputType},mutable.Map[String,Any],String]:
      |${spaces}def apply( ${inputName} : ${inputType}, scratchpad : mutable.Map[String,Any]) : String =
      |${increaseIndent(innerIndent*2)(stringExpression)}""".stripMargin

private def rawTextToBlockPrinter( inputName : Identifier, inputType : Identifier, text : String )(using ui : UnitIndent) : String = rawTextToBlockPrinter(inputName, inputType, ui.toInt, text)

private final case class PartitionedHeaderBlock(importsText : String, otherHeaderText : String, otherLastIndent : Int)

private def partitionHeaderBlock( text : String ) : PartitionedHeaderBlock =
  val linesTuple = text.lines.toScala(List).partition( _.trim.startsWith("import ") )
  val importsText = linesTuple(0).map( _.trim ).mkString(LineSep)
  val otherHeaderText = linesTuple(1).mkString(LineSep)
  val otherLastIndent = linesTuple(1).lastOption.fold( 0 )( _.takeWhile(_ == ' ').length )
  PartitionedHeaderBlock(importsText, otherHeaderText, otherLastIndent)

private def transpileToWriter(pkg : List[Identifier], generatorName : Identifier, generatorExtras : GeneratorExtras, src : GeneratorSource, w : Writer) : Unit =
  val td1 = untabAndCountSpaces( src )
  val td2 = basicParse( td1 )
  val td3 = collectBlocks( td2 )
  val (mbInputName, mbInputType, mbPartitionedHeaderBlock) =
    td3.headerInfo match
      case Some( HeaderInfo( mbInputName, mbInputType, headerBlock ) ) => (mbInputName, mbInputType, Some(partitionHeaderBlock(headerBlock.text)))
      case None                                                        => (None, None, None)
  val inputVarName : Identifier = (mbInputName orElse generatorExtras.mbDefaultInputName).getOrElse( BackstopInputNameIdentifier )
  val inputType    : Identifier = (mbInputType orElse generatorExtras.mbDefaultInputType).getOrElse( UnitIdentifier )

  val textBlocks = td3.nonheaderBlocks.collect { case b : ParseBlock.Text => b }

  val helperName = toIdentifier(s"Helper_${generatorName}")

  if pkg.nonEmpty then
    w.writeln(s"""package ${pkg.mkString(".")}""")

  w.writeln()
  w.writeln("import java.io.{Writer,StringWriter}")
  w.writeln("import scala.collection.*")
  w.writeln()
  w.writeln("// start generator-extras imports")
  generatorExtras.extraImports.foreach { line =>
    val tl = line.trim
    if tl.startsWith("import") then
      w.writeln(tl)
    else
      w.writeln(s"import ${tl}")
  }
  w.writeln("// end generator-extras imports")
  w.writeln()
  w.writeln("// start author-defined imports")
  mbPartitionedHeaderBlock.foreach { phb =>
    w.writeln(phb.importsText)
  }
  w.writeln("// end author-defined imports")
  w.writeln()
  w.writeln(0)(s"private object ${helperName}:")
  val blockPrinterTups =
    for (i <- 0 until textBlocks.length) yield (s"BP${i}", textBlocks(i).functionIdentifier, rawTextToBlockPrinter( inputVarName, inputType, textBlocks(i).rawTextBlock ))
  blockPrinterTups.foreach { tup =>
    w.writeln(1)(s"private val ${tup(0)} = ${tup(2)}" )
  }
  w.writeln()
  val allBPsStr =  blockPrinterTups.map(tup => tup(0)).mkString(", ")
  w.writeln( indentLevel = 1 )(s"val BlockPrinters = Vector( $allBPsStr )")
  w.writeln()
  blockPrinterTups.foreach { tup =>
    tup(1).foreach( fname => w.writeln(1)(s"val ${fname} = ${tup(0)}") )
  }
  w.writeln(0)(s"end ${helperName}")
  w.writeln()
  w.writeln(0)(s"def ${generatorName}(${inputVarName} : ${inputType}) : String =")
  w.writeln(1)(generatorBody(td3, inputVarName, helperName, mbPartitionedHeaderBlock))
  w.writeln(0)(s"end ${generatorName}")
  w.writeln()

private def generatorBody( td3 : TranspileData3, inputVarName : Identifier, helperName : Identifier, mbPartitionedHeaderBlock : Option[PartitionedHeaderBlock] )(using ui : UnitIndent) : String =
  val w = new StringWriter(K100) // XXX: Hardcoded initial capacity
  var lastIndentSpaces = 0

  def lastIndentLevel =
    lastIndentSpaces / ui.toInt + (if (lastIndentSpaces % ui.toInt) == 0 then 0 else 1)

  // setup author resources
  w.writeln(s"import ${helperName}.*")
  w.writeln()
  w.writeln(0)("extension (s : mutable.Map[String,Any])")
  w.writeln(1)("def as[T](key: String): T = s(key).asInstanceOf[T]")
  w.writeln(1)("def check[T](key: String): Option[T] = s.get(key).map(_.asInstanceOf[T])")
  w.writeln()
  w.writeln("val scratchpad : mutable.Map[String,Any] = mutable.Map.empty[String,Any]")
  w.writeln(s"val writer = new StringWriter(${K100}) //XXX: Hardcoded initial capacity")
  w.writeln()

  // header first
  mbPartitionedHeaderBlock.foreach { phb =>
    // println(s">>> phb: ${phb}")
    w.writeln( phb.otherHeaderText )
    lastIndentSpaces = phb.otherLastIndent
  }

  var textBlockCount = 0
  td3.nonheaderBlocks.foreach { block =>
    // println(s"Processing block: ${block}")
    block match
      case cblock : ParseBlock.Code =>
        // println(s">>> cblock: ${cblock}")
        w.writeln(0)(cblock.text)
        lastIndentSpaces = cblock.lastIndent
      case tblock : ParseBlock.Text =>
        val argList = s"( ${inputVarName}, scratchpad )"
        val functionExpression = tblock.functionIdentifier.getOrElse(s"BlockPrinters(${textBlockCount})")
        w.writeln(lastIndentLevel + 1)(s"writer.write(${functionExpression}${argList})${LineSep}")
        textBlockCount += 1
  }
  w.writeln(0)("writer.toString")
  w.toString

private def defaultTranspile( pkg : List[Identifier], generatorName : Identifier, generatorExtras : GeneratorExtras, src : GeneratorSource ) : GeneratorScala =
  val w = new StringWriter(K100) // XXX: hardcoded initial buffer length, should we examine src?
  transpileToWriter(pkg, generatorName, generatorExtras, src, w)
  toGeneratorScala(w.toString)

