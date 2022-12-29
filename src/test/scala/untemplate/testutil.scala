package untemplate

import zio.*

def quickTest[R,E,A](effect : ZIO[Any,E,A]): A =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run[E,A](effect).getOrThrowFiberFailure()
  }


