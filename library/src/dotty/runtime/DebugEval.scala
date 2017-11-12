package dotty.runtime

import java.nio.file.{Files, Paths}
import java.net._

object DebugEval {
  // private val unsafe = scala.concurrent.util.Unsafe.instance

  def eval(path: String, self: Object, names: Array[String], args: Array[Object]): Unit = {
    // val bytes = Files.readAllBytes(Paths.get(path))
    // val cls = unsafe.defineAnonymousClass(this.getClass, bytes, null)

    val cl = new URLClassLoader(Array(new URL("file://" + path)))
    val cls = cl.loadClass("dbg.Global")
    val instance = cls.newInstance

    val exec = cls.getMethod("exec", classOf[Object], classOf[Array[String]], classOf[Array[Object]])
    exec.invoke(instance, self, names, args)
  }
}
