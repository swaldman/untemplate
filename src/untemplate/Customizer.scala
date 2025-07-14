package untemplate

object Customizer:
  val empty : Customizer = Customizer()
  type Selector = Key => Customizer
  val NeverCustomize : Selector = ( _ : Key ) => empty

  final case class Key(inferredPackage      : String, // empty string is the default package
                       resolvedPackage      : String, // empty string is the default package
                       inferredFunctionName : String,
                       resolvedFunctionName : String,
                       outputMetadataType   : String,
                       headerNote           : String,
                       sourceIdentifier     : Option[String])

  final case class InputTypeDefaultArg(inputType : String, mbDefaultArg : Option[String])

import Customizer.InputTypeDefaultArg

final case class Customizer(mbOverrideInferredFunctionName : Option[String]              = None,
                            mbDefaultInputName             : Option[String]              = None,
                            mbDefaultInputTypeDefaultArg   : Option[InputTypeDefaultArg] = None,
                            mbOverrideInferredPackage      : Option[String]              = None, // You can use empty String to override inferred package to the default package
                            mbDefaultMetadataType          : Option[String]              = None,
                            mbDefaultMetadataValue         : Option[String]              = None, // Omit Some(...), we'll generate that
                            mbDefaultOutputTransformer     : Option[String]              = None,
                            extraImports                   : Seq[String]                 = Nil)
