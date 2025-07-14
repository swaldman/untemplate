package untemplate

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

case class UntemplateScala( pkg : String, identifier : Identifier, warnings : Vector[UntemplateWarning], text : String ):
  def fullyQualifiedFunctionName =
    if pkg.nonEmpty then pkg + "." + identifier.toString else identifier.toString
