package norswap.autumn.parsers
import norswap.autumn.*

/// Parsing Characters /////////////////////////////////////////////////////////////////////////////

/**
 * [AnyChar]
 */
val any = AnyChar()

/**
 * [CharRange]`(this, c)`
 */
infix fun Char.through(c: Char)
    = CharRange(this, c)

/**
 * [Str]`(this)`
 */
val String.lit: Parser
    get() = Str(this)

/**
 * [CharSet]`(*this.toCharArray()`
 */
val String.set: Parser
    get() = CharSet(*toCharArray())

/// Lookahead //////////////////////////////////////////////////////////////////////////////////////

/**
 * [Ahead]`(this)`
 */
val Parser.ahead: Parser
    get() = Ahead(this)

/**
 * [Not]`(this)`
 */
val Parser.not: Parser
    get() = Not(this)

/// Sequencing and Optionals ///////////////////////////////////////////////////////////////////////

/**
 * [Opt]`(this)`
 */
val Parser.opt: Parser
    get() = Opt(this)

/**
 * [ZeroMore]`(this)`
 */
val Parser.repeat: Parser
    get() = ZeroMore(this)

/**
 * [OneMore]`(this)`
 */
val Parser.repeat1: Parser
    get() = OneMore(this)

/**
 * [Repeat]`(n, this)`
 */
fun Parser.repeat(n: Int)
    = Repeat(n, this)

/**
 * [Around]`(this, inside)`
 */
infix fun Parser.around(inside: Parser)
    = Opt(Around(this, inside))

/**
 * [Around1]`(this, inside)`
 */
infix fun Parser.around1(inside: Parser)
    = Around1(this, inside)

/// Until //////////////////////////////////////////////////////////////////////////////////////////

/**
 * [Until]`(this, until, matchUntil = true, matchSome = false)`
 */
infix fun Parser.until(until: Parser)
    = Until(this, until, matchUntil = true, matchSome = false)

/**
 * [Until]`(this, until, matchUntil = true, matchSome = true)`
 */
infix fun Parser.until1(until: Parser)
    = Until(this, until, matchUntil = true, matchSome = true)

/**
 * [Until]`(this, until, matchUntil = false, matchSome = false)`
 */
infix fun Parser.until_(until: Parser)
    = Until(this, until, matchUntil = true, matchSome = false)

/**
 * [Until]`(this, until, matchUntil = false, matchSome = true)`
 */
infix fun Parser.until1_(until: Parser)
    = Until(this, until, matchUntil = true, matchSome = true)


/// Failure Handling ///////////////////////////////////////////////////////////////////////////////

/**
 * See [OrRaise].
 */
infix fun Parser.orRaise(e: Parser.(Context) -> Failure)
    = OrRaise(this, e)

/**
 * See [OrRaiseMsg].
 */
infix fun Parser.orRaiseMsg(msg: Parser.(Context) -> String)
    = OrRaiseMsg(this, msg)

/// Diagnostic /////////////////////////////////////////////////////////////////////////////////////

/**
 * See [AfterPrintingState].
 */
val Parser.afterPrintingState: Parser
    get() = AfterPrintingState(this)

/**
 * See [BeforePrintingState].
 */
val Parser.beforePrintingState: Parser
    get() = BeforePrintingState(this)

/// AST Building ///////////////////////////////////////////////////////////////////////////////////

/**
 * See [WithStack].
 */
fun Parser.withStack(pop: Boolean = true, node: StackAccess.() -> Result)
    = WithStack(this, pop, node)

/**
 * See [DoWithStack].
 */
fun Parser.doWithStack(pop: Boolean = true, node: StackAccess.() -> Unit)
    = DoWithStack(this, pop, node)

/**
 * See [Build].
 */
infix fun Parser.build(node: StackAccess.() -> Any)
    = Build(this, node)

/**
 * See [Leaf].
 */
infix fun Parser.leaf(node: (String) -> Any)
    = Leaf(this, node)

/**
 * Sugar for [Leaf]`(this, {it})`
 */
val Parser.leaf: Parser
    get() = Leaf(this, {it})

/**
 * See [BuildMaybe].
 */
val Parser.maybe: Parser
    get() = BuildMaybe(this)

/**
 * See [AsBool].
 */
val Parser.asBool: Parser
    get() = AsBool(this)

/**
 * See [Collect].
 */
fun <T: Any> Parser.collect()
    = Collect<T>(this)

/**
 * See [Collect].
 *
 * In Collect, T = Any, but as this type is erased, the list that is pushed on stack can be cast
 * to the proper type without hurdle.
 */
val Parser.collect: Parser
    get() = Collect<Any>(this)

/**
 * See [RightAssoc].
 */
fun <T: Any> Parser.rightAssoc(assoc: (r: T, t: T) -> T)
    = RightAssoc<T>(this, assoc)

/// With ... ///////////////////////////////////////////////////////////////////////////////////////

/**
 * See [WithMatchStart].
 */
infix fun Parser.withMatchStart(f: Parser.(ctx: Context, start: Int) -> Result)
    = WithMatchStart(this, f)

/**
 * See [WithMatchString].
 */
infix fun Parser.withMatchString(f: Parser.(ctx: Context, str: String) -> Result)
    = WithMatchString(this, f)

/**
 * See [DoWithMatchStart].
 */
infix fun Parser.doWithMatchStart(f: Parser.(Context, Int) -> Unit)
    = DoWithMatchStart(this, f)

/**
 * See [DoWithMatchString].
 */
infix fun Parser.doWithMatchString(f: Parser.(Context, String) -> Unit)
    = DoWithMatchString(this, f)

/// Misc ///////////////////////////////////////////////////////////////////////////////////////////

/**
 * [Bounded]`(this, source)`
 */
infix fun Parser.over(source: Parser)
    = Bounded(this, source)

/**
 * [Bounded]`(this, f)`
 */
infix fun Parser.over(f: Parser.(Context) -> Result)
    = Bounded(this, f)

/// Sugar //////////////////////////////////////////////////////////////////////////////////////////


/**
 * Sugar for CharPred { 'a' <= it && it <= 'z' || 'A' <= it && it <= 'Z' }.
 */
val alpha = CharPred { 'a' <= it && it <= 'z' || 'A' <= it && it <= 'Z' }

/**
 * Sugar for CharPred { 'a' <= it && it <= 'z' || 'A' <= it && it <= 'Z' || '0' <= it && it <= '9' }.
 */
val alphaNum = CharPred { 'a' <= it && it <= 'z' || 'A' <= it && it <= 'Z' || '0' <= it && it <= '9' }

/**
 * Sugar for CharPred { '0' <= it && it <= '9' }.
 */
val digit = CharPred { '0' <= it && it <= '9' }

/**
 * Sugar for `this.build { Pair<Any, Any>(get(), get()) }`.
 */
val Parser.pair: Parser
    get() = build { Pair<Any, Any>(get(), get()) }

/**
 * Sugar for `Seq(left, Seq(*right).repeat.collect)`.
 */
fun Binary(left: Parser, vararg right: Parser)
    = Seq(left, Seq(*right).repeat.collect)

////////////////////////////////////////////////////////////////////////////////////////////////////