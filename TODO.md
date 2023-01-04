# TODO

* Lint pass
    - mixed tab/space prefixes
    - unescaped block delimiters not first character
    - first-character block delimeters with non-spaces on rest-of-line
* Make scala generation incremental (ie check timestamps to limit regeneration)
* Implement long forms, e.g.
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