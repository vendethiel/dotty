package dotty.tools.languageserver.util.embedded

import dotty.tools.languageserver.util.server.TestFile
import dotty.tools.languageserver.util.{CodeRange, PositionContext}

import org.eclipse.lsp4j._

import PositionContext._

/** Used to mark positions in the code */
class CodeMarker(val name: String) extends Embedded {

  /** A range of positions between this marker and `other`. */
  def to(other: CodeMarker): CodeRange = CodeRange(this, other)

  /** The file containing this marker. */
  def file: PosCtx[TestFile] = implicitly[PositionContext].positionOf(this)._1

  /** The line containing this marker. */
  def line: PosCtx[Int] = implicitly[PositionContext].positionOf(this)._2

  /** The columng number of this marker. */
  def character: PosCtx[Int] = implicitly[PositionContext].positionOf(this)._3

  /** Converts this marker to a position. */
  def toPosition: PosCtx[Position] = new Position(line, character)

  def toTextDocumentPositionParams: PosCtx[TextDocumentPositionParams] =
    new TextDocumentPositionParams(toTextDocumentIdentifier, toPosition)

  def toDocumentSymbolParams: PosCtx[DocumentSymbolParams] =
    new DocumentSymbolParams(toTextDocumentIdentifier)

  def toRenameParams(newName: String): PosCtx[RenameParams] =
    new RenameParams(toTextDocumentIdentifier, toPosition, newName)

  def toTextDocumentIdentifier: PosCtx[TextDocumentIdentifier] =
    new TextDocumentIdentifier(file.uri)

  def toReferenceParams(withDecl: Boolean): PosCtx[ReferenceParams] = {
    val rp = new ReferenceParams(new ReferenceContext(withDecl))
    rp.setTextDocument(toTextDocumentIdentifier)
    rp.setPosition(toPosition)
    rp
  }

  def show: PosCtx[String] = s"($name,line=$line,char=$character)"
  override def toString: String = s"CodePosition($name)"
}
