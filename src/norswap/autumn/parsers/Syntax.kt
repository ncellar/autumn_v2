package norswap.autumn.parsers
import norswap.autumn.*
import norswap.autumn.result.*
import norswap.violin.utils.after

/// Parsing Characters /////////////////////////////////////////////////////////////////////////////

/**
 * [AnyChar]
 */
val any = AnyChar() withDefiner "any"

/**
 * The end-of-file (null) character.
 */
val eofChar = '\u0000'

/**
 * Matches the end of file (null) character.
 */
val eof = Str("\u0000") withDefiner "eof"

/**
 * [CharRange]`(this, c)`
 */
infix fun Char.to(c: Char)
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
    = Around(this, inside)

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

/**
 * Sugar for `BeforePrintingState(AfterPrintingState(this))`.
 */
val Parser.printStateAround: Parser
    get() = BeforePrintingState(AfterPrintingState(this))


/**
 * See [AfterPrintingTrace].
 */
val Parser.afterPrintingTrace: Parser
    get() = AfterPrintingTrace(this)

/**
 * See [BeforePrintingTrace].
 */
val Parser.beforePrintingTrace: Parser
    get() = BeforePrintingTrace(this)

/**
 * See [ThenPrintResult].
 */
val Parser.thenPrintResult: Parser
    get() = ThenPrintResult(this)

/**
 * See [After].
 */
fun Parser.after(f: Parser.(Context) -> Unit)
    = After(this, f)


/**
 * See [Before].
 */
fun Parser.then(f: Parser.(Context) -> Unit)
    = Before(this, f)

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
 * [Alias]`(this)`
 */
val Parser.alias: Parser
    get() = Alias(this)

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
 * Sugar for CharPred { 'a' <= it && it <= 'f' || 'A' <= it && it <= 'F' || '0' <= it && it <= '9' }.
 */
val hexDigit = CharPred { 'a' <= it && it <= 'f' || 'A' <= it && it <= 'F' || '0' <= it && it <= '9' }

/**
 * Sugar for CharPred { '0' <= it && it <= '7' }.
 */
val octalDigit = CharPred { '0' <= it && it <= '7' }

/**
 * Sugar for `this.build { Pair<Any, Any>(get(), get()) }`.
 */
val Parser.pair: Parser
    get() = build { Pair<Any, Any>(get(), get()) }

/**
 * Sugar for `Seq(left, right.repeat.collect)`.
 */
fun Binary(left: Parser, right: Parser)
    = Seq(left, right.repeat.collect)

/**
 * Sugar for `Seq(left, Seq(*right).repeat.collect)`.
 */
fun Binary(left: Parser, vararg right: Parser)
    = Seq(left, Seq(*right).repeat.collect)

/// Builders ///////////////////////////////////////////////////////////////////////////////////////

/**
 * Class enabling the `(x / y / z).choice` sugar for `Choice(x, y, z)`.
 */
data class ChoiceBuilder (val list: MutableList<Parser>)
{
    /**
     * Turn this builder into a [Choice] instance.
     */
    val choice: Parser
        get() = Choice(*list.toTypedArray())

    /**
     * Turn this builder into a [Longest] instance.
     */
    val longest: Parser
        get() = Longest(*list.toTypedArray())

    operator fun div (right: Parser)
        = this.after { list.add(right) }

    /**
     * Synonymous to [div], but used to emphasize ordering.
     */
    operator fun mod (right: Parser)
        = this.after { list.add(right) }

    operator fun div (right: ChoiceBuilder)
        = this.after { list.addAll(right.list) }

    /**
     * Synonymous to [div], but used to emphasize ordering.
     */
    operator fun mod (right: ChoiceBuilder)
        = this.after { list.addAll(right.list) }
}

/**
 * See [ChoiceBuilder].
 */
operator fun Parser.div (right: Parser)
    = ChoiceBuilder(mutableListOf(this, right))

/**
 * See [ChoiceBuilder].
 */
operator fun Parser.div (right: ChoiceBuilder)
    = ChoiceBuilder(mutableListOf(this)).div(right)

/**
 * See [ChoiceBuilder].
 */
operator fun Parser.mod (right: Parser)
    = ChoiceBuilder(mutableListOf(this, right))

/**
 * See [ChoiceBuilder].
 */
operator fun Parser.mod (right: ChoiceBuilder)
    = ChoiceBuilder(mutableListOf(this)).mod(right)

// -------------------------------------------------------------------------------------------------

/**
 * Class enabling the `(x .. y .. z).seq` sugar for `Seq(x, y, z)`.
 */
data class SeqBuilder (val list: MutableList<Parser>)
{
    /**
     * Turn this builder into a [Seq] instance.
     */
    val seq: Parser
        get() = Seq(*list.toTypedArray())

    operator fun rangeTo (right: Parser)
        = this.after { list.add(right) }

    operator fun rangeTo (right: SeqBuilder)
        = this.after { list.addAll(right.list) }
}

/**
 * See [SeqBuilder].
 */
operator fun Parser.rangeTo (right: Parser)
     = SeqBuilder(mutableListOf(this, right))

/**
 * See [SeqBuilder].
 */
operator fun Parser.rangeTo (right: SeqBuilder)
    = SeqBuilder(mutableListOf(this)).rangeTo(right)

////////////////////////////////////////////////////////////////////////////////////////////////////
