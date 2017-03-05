/* NSC -- new Scala compiler
 * Copyright 2002-2013 LAMP/EPFL
 * @author  Martin Odersky
 */

package dotty.tools
package dotc
package util

/** This object provides utility methods to extract elements
 *  from Strings.
 *
 *  This was adapted from https://github.com/scala/scala/blob/2.11.x/src/reflect/scala/reflect/internal/util/StringOps.scala
 */
object StringOps {
  def splitWhere(str: String, f: Char => Boolean, doDropIndex: Boolean = false): Option[(String, String)] =
    splitAt(str, str indexWhere f, doDropIndex)

  def splitAt(str: String, idx: Int, doDropIndex: Boolean = false): Option[(String, String)] =
    if (idx == -1) None
    else Some((str take idx, str drop (if (doDropIndex) idx + 1 else idx)))
}
