# TODO

 * Global index by unique suffix!
 * Support a `local import` syntax in headers?
 * InputTransformers
   * `whatever.untemplate.xxx` calls `xxx(input: String): String` at start of transpilation
   * parallelize transpilations. should be trivial via ZIO-lib functions.
 * Documentation: Static site generator generator
 * Can we define a build pipeline that generates untemplates in the JVM
   but makes them available as functions in ScalaJS?
 * Testing 
