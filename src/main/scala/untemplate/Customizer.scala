package untemplate

object Customizer:
  val Skip  : Customizer = Customizer.apply()
  val empty : Customizer = Skip
  type Selector = Key => Customizer
  val Never : Selector = ( _ : Key ) => Skip
  final case class Key(inferredPackage : Option[String], resolvedPackage : Option[String], inferredFunctionName : String, resolvedFunctionName : String, outputMetadataType : String)
  final case class InputTypeDefaultArg(inputType : String, mbDefaultArg : Option[String])

import Customizer.InputTypeDefaultArg

final case class Customizer (
  mbOverrideInferredFunctionName : Option[String]              = None,
  mbDefaultInputName             : Option[String]              = None,
  mbDefaultInputTypeDefaultArg   : Option[InputTypeDefaultArg] = None,
  mbOverrideInferredPackage      : Option[String]              = None, // You can use empty String to override inferred package to the default package
  mbDefaultMetadataType          : Option[String]              = None,
  mbDefaultMetadataValue         : Option[String]              = None, // Omit Some(...), we'll generate that
  mbDefaultOutputTransformer     : Option[String]              = None,
  extraImports                   : Vector[String]              = Vector.empty
)
