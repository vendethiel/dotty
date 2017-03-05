# Run 1, compile Foo.scala:
- frontend: object Foo
- pickler: object Foo
- backend output: Foo$.class, Foo.class

# Run 2, compile Foo.scala:
- frontend: object Foo
- XXX: If we force Foo$, get it from previous run!

