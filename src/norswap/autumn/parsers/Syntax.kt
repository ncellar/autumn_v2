package norswap.autumn.parsers
import norswap.autumn.*

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
 * See [BuildLeaf].
 */
infix fun Parser.buildLeaf(node: (String) -> Any)
    = BuildLeaf(this, node)

/**
 * See [BuildMaybe].
 */
val Parser.buildMaybe: Parser
    get() = BuildMaybe(this)

/**
 * See [BuildOptional].
 */
val Parser.buildOptional: Parser
    get() = BuildOptional(this)

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

////////////////////////////////////////////////////////////////////////////////////////////////////
