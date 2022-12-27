package untemplate

import scala.collection.*

private case class TranspileData1(source : TransformerSource, spaceNormalized : Vector[String], indentLevels : Vector[Int])

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

private def untabAndCountSpaces( ts : TransformerSource ) : TranspileData1 =
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