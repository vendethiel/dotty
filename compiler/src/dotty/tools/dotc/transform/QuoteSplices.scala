package dotty.tools.dotc.transform

import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.ast.Trees._
import dotty.tools.dotc.core.Constants._
import dotty.tools.dotc.core.Contexts._
import dotty.tools.dotc.core.Symbols._
import dotty.tools.dotc.transform.MegaPhase.MiniPhase

/** TODO
 */
class QuoteSplices extends MiniPhase {
  import tpd._

  override def phaseName: String = "quoteSplices"

  override def transformApply(tree: tpd.Apply)(implicit ctx: Context): tpd.Tree = {
    if (tree.symbol ne defn.MetaQuote) tree
    else {
      val quoteTransform = new QuoteTransform
      val quote = quoteTransform.transform(tree)
      quoteTransform.splices.foldLeft(quote) { (acc, splice) =>
        acc.select(defn.MetaExprSpliced).appliedTo(splice)
      }
    }
  }

  private class QuoteTransform extends TreeMap {
    private[this] var id = -1
    var splices: List[Tree] = Nil
    override def transform(tree: tpd.Tree)(implicit ctx: Context): tpd.Tree = tree match {
      case Apply(Select(splicedCode, _), _) if tree.symbol eq defn.MetaExprSplice =>
        id += 1
        splices = splicedCode :: splices
        ref(defn.MetaSpliceHole).appliedToType(tree.tpe.widen)
      case _ => super.transform(tree)
    }
  }
}
