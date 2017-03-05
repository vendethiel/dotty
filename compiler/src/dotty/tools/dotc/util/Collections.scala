/* NSC -- new Scala compiler
 * Copyright 2005-2013 LAMP/EPFL
 * @author  Paul Phillips
 */

package dotty.tools
package dotc
package util

import scala.collection.mutable.ListBuffer

/** Profiler driven changes.
 *  This was adapted from https://github.com/scala/scala/blob/2.11.x/src/reflect/scala/reflect/internal/util/Collections.scala
 */
object Collections {
  final def mapWithIndex[A, B](xs: List[A])(f: (A, Int) => B): List[B] = {
    val lb = new ListBuffer[B]
    var index = 0
    var ys = xs
    while (!ys.isEmpty) {
      lb += f(ys.head, index)
      ys = ys.tail
      index += 1
    }
    lb.toList
  }

  final def map2[A, B, C](xs1: List[A], xs2: List[B])(f: (A, B) => C): List[C] = {
    val lb = new ListBuffer[C]
    var ys1 = xs1
    var ys2 = xs2
    while (!ys1.isEmpty && !ys2.isEmpty) {
      lb += f(ys1.head, ys2.head)
      ys1 = ys1.tail
      ys2 = ys2.tail
    }
    lb.toList
  }
}
