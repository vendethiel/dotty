// old style import disabling (selective)
package test1 {

import Predef.{any2stringadd => _, StringAdd => _, _}

  object rootImplicits {

    println((new Object()) + "abc")    // error: `+` is not  member of Object

    println(any2stringadd(new Object()) + "abc")  // error: not found: any2stringadd
  }
}

// new style: wholesale
package test2 {

  import Predef.{}
  object rootImplicits {

    println((new Object()) + "abc")    // error: `+` is not  member of Object

    Predef.println(any2stringadd(new Object()) + "abc")  // error: not found: any2stringadd
  }
}

