package untemplate

opaque type Identifier = String

def untemplateSourceNameToIdentifier( sourceName : String ) : Identifier =
  val noSuffix =
    if sourceName.endsWith(DotSuffix) then
      sourceName.substring(0, sourceName.length - DotSuffixLen)
    else
      sourceName
  toIdentifier(noSuffix)

def toIdentifier( unrestrictedName : String ) : Identifier =
  val transformed =
    unrestrictedName.map {
      case '.' | '-'                              => '_'
      case c if Character.isJavaIdentifierPart(c) => c
      case c                                      =>
        throw new BadIdentifier(s"Cannot convert identifier '${unrestrictedName} to identifier, contains bad character '${c}'.")
    }
  val c = transformed(0)
  if !Character.isJavaIdentifierStart(c) then
    throw new BadIdentifier(s"Bad initial character for identifier: '${c}' in '${transformed}'.'")
  else
    transformed

def asIdentifier( putativeIdentifier : String ) : Identifier =
  if putativeIdentifier.isEmpty then throw new BadIdentifier(s"An empty string can't be an identifier.")
  else if !Character.isJavaIdentifierStart(putativeIdentifier(0)) then
    throw new BadIdentifier(s"Illegal first Char '${putativeIdentifier(0)}' for identifier in ${putativeIdentifier}.")
  else
    val mbFirstBad = putativeIdentifier.find( c => !Character.isJavaIdentifierPart(c) )
    mbFirstBad.foreach( c => throw new BadIdentifier(s"${putativeIdentifier} contains illegal identifier character '${c}'."))
  putativeIdentifier

