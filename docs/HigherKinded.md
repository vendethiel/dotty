The duality
-----------

The core idea: A parameterized class such as

    class Map[K, V]

is treated as equivalent to a type with type members:

    class Map { type Map$K; type Map$V }

The type members are name-mangled (i.e. `Map$K`) so that they do not conflict with other
members or parameters named `K` or `V`.

A type-instance such as `Map[String, Int]` would then be treated as equivalent to

    Map { type Map$K = String; type Map$V = Int }

Named type parameters
---------------------

Type parameters can have unmangled names. This is achieved by adding the `type` keyword
to a type parameter declaration, analogous to how `val` indicates a named field. For instance,

    class Map[type K, type V]

is treated as equivalent to

    class Map { type K; type V }

The parameters are made visible as fields.

Wildcards
---------

A wildcard type such as `Map[_, Int]` is equivalent to

    Map { type Map$V = Int }

I.e. `_`'s omit parameters from being instantiated. Wildcard arguments
can have bounds. E.g.

    Map[_ <: AnyRef, Int]

is equivalent to

    Map { type Map$K <: AnyRef; type Map$V = Int }


Type parameters
---------------

Under the duality, we use the following definition for what type parameters are.

 - The type parameters of a class or trait type are those parameter fields that are not yet instantiated.
 - The type parameters of an abstract type are the type parameters of its upper bound
 - The type parameters of an alias type are the type parameters of its right hand side
 - An intersection type `T & U` is well-formed only if at most one of `T` and `U` has type parameters. The type parameters of `T & U` are then the union of the type parameters of `T` and the type
parameters of  `U`.

Partial applications
--------------------

The definition of type parameters in the previous section leads to a simple model of partial applications.
Consider for instance:

    type Histogram = Map[_, Int]

`Histogram` is a higher-kinded type that still has one type parameter.
`Histogram[String]`
would be a possible type instance, and it would be equivalent to `Map[String, Int]`.


Modelling polymorphic type declarations
---------------------------------------

The partial application scheme gives us a new -- and quite elegant --
way to do higher-kinded types. But how do we interprete the
poymorphic types that exist in current Scala?

More concretely, current Scala allows us to write parameterize type
definitions, abstract types, and type parameters. In the new scheme,
only classes (and traits) can have parameters and these are treated as
equivalent to type members. Type aliases and abstract types do not
allow the definition of type members so we have to interprete
polymorphic type aliases and abstract types specially.

Modelling polymorphic type aliases: simple case
-----------------------------------------------

A polymorphic type alias such as

    type Pair[T] = Tuple2[T, T]

where `Tuple2` is declared as

    class Tuple2[T1, T2] ...

is expanded to a monomorphic type alias like this:

    type Pair = Tuple2 { type Tuple2$T2 = Tuple2$T1 }

More generally, each type parameter of the left-hand side must
appear as a type member of the right hand side type. Type members
must appear in the same order as their corresponding type parameters.
References to the type parameter are then translated to references to the
type member. The type member itself is left uninstantiated.

This technique can expand most polymorphic type aliases appearing
in Scala codebases but not all of them. For instance, the following
alias cannot be expanded, because the parameter type `T` is not a
type member of the right-hand side `List[List[T]]`.

    type List2[T] = List[List[T]]

We scanned the Scala standard library for occurrences of polymorphic
type aliases and determined that only two occurrences could not be expanded.
In `io/Codec.scala`:

    type Configure[T] = (T => T, Boolean)

And in `collection/immutable/HashMap.scala`:

    private type MergeFunction[A1, B1] = ((A1, B1), (A1, B1)) => (A1, B1)

Modelling polymorphic type aliases: general case
------------------------------------------------

To model more general polymorphic type aliases, introduce a family of types

    type TCi[T1, ..., Ti]  (i = 1, ...)

Type `TCi` represents type constructors of arity `i`. A type alias

    type T[X1,...,Xm] = C1 & ... & Cn

where the `Ci` are possibly instantiated traits and all occurrences of `Xi` are in
type arguments or refinements to the `Ci` is expanded as follows:

   type T = C1 & ... & Cn & TCm { R }

Here `R` will bind all type arguments of `C1`, ..., `Cn` with the original arguments
where every type `Xi` is replaced by `Tci`.

Example:

    type Configure = Tuple2[_, Boolean] & TC1 { Tuple2$T1 = TC1$T1 -> TC1$T1 }

If one of the `Ci`'s already contains a `TC` component, it is expanded out and
thereby replaced by the new `TC` type.

Example: Consider the two aliases

    type RMap[K, V] = Map[V, K]]
    type RRMap[K, V] = RMap[V, K]

These expand as follows:

    type RMap = Map & TC2 { type Map$K = TC2$T2; type Map$V = TC2$T1 }
    type RRMap = Map & TC2 { type Map$K = TC2$T1; type Map$V = TC2$T2 }

Note that `RRMap` is essentially equivakent to `Map`. It is simply `Map` with
a `TC2` which forwards its arguments to the corresponding `Map` arguments.

We call the encoding of a rhs type `T` referring to type parameter declarations `TP1`, ..., `TPn`
`Lambda TP1,...,TPn. T`.

`Lambda TP1,...,TPn. T` can encode all types `T` where the type parameters
`TP1`, ..., `TPn` occur only as arguments to some other type in `T`.
We call those type declarations _non-contracting_. Seen the other way, a _contracting_ type
declaration contains a type parameter as a top-level type of its right
hand side.  The classical example is:

    type Id[T] = T

Contracting type declarations cannot be modelled and are therefore forbidden.

Modelling higher-kinded types
-----------------------------

The encoding of higher-kinded types uses again the `TC` traits to represent type constructors.
Consider the higher-kinded type declaration

    type Rep[T]

We expand this to

    type Rep <: TC1

The type parameters of `Rep` are the type parameters of its upper bound, so
`Rep` is of kind `* -> *`.

More generally, a higher-kinded type declaration

     type X[TP1, ..., TPn] <: U

is encoded as

     type X <: Lambda TP1,...,TPn. U

If we instantiate `Rep` with a type argument, this is expanded as was explained before.

    Rep[String]

would expand to

    Rep { type TC1$T = String }

If we instantiate the higher-kinded type with a concrete type constructor (i.e. a parameterized
trait or class, we have to do one extra thing to make it work. E.g.

    type Rep = Set

would expand to

    type Rep = Set & TC1 { type Set$T = TC1$T }

Or,

    type Rep = Map[String, _]

would expand to

    type Rep = Map[String, _] & TC1 { type Map$V = TC1$T }

In each case, we instantiate the open type parameter(s) of the right hand side
with the type parameters of the `TC` type. After the instantiation, only the
type parameters of the `TC` type remain to be filled.

Full example
------------

Consider the higher-kinded `Functor` type class

    class Functor[F[_]] {
       def map[A, B](f: A => B): F[A] => F[B]
    }

This would be represented as follows:

    class Functor[F <: TC1] {
       def map[A, B](f: A => B): F { type TC1$T1 = A } => F { type TC1$T1 = B }
    }

The type `Functor[List]` would be represented as follows

    Functor {
       type F = List & TC1 { type List$T1 = TC1$T1 }
    }

Now, assume we have a value

    val ml: Functor[List]

Then `ml.map` would have type

    subst(F { type TC1$T1 = A } => F { type TC1$T1 = B })

where `subst` is the substitution of `List & TC1 { type List$T1 = TC1$T1 }` for `F`.
This gives:

    List { type List$T1 == TC1$T1; type TC1$T1 = A }
    => List { type List$T1 == TC1$T1; type TC1$T1 = B }




