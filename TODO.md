# TODO

* Document/test long delimeters + comments, e.g.
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
 * Documentation: Static site generator generator
 * Documentation: Mill Quickstart
 * Documentation: Cheatsheet
 * Testing 
