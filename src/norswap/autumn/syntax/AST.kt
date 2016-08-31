package norswap.autumn.syntax
import norswap.autumn.Parser
import norswap.autumn.StackAccess
import norswap.autumn.parsers.*
import norswap.autumn.result.Result

// -------------------------------------------------------------------------------------------------

/**
 * See [WithStack].
 */
fun Parser.withStack (pop: Boolean = true, node: StackAccess.() -> Result)
    = WithStack(this, pop, node)

// -------------------------------------------------------------------------------------------------

/**
 * See [DoWithStack].
 */
fun Parser.doWithStack (pop: Boolean = true, node: StackAccess.() -> Unit)
    = DoWithStack(this, pop, node)

// -------------------------------------------------------------------------------------------------

/**
 * See [Build].
 */
infix fun Parser.build (node: StackAccess.() -> Any)
    = Build(this, node)

// -------------------------------------------------------------------------------------------------

/**
 * See [Leaf].
 */
infix fun Parser.leaf (node: (String) -> Any)
    = Leaf(this, node)

// -------------------------------------------------------------------------------------------------

/**
 * Sugar for [Leaf]`(this, {it})`
 */
val Parser.leaf: Parser
    get() = Leaf(this, {it})

// -------------------------------------------------------------------------------------------------

/**
 * See [BuildMaybe].
 */
val Parser.maybe: Parser
    get() = BuildMaybe(this)

// -------------------------------------------------------------------------------------------------

/**
 * See [AsBool].
 */
val Parser.asBool: Parser
    get() = AsBool(this)

// -------------------------------------------------------------------------------------------------

/**
 * See [Collect].
 */
fun <T: Any> Parser.collect ()
    = Collect<T>(this)

// -------------------------------------------------------------------------------------------------

/**
 * See [Collect].
 *
 * In Collect, T = Any, but as this type is erased, the list that is pushed on stack can be cast
 * to the proper type without hurdle.
 */
val Parser.collect: Parser
    get() = Collect<Any>(this)

// -------------------------------------------------------------------------------------------------

/**
 * See [BuildPair].
 */
val Parser.pair: Parser
    get() = BuildPair(this)

// -------------------------------------------------------------------------------------------------