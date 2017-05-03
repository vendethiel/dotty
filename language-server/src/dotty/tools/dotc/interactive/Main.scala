package dotty.tools
package dotc
package interactive

import java.util.function.Consumer

import java.io.{ InputStream, OutputStream }
import java.net._

import org.eclipse.lsp4j._
import org.eclipse.lsp4j.services._
import org.eclipse.lsp4j.launch._

object Main {
  def main(args: Array[String]): Unit = {
    val (in: InputStream, out: OutputStream) = args.toList match {
      case List("-port", port) =>
        val serverSocket = new ServerSocket(port.toInt)
        Runtime.getRuntime().addShutdownHook(new Thread(
          new Runnable {
            def run: Unit = {
              serverSocket.close()
            }
          }));

        val clientSocket = serverSocket.accept()
        (clientSocket.getInputStream, clientSocket.getOutputStream)
      case List("-stdio") =>
        (System.in, System.out)
      case _ =>
        Console.err.println("Invalid arguments: expected \"-stdio\" or \"-port NNNN\"")
        System.exit(1)
    }

    val server = new ScalaLanguageServer

    System.setOut(System.err)
    scala.Console.withOut(scala.Console.err) {
      println("hi")
      // val launcher = LSPLauncher.createServerLauncher(server, in, out, false, new PrintWriter(System.err, true))
      val launcher = LSPLauncher.createServerLauncher(server, in, out)
      val client = launcher.getRemoteProxy()
      server.connect(client)
      launcher.startListening()
    }
  }
}
