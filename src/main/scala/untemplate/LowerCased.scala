package untemplate

import scala.collection.{immutable,mutable}

object LowerCased:
  type Map = immutable.Map[LowerCased,Any]

  def attributesFrom( ut : Untemplate.AnyUntemplate ) : LowerCased.Map =
    val raw = ut.UntemplateAttributes
    val tupVec =
      raw.toVector.map { case (k,v) => (this.apply(k),v) }
    val dups = tupVec.groupBy( _(0) ).filter( _(1).size > 1 )
    if dups.nonEmpty then
      // XXX: We'd like to standardize on scribe logging, but it's incompatible
      //      with the mixed 2.13/3.2.x mill project we still need
      //scribe.warn(
      System.err.println (
        "WARNING: " +
        s"Attributes map for untemplate '${ut.UntemplateFullyQualifiedName}' loses information when made case insensitive. Duplicate keys: " + dups.mkString(", ")
      )
    tupVec.toMap
  end attributesFrom
  def apply( s : String ) : LowerCased = s.toLowerCase
opaque type LowerCased <: String = String


