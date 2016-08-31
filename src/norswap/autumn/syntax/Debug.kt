package norswap.autumn.syntax
import norswap.autumn.Context
import norswap.autumn.Parser
import norswap.autumn.parsers.*

// -------------------------------------------------------------------------------------------------

/**
 * See [AfterPrintingState].
 */
val Parser.afterPrintingState: Parser
    get() = AfterPrintingState(this)

// -------------------------------------------------------------------------------------------------

/**
 * See [BeforePrintingState].
 */
val Parser.beforePrintingState: Parser
    get() = BeforePrintingState(this)

// -------------------------------------------------------------------------------------------------

/**
 * Sugar for `BeforePrintingState(AfterPrintingState(this))`.
 */
val Parser.printStateAround: Parser
    get() = BeforePrintingState(AfterPrintingState(this))

// -------------------------------------------------------------------------------------------------

/**
 * See [AfterPrintingTrace].
 */
val Parser.afterPrintingTrace: Parser
    get() = AfterPrintingTrace(this)

// -------------------------------------------------------------------------------------------------

/**
 * See [BeforePrintingTrace].
 */
val Parser.beforePrintingTrace: Parser
    get() = BeforePrintingTrace(this)

// -------------------------------------------------------------------------------------------------

/**
 * See [ThenPrintResult].
 */
val Parser.thenPrintResult: Parser
    get() = ThenPrintResult(this)

// -------------------------------------------------------------------------------------------------

/**
 * See [After].
 */
fun Parser.after(f: Parser.(Context) -> Unit)
    = After(this, f)

// -------------------------------------------------------------------------------------------------

/**
 * See [Before].
 */
fun Parser.then(f: Parser.(Context) -> Unit)
    = Before(this, f)

// -------------------------------------------------------------------------------------------------