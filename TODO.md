# TODO

* Lint pass
    - mixed tab/space prefixes
    - unescaped block delimiters not first character
    - first-character block delimeters with non-spaces on rest-of-line
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
 * Documentation: "Feature creep" docs: Customizers, OutputTransformer, long delimeters
 * Template indexing
 * Documentation: Static site generator generator
 * Documentation: Mill quickstart


