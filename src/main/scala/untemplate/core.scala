package untemplate

import scala.collection.*
import scala.util.matching.Regex.Match
import com.mchange.codegenutil

private val Suffix = "untemplate"
private val DotSuffix = "." + Suffix
private val DotSuffixLen = DotSuffix.length

final case class Result[+A](mbMetadata : Option[A], text : String ):
  override def toString() : String = text

opaque type UntemplateSource  = Vector[String]
opaque type UntemplateWarning = String

def toUntemplateWarning( s : String ) : UntemplateWarning = s

object UntemplateScala:
  def fromScalaText( text : String ) : UntemplateScala =
    val (pkg, mbId) =
      text.linesIterator.foldLeft( Tuple2( "", None : Option[Identifier] ) ) { (accum, nextLine) =>
        nextLine match
          case PackageExtractFromLineRegex(pkgName) =>
            if accum(0).isEmpty then Tuple2(pkgName, accum(1))
            else throw new ParseException(s"Expected at most one package declaration from generated Scala source. Second package declared: ${pkgName}")
          case UntemplateIdentifierExtractFromLineRegex(untemplateName) =>
            if (accum(1)).isEmpty then Tuple2(accum(0), Some(asIdentifier(untemplateName)))
            else throw new ParseException(s"Expected at most one Untemplate val declaration in generated Scala source. Second declaration: ${untemplateName}")
          case _ => accum
    }
    mbId match
      case Some(id) => UntemplateScala( pkg, id, Vector.empty, text )
      case None     => throw new ParseException(s"Could not extract untemplate identifier from text.")

case class UntemplateScala( pkg : String, identifier : Identifier, warnings : Vector[UntemplateWarning], text : String )

type Transpiler           = Function5[LocationPackage, Identifier, Customizer.Selector, UntemplateSource, Option[String], UntemplateScala]
type BlockPrinter         = Function0[String]
type OutputTransformer[A] = Function1[Result[A],Result[A]]

val DefaultTranspiler : Transpiler = defaultTranspile

// these are just examples
// val HeaderDelimeter    = "()[]~()>"

// val TextStartDelimeter = "()>"
// val TextEndDelimeter   = "<()"

// val EmbeddedExpressionDelimiter = "<()>"

case class UntemplateSourceMetadata( mbLastModMetaOption : Option[Long] )

private def asUntemplateSource(vs : Vector[String]) : UntemplateSource = vs

extension (ts : UntemplateSource )
  def lines : Vector[String] = ts
  def textLen : Int = lines.foldLeft(0)( (accum, next) => accum + next.length ) + lines.length * codegenutil.LineSep.length


