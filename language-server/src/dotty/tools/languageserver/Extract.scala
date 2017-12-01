package dotty.tools
package languageserver

import scala.collection.mutable

import dotc.ast.{TreeTypeMap, Trees, tpd}
import dotc.core._, dotc.core.Decorators._
import dotc.transform.{ CtxLazy, MacroTransform }
import Phases.Phase
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

  private val GlobalClassRef = new CtxLazy(implicit ctx =>
    ctx.requiredClassRef("dbg.Global"))
  def GlobalClass(implicit ctx: Context) = GlobalClassRef().symbol.asClass

  private val immMapType = new CtxLazy(implicit ctx =>
    ctx.requiredClassRef("scala.collection.immutable.Map"))

  class ExtractTransformer extends Transformer {
    var expr: tpd.Block = _
    var exprOwner: Symbol = _
    var defs: List[DefDef] = Nil

    override def transform(tree: Tree)(implicit ctx: Context): Tree = tree match {
      case tree: TypeDef if tree.symbol == GlobalClass =>
        // Skip
        tree
      case _ =>
        if (tree.pos.exists && exprPos.contains(tree.pos)) {
          tree match {
            case tree: DefDef =>
              defs = tree :: defs
            case tree: Block =>
              // FIXME: Somehow this triggers on TypeComparer:172, expr is duplicated?
              assert(expr == null, s"Old: $expr\nNew: $tree")
              expr = tree
              exprOwner = ctx.owner
          }
          tree
        }
        else {
          super.transform(tree)
        }
    }
  }

  class GlobalTransformer(expr: tpd.Block, exprOwner: Symbol, defs: List[DefDef]) extends Transformer {
    def collectParams(tree: Tree)(implicit ctx: Context) = {
      val buf = new mutable.ListBuffer[Ident]

      (new TreeTraverser {
        override def traverse(tree: Tree)(implicit ctx: Context) = {
          tree match {
            case ident: Ident if ident.tpe.asInstanceOf[NamedType].prefix == NoPrefix && !exprPos.contains(ident.symbol.pos) =>
              buf += ident
            case _ =>
          }
          traverseChildren(tree)
        }
      }).traverse(tree)

      buf.toList
    }

    def liftedSym(name: TermName, resultType: Type, origParams: List[NameTree], origClass: ClassSymbol)(implicit ctx: Context) = {
      val paramNames = nme.SELF :: origParams.map(_.name.asTermName)
      val paramInfos = origClass.typeRef :: origParams.map(_.tpe.widen)
      val liftedType = MethodType.apply(paramNames)(mt => paramInfos, mt => resultType)

      ctx.newSymbol(GlobalClass, name, Method | Synthetic, liftedType)
    }

    def desugarIdent(i: Ident)(implicit ctx: Context): Tree =
      i.tpe match {
        case TermRef(prefix: TermRef, name) =>
          tpd.ref(prefix).select(i.symbol)
        case TermRef(prefix: ThisType, name) =>
          tpd.This(prefix.cls).select(i.symbol)
        case _ =>
          i
      }

    def lifted(newSym: TermSymbol, resultType: Type, origParams: List[NameTree], origClass: ClassSymbol,
      origOwner: Symbol, origBody: Tree, oldSyms: List[Symbol], newSyms: List[Symbol])(implicit ctx: Context): Tree = {
      val symMap = (oldSyms, newSyms).zipped.toMap
      def rewiredTarget(referenced: Symbol)(implicit ctx: Context): Symbol =
        symMap.getOrElse(referenced, NoSymbol)

      polyDefDef(newSym, trefs => vrefss => {
        val thisRef :: argRefs = vrefss.flatten

        def rewireTree0(tree: Tree, targs: List[Tree])(implicit ctx: Context): Tree = {
          def rewireCall(thisArg: Tree, otherArgs: List[Tree]): Tree = {
            val rewired = rewiredTarget(tree.symbol)
            if (rewired.exists) {
              ref(rewired.termRef)
                .appliedTo(thisArg, otherArgs: _*)
            } else EmptyTree
          }
          tree match {
            case Apply(Ident(_), args) => rewireCall(thisRef, args)
            case Apply(Select(qual, _), args) => rewireCall(qual, args)
            case Ident(_) => rewireCall(thisRef, Nil)
            case Select(qual, _) => rewireCall(qual, Nil)
            case _ => EmptyTree
          }
        }

        def rewireTree(tree: Tree, targs: List[Tree])(implicit ctx: Context): Tree = {
          tree match {
            case Apply(i @ Ident(_), args) => rewireTree0(cpy.Apply(tree)(desugarIdent(i), args), targs)
            case i @ Ident(_) => rewireTree0(desugarIdent(i), targs)
            case _ => rewireTree0(tree, targs)
          }
        }

        def rewireType(tpe: Type) = tpe match {
          case tpe: TermRef if rewiredTarget(tpe.symbol).exists => tpe.widen
          case _ => tpe
        }

        new TreeTypeMap(
          typeMap = rewireType(_)
            .subst(origParams.map(_.symbol), argRefs.map(_.tpe))
            .substThisUnlessStatic(origClass, thisRef.tpe)
            ,
          treeMap = {
            case tree: This if tree.symbol == origClass => thisRef
            case tree =>
              rewireTree(tree, Nil) orElse tree
          },
          oldOwners = exprOwner :: Nil,
          newOwners = newSym :: Nil
        ).transform(origBody)
      })
    }


    override def transform(tree: Tree)(implicit ctx: Context): Tree = {
      tree match {
        case tree: TypeDef if tree.symbol != GlobalClass =>
          EmptyTree
        case tree: Template if tree.symbol.owner == GlobalClass =>
          val origParams = collectParams(expr)
          val origClass = exprOwner.enclosingClass.asClass

          val liftedExprSym =
            liftedSym("liftedExpr".toTermName, defn.UnitType, origParams, origClass)
          val liftedDefsSyms = defs.map(d =>
            liftedSym(d.name, d.tpt.tpe, collectParams(d.rhs) ++ d.vparamss.flatten, origClass))

          val oldSyms = exprOwner :: defs.map(_.symbol)
          val newSyms = liftedExprSym :: liftedDefsSyms

          val liftedExpr = lifted(liftedExprSym, defn.UnitType, origParams, origClass, exprOwner, expr, oldSyms, newSyms)
          val liftedDefs = (defs, liftedDefsSyms).zipped.map((d, sym) =>
            lifted(sym, d.tpt.tpe, collectParams(d.rhs) ++ d.vparamss.flatten, origClass, d.symbol.owner, d.rhs, oldSyms, newSyms))

          val execParamNames = List("self", "names", "args").map(_.toTermName)
          val execParamTypes = List(defn.ObjectType, JavaArrayType(defn.StringType), JavaArrayType(defn.ObjectType))
          val execDefSym = ctx.newSymbol(GlobalClass, "exec".toTermName, Method | Synthetic,
            MethodType(execParamNames)(mt => execParamTypes, mt => defn.UnitType))
          val execDef = polyDefDef(execDefSym, trefs => vrefss => {
            val List(selfRef, namesRef, argsRef) = vrefss.flatten
            val mapSym = ctx.newSymbol(execDefSym, "map".toTermName, Synthetic, immMapType())
            val localArgs = origParams.map(param =>
              Ident(mapSym.termRef).select(nme.apply)
                .appliedTo(Literal(Constant(param.name.mangledString)))
                .asInstance(param.tpe.widen))

            Block(
              List(
                ValDef(mapSym, This(GlobalClass).select("makeMap".toTermName).appliedTo(namesRef, argsRef)),
                ref(liftedExprSym).appliedTo(selfRef.asInstance(origClass.typeRef), localArgs: _*)
              ),
              Literal(Constant(()))
            )
            // ref(liftedExprSym).applied
          })

          val allDefs = execDef :: liftedExpr :: liftedDefs

          val cinfo = GlobalClass.classInfo
          val newDecls = cinfo.decls.cloneScope
          allDefs.foreach(d => newDecls.enter(d.symbol))


          GlobalClass.copySymDenotation(
            info = cinfo.derivedClassInfo(
              decls = newDecls)).installAfter(thisTransformer)

          cpy.Template(tree)(body = tree.body ++ allDefs)
        case _ =>
          super.transform(tree)
      }
    }
  }
}
