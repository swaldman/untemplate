package untemplate

object Macro:
  import scala.quoted.*

// with thanks to Dmytro Mitin

  inline def nonEmptyStringOption( inline s : String ) =
    if s == null || s.isEmpty then None else Some(s)

  // https://stackoverflow.com/questions/75051483/providing-the-equivalent-of-a-type-parameter-t-from-inside-a-scala-3-macro/75052126#75052126
  inline def recursiveCanonicalName[T]: String = ${ Macro.recursiveCanonicalNameImpl[T] }

  private def unliteral(s: String) = s.stripPrefix("\"").stripSuffix("\"")

  private def recursiveCanonicalNameImpl[T](using q: Quotes)(using tt: Type[T]): Expr[String] =
    try
      import quotes.reflect.*
      val repr = TypeRepr.of[T]
      repr.widenTermRefByName.dealias match
        case AppliedType(name, args) =>
          val typeParams = args.map { a =>
            a.asType match
              case '[a] =>
                unliteral(recursiveCanonicalNameImpl[a].show)
          }
          Expr(name.dealias.simplified.show + "[" + typeParams.mkString(",") + "]")
        case _ =>
          Expr(repr.dealias.simplified.show)
    catch
      case e : (Exception | AssertionError) =>
        e.printStackTrace()
        Expr("")
