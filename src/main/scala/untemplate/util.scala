package untemplate

import scala.collection.*

type Scratchpad = mutable.Map[String,Any]

extension (s : Scratchpad)
  def as[T](key : String) : T = s(key).asInstanceOf[T]
  def check[T](key : String) : Option[T] = s.get(key).map( _.asInstanceOf[T] )

