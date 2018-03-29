package dotty.tools.languageserver

import org.junit.Test
import org.eclipse.lsp4j.SymbolKind

import dotty.tools.languageserver.util.Code._

class SymbolTest {

//  @Test def symbol0: Unit =
//    code"class ${m1}Foo$m2".withSource.symbol("Foo", ("Foo", "Class", m1 to m2))

  @Test def symbol1: Unit = {
    val Foo = (m1 to m2).withCode("Foo")
    val Bar = (m3 to m4).withCode("Bar")
    val fooFoo = (m5 to m6).withCode("Foo")
    withSources(
      code"class $Foo",
      code"class $Bar",
      code"""package foo
            |class $fooFoo {
            |  class Bar
            |}
          """
    ) .symbol("Foo", Foo.range.symInfo("Foo", SymbolKind.Class), fooFoo.range.symInfo("Foo", SymbolKind.Class, "foo"))
      .symbol("Bar", Bar.range.symInfo("Bar", SymbolKind.Class))
  }
}
