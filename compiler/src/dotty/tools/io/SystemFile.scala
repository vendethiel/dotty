package dotty.tools
package io

import java.io.{
  Reader,
  Writer,
  OutputStream,
  FileOutputStream,
  ByteArrayInputStream
}
import java.net.URI
import scala.io.Source

/** Concrete implementation of `WritableBinaryFile` with underlying
 *  `java.io.File`
 */
case class SystemFile(underlying: JFile) extends WritableBinaryFile {
  def path: String = underlying.getAbsolutePath

  def exists: Boolean = underlying.exists

  def isDirectory = underlying.isDirectory

  def content: Array[Byte] = Source.fromFile(underlying).map(_.toByte).toArray

  def outputStream: OutputStream = new FileOutputStream(underlying)

  def version: Option[String] = Some(underlying.lastModified.toString)

  def toURI: URI = underlying.toURI

  def extension: String = name.split("\\.").last

  def parent: SystemDirectory = SystemDirectory(underlying.getParentFile)
}

/** Concrete implementation of `Directory` with underlying `java.io.File` */
case class SystemDirectory(underlying: JFile) extends Directory {

  def path: String = underlying.getAbsolutePath

  override def name: String = underlying.getName

  def exists: Boolean = underlying.exists

  def listFiles: Array[File] = underlying.listFiles().map(SystemFile.apply)

  def toURI: URI = underlying.toURI

  def version: Option[String] = Some(underlying.lastModified.toString)

  def parent: SystemDirectory = SystemDirectory(underlying.getParentFile)

  def /(subPath: String): JFile =
    new JFile(path + File.separator + subPath)
}

