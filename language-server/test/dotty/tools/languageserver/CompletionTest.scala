package dotty.tools.languageserver

import org.junit.Test

import dotty.tools.languageserver.util.Code._

class CompletionTest {

  @Test def completion0: Unit = {
    code"class Foo { val xyz: Int = 0; def y: Int = xy$m1 }".withSource
      .completion(m1, List(("xyz", "Field", "Int")))
  }
}
