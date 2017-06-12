package dotty.tools
package languageserver

import dotc.ast.{TreeTypeMap, Trees, tpd}
import dotc.core._, dotc.core.Decorators._
import dotc.transform.{ CtxLazy, MacroTransform }
import dotc.transform.TreeTransforms._, Phases.Phase
import Types._, Contexts._, Constants._, Names._, NameOps._, Flags._, DenotTransformers._
import SymDenotations._, Symbols._, StdNames._, Annotations._, Trees._, Scopes._, Denotations._
import dotc.util.Positions._

class Extract(exprPos: Position) extends MacroTransform with IdentityDenotTransformer { thisTransformer =>
  import tpd._

  def phaseName: String = "extract"

  override def changesMembers = true // the phase adds members to dbg.Global

  override def run(implicit ctx: Context): Unit = {
    val unit = ctx.compilationUnit
    val extractTransformer = new ExtractTransformer
    unit.tpdTree = extractTransformer.transform(unit.tpdTree)(ctx.withPhase(transformPhase))

    println("expr: " + extractTransformer.expr)
    println("defs: " + extractTransformer.defs)

    val globalTransformer = new GlobalTransformer(extractTransformer.expr,
      extractTransformer.exprOwner,
      extractTransformer.defs)
    unit.tpdTree = globalTransformer.transform(unit.tpdTree)(ctx.withPhase(transformPhase))
  }

  override protected def newTransformer(implicit ctx: Context) = ???

  private val GlobalModuleRef = new CtxLazy(implicit ctx =>
    ctx.requiredModuleRef("dbg.Global"))
  def GlobalModule(implicit ctx: Context) = GlobalModuleRef().symbol
  def GlobalClass(implicit ctx: Context) = GlobalModule.moduleClass.asClass

  class ExtractTransformer extends Transformer {
    var expr: tpd.Block = _
    var exprOwner: Symbol = _
    var defs: List[tpd.DefTree] = Nil

    override def transform(tree: Tree)(implicit ctx: Context): Tree = tree match {
      case tree: TypeDef if tree.symbol == GlobalClass =>
        // Skip
        tree
      case _ =>
        if (tree.pos.exists && exprPos.contains(tree.pos)) {
          tree match {
            case tree: DefTree =>
              defs = tree :: defs
            case tree: Block =>
              assert(expr == null)
              expr = tree
              exprOwner = ctx.owner
          }
          tree
        }
        else
          super.transform(tree)
    }
  }

  class GlobalTransformer(expr: tpd.Block, exprOwner: Symbol, defs: List[tpd.DefTree]) extends Transformer {
    def rewiredTarget(referenced: Symbol)(implicit ctx: Context): Symbol =
      NoSymbol // TODO

    override def transform(tree: Tree)(implicit ctx: Context): Tree = {
      tree match {
        case tree: Template if tree.symbol.owner == GlobalClass =>
          val origClass = exprOwner.enclosingClass.asClass

          val liftedType = MethodType(List(nme.SELF))(
            mt => List(origClass.typeRef),
            mt => defn.UnitType
          )
          val liftedSym = ctx.newSymbol(GlobalClass, "liftedExpr".toTermName,
            Method | Synthetic,
            liftedType)

          val liftedDef = polyDefDef(liftedSym, trefs => vrefss => {
            val thisRef :: _ = vrefss.flatten
            /** If tree should be rewired, the rewired tree, otherwise EmptyTree.
              *  @param   targs  Any type arguments passed to the rewired tree.
              */
            def rewireTree(tree: Tree, targs: List[Tree])(implicit ctx: Context): Tree = {
              def rewireCall(thisArg: Tree): Tree = {
                val rewired = rewiredTarget(tree.symbol)
                if (rewired.exists) {
                  val base = thisArg.tpe//.baseTypeWithArgs(origClass)
                  assert(base.exists)
                  ref(rewired.termRef)
                    .appliedTo(thisArg)
                } else EmptyTree
              }
              tree match {
                case Ident(_) => rewireCall(thisRef)
                case Select(qual, _) => rewireCall(qual)
                case _ => EmptyTree
              }
            }

            def rewireType(tpe: Type) = tpe match {
              case tpe: TermRef if rewiredTarget(tpe.symbol).exists => tpe.widen
              case _ => tpe
            }

            new TreeTypeMap(
              typeMap = rewireType(_)
                // .subst(origVParams, argRefs.map(_.tpe))
                // .substThisUnlessStatic(origClass, thisRef.tpe)
                ,
              treeMap = {
                case tree: This if tree.symbol == origClass => thisRef
                case tree =>
                  rewireTree(tree, Nil) orElse tree
              },
              oldOwners = exprOwner :: Nil,
              newOwners = liftedSym :: Nil
            ).transform(expr)
          })
          val cinfo = GlobalClass.classInfo
          val newDecls = cinfo.decls.cloneScope
          newDecls.enter(liftedSym)


          GlobalClass.copySymDenotation(
            info = cinfo.derivedClassInfo(
              decls = newDecls)).installAfter(thisTransformer)

          cpy.Template(tree)(body = tree.body ++
            List(liftedDef))
        case _ =>
          super.transform(tree)
      }
    }
  }
}
