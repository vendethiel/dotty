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
      val sourceLookup = new DottySourceLookUpProvider(languageServer)
      val vmManager = new DottyVirtualMachineManagerProvider
      c.registerProvider(classOf[ISourceLookUpProvider],
        sourceLookup)
      c.registerProvider(classOf[IVirtualMachineManagerProvider],
        vmManager)
      c.registerProvider(classOf[IEvaluationProvider],
        new DottyEvaluationProvider(languageServer, sourceLookup, vmManager.getVirtualMachineManager))
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
      ps.run()
    }

    val port = serverSocket.getLocalPort
    println(s"DDS started on port $port")
    port
  }
}
