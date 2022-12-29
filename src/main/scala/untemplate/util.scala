package untemplate

import scala.collection.*

type Scratchpad = mutable.Map[String,Any]

def newScratchpad() : Scratchpad = mutable.Map.empty[String,Any]


