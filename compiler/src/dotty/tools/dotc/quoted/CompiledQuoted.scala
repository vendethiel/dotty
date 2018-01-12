package dotty.tools.dotc.quoted

import dotty.tools.io.VirtualDirectory
import dotty.tools.repl.AbstractFileClassLoader

/** Quoted `quoted.Quoted` for which its internal representation is the compiled code.
 */
class CompiledQuoted[T](classBytes: Array[Byte], showed: String) extends quoted.Expr[T] {

  def run: T = {
    val outDir = new VirtualDirectory("(memory)", None)
    val out = outDir.fileNamed("'.class").output
    try {
      out.write(classBytes)
      out.flush()
    } finally {
      out.close()
    }

    val classLoader = new AbstractFileClassLoader(outDir, this.getClass.getClassLoader)

    val clazz = classLoader.findClass("'")
    val method = clazz.getMethod("apply")
    val instance = clazz.newInstance()

    method.invoke(instance).asInstanceOf[T]
  }

  def show: String =
    if (showed == null) "<compiled code>"
    else showed

  override def toString: String = s"$CompiledQuoted($show)"
}

object CompiledQuoted {

}
