# TODO

* Lint pass
    - mixed tab/space prefixes
    - unescaped block delimiters not first character
    - first-character block delimeters with non-spaces on rest-of-line
* Make scala generation incremental (ie check timestamps to limit regeneration)
* Implement long delimeters, e.g.
  ```
  (chillOnReturnType)>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
  Return type `untemplate.Result[Nothing]` looks intimidating, but it's just a
  fancy wrapper for a `String`, as a field called `text`.
  The `[Nothing]` part just means there cannot be metadata attached to this result.
  <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<()
  ```
  or
  ```
  (chillOnReturnType)--------------------------------------------------------------->
  Return type `untemplate.Result[Nothing]` looks intimidating, but it's just a
  fancy wrapper for a `String`, as a field called `text`.
  The `[Nothing]` part just means there cannot be metadata attached to this result.
  <--------------------------------------------------------------------------------()
  ```
 * Allow for a user-definable function that accepts e.g. identifier name, 
   and metadata type, perhaps information about package (only if default?)
   and chooses a default `OutputTransformer` on that basis (rather than always
   defaulting to identity).
   * Maybe extra imports by the same discriminator?
   * CustomizerKey?
   * Update docs about uselessness of Metainformation...
 * Refactorings
   * don't use Java Streamsy String.lines, use Scala .linesIterator
   * instead of `Generator` (all over the place!), use more straightforward `Untemplate`
 * Let `PackageSource` return a result, whose metadata might include `lastModified`
   to support incremental generation
 * "Feature creep" docs: OutputTransformer, long delimeters
 * Trait-ify `Result`, so we can have SimpleResult and LazyResult 
   (for files that needn't be read)
   * Don't forget to update the docs
   * ```scala
     class LazyResult[+A]( val mbMetadata : Option[+A], genText : =>String ):
       lazy val text = genText
       override def toString : String = text
     ```