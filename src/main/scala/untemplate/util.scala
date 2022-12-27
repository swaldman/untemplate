package untemplate

class UntemplateException(msg : String, cause : Throwable = null) extends Exception(msg, cause)
class NonuniqueIdentifier(msg : String, cause : Throwable = null) extends UntemplateException(msg, cause)
class BadIdentifierSource(msg : String, cause : Throwable = null) extends UntemplateException(msg, cause)
class ParseException(msg : String, cause : Throwable = null) extends UntemplateException(msg, cause)

opaque type Identifier = String

def toIdentifier( unrestrictedName : String ) : Identifier =
  val transformed =
    unrestrictedName.map {
      case '.' | '-'                              => '_'
      case c if Character.isJavaIdentifierPart(c) => c
      case c                                      =>
        throw new BadIdentifierSource(s"Cannot convert identifier '${unrestrictedName} to identifier, contains bad character '${c}'.")
    }
  val c = transformed(0)
  if !Character.isJavaIdentifierStart(c) then
    throw new BadIdentifierSource(s"Bad initial character for identifier: '${c}' in '${transformed}'.'")
  else
    transformed

