package dotty.tools
package languageserver

import com.microsoft.java.debug.core._
import com.microsoft.java.debug.core.adapter._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import java.io.{ InputStream, OutputStream }

import java.net._
import java.nio.channels._

import java.util.logging.Level
import java.util.logging.Logger

object DottyDebugServer {
  def newServer(languageServer: DottyLanguageServer): Int = {
    println("Starting DDS")

    val logger = Logger.getLogger("java-debug")
    logger.setLevel(Level.FINEST)

    val providerContext = {
      val c = new ProviderContext
      c.registerProvider(classOf[ISourceLookUpProvider], new DottySourceLookUpProvider(languageServer))
      c.registerProvider(classOf[IVirtualMachineManagerProvider], new DottyVirtualMachineManagerProvider)
      c
    }

    val serverSocket = new ServerSocket(0)
    Runtime.getRuntime().addShutdownHook(new Thread(
      new Runnable {
        def run(): Unit = {
          serverSocket.close()
        }
      }))

    Future {
      val clientSocket = serverSocket.accept()

      val ps = new ProtocolServer(clientSocket.getInputStream, clientSocket.getOutputStream, providerContext)
      ps.start()
    }

    val port = serverSocket.getLocalPort
    println(s"DDS started on port $port")
    port
  }
}
