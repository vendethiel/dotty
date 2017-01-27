package dotty.tools
package io

import java.io.{
  Reader,
  StringReader,
  Writer,
  InputStream,
  OutputStream,
  ByteArrayInputStream
}
import java.net.URI

/** An input file */
trait File {
  /** Path of the file, including everything. Unique if possible (used for
   *  lookup).
   */
  def path: String

  /** Name of the file, including extension */
  def name: String = File.nameFromPath(path)

  /** Whether this file exists. Reading a non-existent file may fail */
  def exists: Boolean

  /** Returns true if the file is a directory */
  def isDirectory: Boolean

  /** Returns true if the file is an existing file */
  def isFile: Boolean = !isDirectory && exists

  /** True if the file is not a directory and has the correct extension */
  def hasExtension(ext: String*): Boolean =
    !isDirectory && {
      val pos = name.lastIndexOf('.')
      pos != -1 && ext.contains(name.substring(pos + 1))
    }

  /** Optionally returns an implementation-dependent "version" token.
   *  Versions are compared with `==`. If defined, a different version must
   *  be returned when the content changes. It should be equal if the content
   *  has not changed, but it is not mandatory. Such a token can be used by
   *  caches: the file need not be read and processed again if its version
   *  has not changed.
   */
  def version: Option[String]

  /** URI for this file */
  def toURI: URI

  /** Returns the parent directory of this file */
  def parent: Directory

  override def toString: String = s"File($path)"
}

object File {
  def separator: String = sys.props("file.separator")

  def pathSeparator: String = sys.props("path.separator")

  def nameFromPath(path: String) = {
    val pos = path.lastIndexOf(separator)
    if (pos == -1) path
    else path.substring(pos + 1)
  }

  implicit class PimpJFile(val jfile: JFile) extends AnyVal {
    def /(subPath: String): JFile =
      new JFile(jfile.getAbsolutePath + separator + subPath)
  }
}

trait Directory extends File {
  /** Returns true if the file is a directory */
  def isDirectory: Boolean = true

  /** Lists the subfiles of this directory */
  def listFiles: Array[File]

  def parent: Directory
}

trait TextFile extends File {
  /** Returns the content of the file. */
  def content: String

  /** Returns true if the file is a directory */
  def isDirectory: Boolean = false

  /** Returns a reader for content */
  def reader: Reader = new StringReader(content)

  /** Returns the lines in the content. Lines do not contain the new line
   *  characters.
   */
  def readLines: Iterator[String]
}

trait WritableTextFile extends TextFile {
  def contentWriter: Writer
}

trait BinaryFile extends File {
  /** Returns the content of the file. */
  def content: Array[Byte]

  /** Returns a new InputStream of the file. */
  def inputStream: InputStream = new ByteArrayInputStream(content)
}

trait WritableBinaryFile extends BinaryFile {
  def outputStream: OutputStream
}

