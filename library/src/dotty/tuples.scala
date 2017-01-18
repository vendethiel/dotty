package dotty

sealed trait Tuple

// TODO OLIVIER: This is to be unified with `Unit`.
final case object TNil extends Tuple

sealed trait TupleCons[+H, +T <: Tuple] extends Tuple {
  def head: H
  def tail: T
}

object TupleCons {
  def apply[H, T <: Tuple](h: H, t: T): TupleCons[H, T] =
    (t match {
      case impln if impln.isInstanceOf[TupleImplN[_, _]] =>
        val underlying = impln.asInstanceOf[TupleImplN[_, _]].underlying
        var s = underlying.size
        val a = new Array[Any](s + 1)
        a(0) = h
        while (s != 0) {
          a(s) = underlying(s - 1)
          s = s - 1
        }
        TupleImplN(a)
      case TNil                       => new TupleImpl1(h)
      case TupleImpl1(e1)             => new TupleImpl2(h, e1)
      case TupleImpl2(e1, e2)         => new TupleImpl3(h, e1, e2)
      case TupleImpl3(e1, e2, e3)     => new TupleImpl4(h, e1, e2, e3)
      case TupleImpl4(e1, e2, e3, e4) =>
        val a = new Array[Any](5)
        a(0) = h
        a(1) = e1
        a(2) = e2
        a(3) = e3
        a(4) = e4
        TupleImplN(a)
    }).asInstanceOf[TupleCons[H, T]]

  def unapply[H, T <: Tuple](t: TupleCons[H, T]): Option[TupleImpl2[H, T]] = Some(new TupleImpl2(t.head, t.tail))
}

case class TupleImplN[H, T <: Tuple](underlying: Array[Any]) extends TupleCons[H, T] {
  def head: H = underlying(0).asInstanceOf[H]
  def tail: T = {
    var s = underlying.size
    if (s == 5) {
      new TupleImpl4(underlying(1), underlying(2), underlying(3), underlying(4)).asInstanceOf[T]
    } else {
      s = s - 1
      val a = new Array[Any](s)
      while (s != 0) {
        a(s - 1) = underlying(s)
        s = s - 1
      }
      TupleImplN(a).asInstanceOf[T]
    }
  }
  override def toString: String = underlying.mkString("(", ", ", ")")
}

object TupleImplN {
  def wrap[H, T <: Tuple](seq: Seq[Any]): TupleImplN[H, T] =
    TupleImplN(seq.toArray)
}

object TupleUnapplySeq {
  def unapplySeq[H, T <: Tuple](tuple: TupleCons[H, T]): Option[Seq[Any]] =
    Some(tuple.asInstanceOf[TupleImplN[H, T]].underlying)
}

class TupleImpl1[+T1](_1: T1) extends TupleCons[T1, TNil.type] {
  def head: T1 = _1
  def tail: TNil.type = TNil
  override def toString: String = s"(${_1})"
  def isEmpty = false
  def get     = this
}
object TupleImpl1 {
  def apply[T1](_1: T1): TupleImpl1[T1] =
    new TupleImpl1[T1](_1)

  def unapply[T1](t: TupleImpl1[T1]): TupleImpl1[T1] = t
}

class TupleImpl2[+T1, +T2](val _1: T1, val _2: T2) extends TupleCons[T1, TupleCons[T2, TNil.type]] {
  def head: T1 = _1
  def tail: TupleCons[T2, TNil.type] = new TupleImpl1(_2)
  override def toString: String = s"(${_1}, ${_2})"
  def isEmpty = false
  def get     = this
}
object TupleImpl2 {
  def apply[T1, T2](_1: T1, _2: T2): TupleImpl2[T1, T2] =
    new TupleImpl2[T1, T2](_1, _2)
  def unapply[T1, T2](t: TupleImpl2[T1, T2]): TupleImpl2[T1, T2] = t
}

class TupleImpl3[+T1, +T2, +T3](val _1: T1, val _2: T2, val _3: T3) extends TupleCons[T1, TupleCons[T2, TupleCons[T3, TNil.type]]] {
  def head: T1 = _1
  def tail: TupleCons[T2, TupleCons[T3, TNil.type]] = new TupleImpl2(_2, _3)
  override def toString: String = s"(${_1}, ${_2}, ${_3})"
  def isEmpty = false
  def get     = this
}
object TupleImpl3 {
  def apply[T1, T2, T3](_1: T1, _2: T2, _3: T3): TupleImpl3[T1, T2, T3] =
    new TupleImpl3[T1, T2, T3](_1, _2, _3)

  def unapply[T1, T2, T3](t: TupleImpl3[T1, T2, T3]): TupleImpl3[T1, T2, T3] = t
}

class TupleImpl4[+T1, +T2, +T3, +T4](val _1: T1, val _2: T2, val _3: T3, val _4: T4) extends TupleCons[T1, TupleCons[T2, TupleCons[T3, TupleCons[T4, TNil.type]]]] {
  def head: T1 = _1
  def tail: TupleCons[T2, TupleCons[T3, TupleCons[T4, TNil.type]]] = new TupleImpl3(_2, _3, _4)
  override def toString: String = s"(${_1}, ${_2}, ${_3}, ${_4})"
  def isEmpty = false
  def get     = this
}
object TupleImpl4 {
  def apply[T1, T2, T3, T4](_1: T1, _2: T2, _3: T3, _4: T4): TupleImpl4[T1, T2, T3, T4] =
    new TupleImpl4[T1, T2, T3, T4](_1, _2, _3, _4)

  def unapply[T1, T2, T3, T4](t: TupleImpl4[T1, T2, T3, T4]): TupleImpl4[T1, T2, T3, T4] = t
}
