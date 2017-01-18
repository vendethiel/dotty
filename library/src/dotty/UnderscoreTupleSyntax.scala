package dotty

trait UnderscoreTupleSyntax {
  implicit class ArrowAssoc[A](a: A) {
    def -> [B](b: B): TupleCons[A, TupleCons[B, TNil.type]] = TupleCons(a, TupleCons(b, TNil))
  }

  implicit class Tuple1Assessors[A](l: TupleCons[A, TNil.type]) {
    def _1: A = l.head
  }

  type Tuple1[A] = TupleCons[A, TNil.type]

  def Tuple1[A](a: A): TupleCons[A, TNil.type] = TupleCons(a, TNil)

  implicit class Tuple2Assessors[A, B](l: TupleCons[A, TupleCons[B, TNil.type]]) {
    def _1: A = l.head; def _2: B = l.tail.head
  }

  type Tuple2[A, B] = TupleCons[A, TupleCons[B, TNil.type]]

  def Tuple2[A, B](a: A, b: B): TupleCons[A, TupleCons[B, TNil.type]] = TupleCons(a, TupleCons(b, TNil))

  implicit class Tuple3Assessors[A, B, C](l: TupleCons[A, TupleCons[B, TupleCons[C, TNil.type]]]) {
    def _1: A = l.head; def _2: B = l.tail.head; def _3: C = l.tail.tail.head
  }

  type Tuple3[A, B, C] = TupleCons[A, TupleCons[B, TupleCons[C, TNil.type]]]

  def Tuple3[A, B, C](a: A, b: B, c: C): TupleCons[A, TupleCons[B, TupleCons[C, TNil.type]]] = TupleCons(a, TupleCons(b, TupleCons(c, TNil)))

  implicit class Tuple4Assessors[A, B, C, D](l: TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TNil.type]]]]) {
    def _1: A = l.head; def _2: B = l.tail.head; def _3: C = l.tail.tail.head; def _4: D = l.tail.tail.tail.head
  }

  type Tuple4[A, B, C, D] = TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TNil.type]]]]

  def Tuple4[A, B, C, D](a: A, b: B, c: C, d: D): TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TNil.type]]]] = TupleCons(a, TupleCons(b, TupleCons(c, TupleCons(d, TNil))))

  implicit class Tuple5Assessors[A, B, C, D, E](l: TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TNil.type]]]]]) {
    def _1: A = l.head; def _2: B = l.tail.head; def _3: C = l.tail.tail.head; def _4: D = l.tail.tail.tail.head; def _5: E = l.tail.tail.tail.tail.head
  }

  type Tuple5[A, B, C, D, E] = TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TNil.type]]]]]

  def Tuple5[A, B, C, D, E](a: A, b: B, c: C, d: D, e: E): TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TNil.type]]]]] = TupleCons(a, TupleCons(b, TupleCons(c, TupleCons(d, TupleCons(e, TNil)))))

  implicit class Tuple6Assessors[A, B, C, D, E, F](l: TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TNil.type]]]]]]) {
    def _1: A = l.head; def _2: B = l.tail.head; def _3: C = l.tail.tail.head; def _4: D = l.tail.tail.tail.head; def _5: E = l.tail.tail.tail.tail.head; def _6: F = l.tail.tail.tail.tail.tail.head
  }

  type Tuple6[A, B, C, D, E, F] = TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TNil.type]]]]]]

  def Tuple6[A, B, C, D, E, F](a: A, b: B, c: C, d: D, e: E, f: F): TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TNil.type]]]]]] = TupleCons(a, TupleCons(b, TupleCons(c, TupleCons(d, TupleCons(e, TupleCons(f, TNil))))))

  implicit class Tuple7Assessors[A, B, C, D, E, F, G](l: TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TNil.type]]]]]]]) {
    def _1: A = l.head; def _2: B = l.tail.head; def _3: C = l.tail.tail.head; def _4: D = l.tail.tail.tail.head; def _5: E = l.tail.tail.tail.tail.head; def _6: F = l.tail.tail.tail.tail.tail.head; def _7: G = l.tail.tail.tail.tail.tail.tail.head
  }

  type Tuple7[A, B, C, D, E, F, G] = TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TNil.type]]]]]]]

  def Tuple7[A, B, C, D, E, F, G](a: A, b: B, c: C, d: D, e: E, f: F, g: G): TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TNil.type]]]]]]] = TupleCons(a, TupleCons(b, TupleCons(c, TupleCons(d, TupleCons(e, TupleCons(f, TupleCons(g, TNil)))))))

  implicit class Tuple8Assessors[A, B, C, D, E, F, G, H](l: TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TNil.type]]]]]]]]) {
    def _1: A = l.head; def _2: B = l.tail.head; def _3: C = l.tail.tail.head; def _4: D = l.tail.tail.tail.head; def _5: E = l.tail.tail.tail.tail.head; def _6: F = l.tail.tail.tail.tail.tail.head; def _7: G = l.tail.tail.tail.tail.tail.tail.head; def _8: H = l.tail.tail.tail.tail.tail.tail.tail.head
  }

  type Tuple8[A, B, C, D, E, F, G, H] = TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TNil.type]]]]]]]]

  def Tuple8[A, B, C, D, E, F, G, H](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H): TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TNil.type]]]]]]]] = TupleCons(a, TupleCons(b, TupleCons(c, TupleCons(d, TupleCons(e, TupleCons(f, TupleCons(g, TupleCons(h, TNil))))))))

  implicit class Tuple9Assessors[A, B, C, D, E, F, G, H, I](l: TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TNil.type]]]]]]]]]) {
    def _1: A = l.head; def _2: B = l.tail.head; def _3: C = l.tail.tail.head; def _4: D = l.tail.tail.tail.head; def _5: E = l.tail.tail.tail.tail.head; def _6: F = l.tail.tail.tail.tail.tail.head; def _7: G = l.tail.tail.tail.tail.tail.tail.head; def _8: H = l.tail.tail.tail.tail.tail.tail.tail.head; def _9: I = l.tail.tail.tail.tail.tail.tail.tail.tail.head
  }

  type Tuple9[A, B, C, D, E, F, G, H, I] = TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TNil.type]]]]]]]]]

  def Tuple9[A, B, C, D, E, F, G, H, I](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I): TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TNil.type]]]]]]]]] = TupleCons(a, TupleCons(b, TupleCons(c, TupleCons(d, TupleCons(e, TupleCons(f, TupleCons(g, TupleCons(h, TupleCons(i, TNil)))))))))

  implicit class Tuple10Assessors[A, B, C, D, E, F, G, H, I, J](l: TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TNil.type]]]]]]]]]]) {
    def _1: A = l.head; def _2: B = l.tail.head; def _3: C = l.tail.tail.head; def _4: D = l.tail.tail.tail.head; def _5: E = l.tail.tail.tail.tail.head; def _6: F = l.tail.tail.tail.tail.tail.head; def _7: G = l.tail.tail.tail.tail.tail.tail.head; def _8: H = l.tail.tail.tail.tail.tail.tail.tail.head; def _9: I = l.tail.tail.tail.tail.tail.tail.tail.tail.head; def _10: J = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.head
  }

  type Tuple10[A, B, C, D, E, F, G, H, I, J] = TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TNil.type]]]]]]]]]]

  def Tuple10[A, B, C, D, E, F, G, H, I, J](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J): TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TNil.type]]]]]]]]]] = TupleCons(a, TupleCons(b, TupleCons(c, TupleCons(d, TupleCons(e, TupleCons(f, TupleCons(g, TupleCons(h, TupleCons(i, TupleCons(j, TNil))))))))))

  implicit class Tuple11Assessors[A, B, C, D, E, F, G, H, I, J, K](l: TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TNil.type]]]]]]]]]]]) {
    def _1: A = l.head; def _2: B = l.tail.head; def _3: C = l.tail.tail.head; def _4: D = l.tail.tail.tail.head; def _5: E = l.tail.tail.tail.tail.head; def _6: F = l.tail.tail.tail.tail.tail.head; def _7: G = l.tail.tail.tail.tail.tail.tail.head; def _8: H = l.tail.tail.tail.tail.tail.tail.tail.head; def _9: I = l.tail.tail.tail.tail.tail.tail.tail.tail.head; def _10: J = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _11: K = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head
  }

  type Tuple11[A, B, C, D, E, F, G, H, I, J, K] = TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TNil.type]]]]]]]]]]]

  def Tuple11[A, B, C, D, E, F, G, H, I, J, K](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K): TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TNil.type]]]]]]]]]]] = TupleCons(a, TupleCons(b, TupleCons(c, TupleCons(d, TupleCons(e, TupleCons(f, TupleCons(g, TupleCons(h, TupleCons(i, TupleCons(j, TupleCons(k, TNil)))))))))))

  implicit class Tuple12Assessors[A, B, C, D, E, F, G, H, I, J, K, L](l: TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TNil.type]]]]]]]]]]]]) {
    def _1: A = l.head; def _2: B = l.tail.head; def _3: C = l.tail.tail.head; def _4: D = l.tail.tail.tail.head; def _5: E = l.tail.tail.tail.tail.head; def _6: F = l.tail.tail.tail.tail.tail.head; def _7: G = l.tail.tail.tail.tail.tail.tail.head; def _8: H = l.tail.tail.tail.tail.tail.tail.tail.head; def _9: I = l.tail.tail.tail.tail.tail.tail.tail.tail.head; def _10: J = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _11: K = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _12: L = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head
  }

  type Tuple12[A, B, C, D, E, F, G, H, I, J, K, L] = TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TNil.type]]]]]]]]]]]]

  def Tuple12[A, B, C, D, E, F, G, H, I, J, K, L](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L): TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TNil.type]]]]]]]]]]]] = TupleCons(a, TupleCons(b, TupleCons(c, TupleCons(d, TupleCons(e, TupleCons(f, TupleCons(g, TupleCons(h, TupleCons(i, TupleCons(j, TupleCons(k, TupleCons(l, TNil))))))))))))

  implicit class Tuple13Assessors[A, B, C, D, E, F, G, H, I, J, K, L, M](l: TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TNil.type]]]]]]]]]]]]]) {
    def _1: A = l.head; def _2: B = l.tail.head; def _3: C = l.tail.tail.head; def _4: D = l.tail.tail.tail.head; def _5: E = l.tail.tail.tail.tail.head; def _6: F = l.tail.tail.tail.tail.tail.head; def _7: G = l.tail.tail.tail.tail.tail.tail.head; def _8: H = l.tail.tail.tail.tail.tail.tail.tail.head; def _9: I = l.tail.tail.tail.tail.tail.tail.tail.tail.head; def _10: J = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _11: K = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _12: L = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _13: M = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head
  }

  type Tuple13[A, B, C, D, E, F, G, H, I, J, K, L, M] = TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TNil.type]]]]]]]]]]]]]

  def Tuple13[A, B, C, D, E, F, G, H, I, J, K, L, M](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, m: M): TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TNil.type]]]]]]]]]]]]] = TupleCons(a, TupleCons(b, TupleCons(c, TupleCons(d, TupleCons(e, TupleCons(f, TupleCons(g, TupleCons(h, TupleCons(i, TupleCons(j, TupleCons(k, TupleCons(l, TupleCons(m, TNil)))))))))))))

  implicit class Tuple14Assessors[A, B, C, D, E, F, G, H, I, J, K, L, M, N](l: TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TNil.type]]]]]]]]]]]]]]) {
    def _1: A = l.head; def _2: B = l.tail.head; def _3: C = l.tail.tail.head; def _4: D = l.tail.tail.tail.head; def _5: E = l.tail.tail.tail.tail.head; def _6: F = l.tail.tail.tail.tail.tail.head; def _7: G = l.tail.tail.tail.tail.tail.tail.head; def _8: H = l.tail.tail.tail.tail.tail.tail.tail.head; def _9: I = l.tail.tail.tail.tail.tail.tail.tail.tail.head; def _10: J = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _11: K = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _12: L = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _13: M = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _14: N = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head
  }

  type Tuple14[A, B, C, D, E, F, G, H, I, J, K, L, M, N] = TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TNil.type]]]]]]]]]]]]]]

  def Tuple14[A, B, C, D, E, F, G, H, I, J, K, L, M, N](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, m: M, n: N): TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TNil.type]]]]]]]]]]]]]] = TupleCons(a, TupleCons(b, TupleCons(c, TupleCons(d, TupleCons(e, TupleCons(f, TupleCons(g, TupleCons(h, TupleCons(i, TupleCons(j, TupleCons(k, TupleCons(l, TupleCons(m, TupleCons(n, TNil))))))))))))))

  implicit class Tuple15Assessors[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O](l: TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TNil.type]]]]]]]]]]]]]]]) {
    def _1: A = l.head; def _2: B = l.tail.head; def _3: C = l.tail.tail.head; def _4: D = l.tail.tail.tail.head; def _5: E = l.tail.tail.tail.tail.head; def _6: F = l.tail.tail.tail.tail.tail.head; def _7: G = l.tail.tail.tail.tail.tail.tail.head; def _8: H = l.tail.tail.tail.tail.tail.tail.tail.head; def _9: I = l.tail.tail.tail.tail.tail.tail.tail.tail.head; def _10: J = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _11: K = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _12: L = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _13: M = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _14: N = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _15: O = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head
  }

  type Tuple15[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O] = TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TNil.type]]]]]]]]]]]]]]]

  def Tuple15[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, m: M, n: N, o: O): TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TNil.type]]]]]]]]]]]]]]] = TupleCons(a, TupleCons(b, TupleCons(c, TupleCons(d, TupleCons(e, TupleCons(f, TupleCons(g, TupleCons(h, TupleCons(i, TupleCons(j, TupleCons(k, TupleCons(l, TupleCons(m, TupleCons(n, TupleCons(o, TNil)))))))))))))))

  implicit class Tuple16Assessors[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P](l: TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TupleCons[P, TNil.type]]]]]]]]]]]]]]]]) {
    def _1: A = l.head; def _2: B = l.tail.head; def _3: C = l.tail.tail.head; def _4: D = l.tail.tail.tail.head; def _5: E = l.tail.tail.tail.tail.head; def _6: F = l.tail.tail.tail.tail.tail.head; def _7: G = l.tail.tail.tail.tail.tail.tail.head; def _8: H = l.tail.tail.tail.tail.tail.tail.tail.head; def _9: I = l.tail.tail.tail.tail.tail.tail.tail.tail.head; def _10: J = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _11: K = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _12: L = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _13: M = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _14: N = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _15: O = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _16: P = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head
  }

  type Tuple16[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P] = TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TupleCons[P, TNil.type]]]]]]]]]]]]]]]]

  def Tuple16[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, m: M, n: N, o: O, p: P): TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TupleCons[P, TNil.type]]]]]]]]]]]]]]]] = TupleCons(a, TupleCons(b, TupleCons(c, TupleCons(d, TupleCons(e, TupleCons(f, TupleCons(g, TupleCons(h, TupleCons(i, TupleCons(j, TupleCons(k, TupleCons(l, TupleCons(m, TupleCons(n, TupleCons(o, TupleCons(p, TNil))))))))))))))))

  implicit class Tuple17Assessors[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q](l: TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TupleCons[P, TupleCons[Q, TNil.type]]]]]]]]]]]]]]]]]) {
    def _1: A = l.head; def _2: B = l.tail.head; def _3: C = l.tail.tail.head; def _4: D = l.tail.tail.tail.head; def _5: E = l.tail.tail.tail.tail.head; def _6: F = l.tail.tail.tail.tail.tail.head; def _7: G = l.tail.tail.tail.tail.tail.tail.head; def _8: H = l.tail.tail.tail.tail.tail.tail.tail.head; def _9: I = l.tail.tail.tail.tail.tail.tail.tail.tail.head; def _10: J = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _11: K = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _12: L = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _13: M = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _14: N = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _15: O = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _16: P = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _17: Q = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head
  }

  type Tuple17[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q] = TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TupleCons[P, TupleCons[Q, TNil.type]]]]]]]]]]]]]]]]]

  def Tuple17[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, m: M, n: N, o: O, p: P, q: Q): TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TupleCons[P, TupleCons[Q, TNil.type]]]]]]]]]]]]]]]]] = TupleCons(a, TupleCons(b, TupleCons(c, TupleCons(d, TupleCons(e, TupleCons(f, TupleCons(g, TupleCons(h, TupleCons(i, TupleCons(j, TupleCons(k, TupleCons(l, TupleCons(m, TupleCons(n, TupleCons(o, TupleCons(p, TupleCons(q, TNil)))))))))))))))))

  implicit class Tuple18Assessors[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R](l: TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TupleCons[P, TupleCons[Q, TupleCons[R, TNil.type]]]]]]]]]]]]]]]]]]) {
    def _1: A = l.head; def _2: B = l.tail.head; def _3: C = l.tail.tail.head; def _4: D = l.tail.tail.tail.head; def _5: E = l.tail.tail.tail.tail.head; def _6: F = l.tail.tail.tail.tail.tail.head; def _7: G = l.tail.tail.tail.tail.tail.tail.head; def _8: H = l.tail.tail.tail.tail.tail.tail.tail.head; def _9: I = l.tail.tail.tail.tail.tail.tail.tail.tail.head; def _10: J = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _11: K = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _12: L = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _13: M = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _14: N = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _15: O = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _16: P = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _17: Q = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _18: R = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head
  }

  type Tuple18[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R] = TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TupleCons[P, TupleCons[Q, TupleCons[R, TNil.type]]]]]]]]]]]]]]]]]]

  def Tuple18[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, m: M, n: N, o: O, p: P, q: Q, r: R): TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TupleCons[P, TupleCons[Q, TupleCons[R, TNil.type]]]]]]]]]]]]]]]]]] = TupleCons(a, TupleCons(b, TupleCons(c, TupleCons(d, TupleCons(e, TupleCons(f, TupleCons(g, TupleCons(h, TupleCons(i, TupleCons(j, TupleCons(k, TupleCons(l, TupleCons(m, TupleCons(n, TupleCons(o, TupleCons(p, TupleCons(q, TupleCons(r, TNil))))))))))))))))))

  implicit class Tuple19Assessors[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S](l: TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TupleCons[P, TupleCons[Q, TupleCons[R, TupleCons[S, TNil.type]]]]]]]]]]]]]]]]]]]) {
    def _1: A = l.head; def _2: B = l.tail.head; def _3: C = l.tail.tail.head; def _4: D = l.tail.tail.tail.head; def _5: E = l.tail.tail.tail.tail.head; def _6: F = l.tail.tail.tail.tail.tail.head; def _7: G = l.tail.tail.tail.tail.tail.tail.head; def _8: H = l.tail.tail.tail.tail.tail.tail.tail.head; def _9: I = l.tail.tail.tail.tail.tail.tail.tail.tail.head; def _10: J = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _11: K = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _12: L = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _13: M = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _14: N = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _15: O = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _16: P = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _17: Q = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _18: R = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _19: S = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head
  }

  type Tuple19[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S] = TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TupleCons[P, TupleCons[Q, TupleCons[R, TupleCons[S, TNil.type]]]]]]]]]]]]]]]]]]]

  def Tuple19[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, m: M, n: N, o: O, p: P, q: Q, r: R, s: S): TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TupleCons[P, TupleCons[Q, TupleCons[R, TupleCons[S, TNil.type]]]]]]]]]]]]]]]]]]] = TupleCons(a, TupleCons(b, TupleCons(c, TupleCons(d, TupleCons(e, TupleCons(f, TupleCons(g, TupleCons(h, TupleCons(i, TupleCons(j, TupleCons(k, TupleCons(l, TupleCons(m, TupleCons(n, TupleCons(o, TupleCons(p, TupleCons(q, TupleCons(r, TupleCons(s, TNil)))))))))))))))))))

  implicit class Tuple20Assessors[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T](l: TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TupleCons[P, TupleCons[Q, TupleCons[R, TupleCons[S, TupleCons[T, TNil.type]]]]]]]]]]]]]]]]]]]]) {
    def _1: A = l.head; def _2: B = l.tail.head; def _3: C = l.tail.tail.head; def _4: D = l.tail.tail.tail.head; def _5: E = l.tail.tail.tail.tail.head; def _6: F = l.tail.tail.tail.tail.tail.head; def _7: G = l.tail.tail.tail.tail.tail.tail.head; def _8: H = l.tail.tail.tail.tail.tail.tail.tail.head; def _9: I = l.tail.tail.tail.tail.tail.tail.tail.tail.head; def _10: J = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _11: K = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _12: L = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _13: M = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _14: N = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _15: O = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _16: P = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _17: Q = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _18: R = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _19: S = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _20: T = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head
  }

  type Tuple20[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T] = TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TupleCons[P, TupleCons[Q, TupleCons[R, TupleCons[S, TupleCons[T, TNil.type]]]]]]]]]]]]]]]]]]]]

  def Tuple20[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, m: M, n: N, o: O, p: P, q: Q, r: R, s: S, t: T): TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TupleCons[P, TupleCons[Q, TupleCons[R, TupleCons[S, TupleCons[T, TNil.type]]]]]]]]]]]]]]]]]]]] = TupleCons(a, TupleCons(b, TupleCons(c, TupleCons(d, TupleCons(e, TupleCons(f, TupleCons(g, TupleCons(h, TupleCons(i, TupleCons(j, TupleCons(k, TupleCons(l, TupleCons(m, TupleCons(n, TupleCons(o, TupleCons(p, TupleCons(q, TupleCons(r, TupleCons(s, TupleCons(t, TNil))))))))))))))))))))

  implicit class Tuple21Assessors[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U](l: TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TupleCons[P, TupleCons[Q, TupleCons[R, TupleCons[S, TupleCons[T, TupleCons[U, TNil.type]]]]]]]]]]]]]]]]]]]]]) {
    def _1: A = l.head; def _2: B = l.tail.head; def _3: C = l.tail.tail.head; def _4: D = l.tail.tail.tail.head; def _5: E = l.tail.tail.tail.tail.head; def _6: F = l.tail.tail.tail.tail.tail.head; def _7: G = l.tail.tail.tail.tail.tail.tail.head; def _8: H = l.tail.tail.tail.tail.tail.tail.tail.head; def _9: I = l.tail.tail.tail.tail.tail.tail.tail.tail.head; def _10: J = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _11: K = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _12: L = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _13: M = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _14: N = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _15: O = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _16: P = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _17: Q = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _18: R = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _19: S = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _20: T = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _21: U = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head
  }

  type Tuple21[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U] = TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TupleCons[P, TupleCons[Q, TupleCons[R, TupleCons[S, TupleCons[T, TupleCons[U, TNil.type]]]]]]]]]]]]]]]]]]]]]

  def Tuple21[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, m: M, n: N, o: O, p: P, q: Q, r: R, s: S, t: T, u: U): TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TupleCons[P, TupleCons[Q, TupleCons[R, TupleCons[S, TupleCons[T, TupleCons[U, TNil.type]]]]]]]]]]]]]]]]]]]]] = TupleCons(a, TupleCons(b, TupleCons(c, TupleCons(d, TupleCons(e, TupleCons(f, TupleCons(g, TupleCons(h, TupleCons(i, TupleCons(j, TupleCons(k, TupleCons(l, TupleCons(m, TupleCons(n, TupleCons(o, TupleCons(p, TupleCons(q, TupleCons(r, TupleCons(s, TupleCons(t, TupleCons(u, TNil)))))))))))))))))))))

  implicit class Tuple22Assessors[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V](l: TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TupleCons[P, TupleCons[Q, TupleCons[R, TupleCons[S, TupleCons[T, TupleCons[U, TupleCons[V, TNil.type]]]]]]]]]]]]]]]]]]]]]]) {
    def _1: A = l.head; def _2: B = l.tail.head; def _3: C = l.tail.tail.head; def _4: D = l.tail.tail.tail.head; def _5: E = l.tail.tail.tail.tail.head; def _6: F = l.tail.tail.tail.tail.tail.head; def _7: G = l.tail.tail.tail.tail.tail.tail.head; def _8: H = l.tail.tail.tail.tail.tail.tail.tail.head; def _9: I = l.tail.tail.tail.tail.tail.tail.tail.tail.head; def _10: J = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _11: K = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _12: L = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _13: M = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _14: N = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _15: O = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _16: P = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _17: Q = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _18: R = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _19: S = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _20: T = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _21: U = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head; def _22: V = l.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.tail.head
  }

  type Tuple22[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V] = TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TupleCons[P, TupleCons[Q, TupleCons[R, TupleCons[S, TupleCons[T, TupleCons[U, TupleCons[V, TNil.type]]]]]]]]]]]]]]]]]]]]]]

  def Tuple22[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, m: M, n: N, o: O, p: P, q: Q, r: R, s: S, t: T, u: U, v: V): TupleCons[A, TupleCons[B, TupleCons[C, TupleCons[D, TupleCons[E, TupleCons[F, TupleCons[G, TupleCons[H, TupleCons[I, TupleCons[J, TupleCons[K, TupleCons[L, TupleCons[M, TupleCons[N, TupleCons[O, TupleCons[P, TupleCons[Q, TupleCons[R, TupleCons[S, TupleCons[T, TupleCons[U, TupleCons[V, TNil.type]]]]]]]]]]]]]]]]]]]]]] = TupleCons(a, TupleCons(b, TupleCons(c, TupleCons(d, TupleCons(e, TupleCons(f, TupleCons(g, TupleCons(h, TupleCons(i, TupleCons(j, TupleCons(k, TupleCons(l, TupleCons(m, TupleCons(n, TupleCons(o, TupleCons(p, TupleCons(q, TupleCons(r, TupleCons(s, TupleCons(t, TupleCons(u, TupleCons(v, TNil))))))))))))))))))))))
}
