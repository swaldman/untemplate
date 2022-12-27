package untemplate

import scala.collection.*

private case class TranspileData1(source : GeneratorSource, spaceNormalized : Vector[String], indentLevels : Vector[Int])

private case class TextBlockInfo(functionName : Option[String], startDelimeter : Option[Int], stopDelimeter : Option[Int])
private case class BasicParseData(metadataType : Option[String], textBlockInfos : List[TextBlockInfo])

private case class TranspileData2(last : TranspileData1, parseData : BasicParseData)


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

private val TabLength = 2

private def untabAndCountSpaces( ts : GeneratorSource ) : TranspileData1 =
  val indents = Array[Int](ts.lines.length)
  val oldLines = ts.lines
  val newLines = mutable.Buffer.empty[String]
  for( i <- 0 until ts.lines.length )
    val oldLine = oldLines(i)
    val untabbed = prefixTabSpaceToSpaces(TabLength, oldLine)
    val indent = untabbed.takeWhile(_ == ' ').length
    newLines.append(untabbed)
    indents(i) = indent
  TranspileData1(ts, newLines.toVector, indents.toVector)

private object LineDelimeter:
  object Header:
    def apply(str: String) : Header = if str == null || str.length == 0 then Header(None) else Header(Some(str))
  case class Header(metadataType : Option[String]) extends LineDelimeter
  object Start:
    def apply(str : String) : Start = if str == null || str.length == 0 then Start(None) else Start(Some(str))
  case class Start(functionName : Option[String]) extends LineDelimeter
  case object End extends LineDelimeter
private sealed trait LineDelimeter;

private def basicParse( td1 : TranspileData1 ) : TranspileData2 =
  var headerTuple : Option[Tuple2[Int,LineDelimeter.Header]] = None
  val parseTuples : mutable.SortedMap[Int,LineDelimeter] = mutable.SortedMap.empty

  for( i <- 0 until td1.indentLevels.length)
    if td1.indentLevels(i) == 0 then
      td1.source.lines(i) match {
        case HeaderDelimeterRegex(metadataType, functionName) =>
          if headerTuple == None then
            headerTuple = Some(Tuple2(i, LineDelimeter.Header(metadataType)))
            parseTuples += Tuple2(i, LineDelimeter.Start(functionName))
          else
            throw new ParseException(s"Duplicate header delimeter at line ${i}")
        case TextStartDelimiterRegex(functionName) =>
          parseTuples += Tuple2(i, LineDelimeter.Start(functionName))
        case TextEndDelimeterRegex() =>
          parseTuples += Tuple2(i, LineDelimeter.End)
      }

  // sanity checks
  headerTuple.foreach { case (line, ldh) =>
    if line != parseTuples.keys.head then
      throw new ParseException(
        s"The first start tuple is at line ${parseTuples.keys.head}, but header boundary is at ${line}, should be identical." +
        "Perhaps there is a start delimeter above the header delimeter. That would be bad!"
      )
  }

  var lastSeen : LineDelimeter = null; // so sue me... purely an internal implementation detail
  parseTuples.foreach { case (i, delim) =>
    if lastSeen == null then
      lastSeen = delim
    else
      (lastSeen, delim) match {
        case (a : LineDelimeter.Start, b : LineDelimeter.End.type)         => /* Good */
        case (a : LineDelimeter.End.type, b : LineDelimeter.Start)         => /* Good */
        case (a : LineDelimeter.Start, b : LineDelimeter.Start)            =>
          throw new ParseException(s"Line ${i}: Text region start requested within already started text region. Please escape untemplate delimeters in text.")
        case (a : LineDelimeter.End.type, b : LineDelimeter.End.type)       =>
          throw new ParseException(s"Line ${i}: Text region end requested within Scala code region.")
        case (_ : LineDelimeter.Header, _) | (_, _ : LineDelimeter.Header) =>
          throw new AssertionError(s"Line ${i}: There should be no LineDelimeter.Header in parseTuples!")
      }
  }

  // okay... apparently we have alternating sections. Let's build our output
  val metadataType : Option[String] = headerTuple.flatMap { case (i, ldHeader) => ldHeader.metadataType }

  if parseTuples.nonEmpty then
    val textBlockInfos : mutable.Buffer[TextBlockInfo] = mutable.Buffer.empty

    val headTuple = parseTuples.head
    val (groupTuples, prependHead) =
      headTuple match {
        case (_, _ : LineDelimeter.Start)    => Tuple2(parseTuples, false)
        case (_, _ : LineDelimeter.End.type) => Tuple2(parseTuples.tail, true)
        case (_, _) =>
          throw new AssertionError("There should be no LineDelimter.Header values in parseTuples.")
      }
    if prependHead then
      textBlockInfos.append(TextBlockInfo(None,None,Some(headTuple._1)))
    groupTuples.grouped(2).foreach { minimap =>
      val l = minimap.toList
      val (start, functionName) =
        l.head match {
          case (s, LineDelimeter.Start(fname)) => (s, fname)
          case (a,b) =>
            throw new AssertionError(s"Expected tuple of (Int,LineDelimeter.Start), found (${a},${b})")
        }
      val end =
        if (l.tail.nonEmpty) Some(l.tail.head._1)
        else None
      textBlockInfos.append(TextBlockInfo(functionName,Some(start),end))
    }
    TranspileData2( td1, BasicParseData(metadataType, textBlockInfos.toList) )
  else
    TranspileData2( td1, BasicParseData(None, Nil) )



