package norswap.autumn.parsers
import norswap.autumn.Context
import norswap.autumn.Parser
import norswap.violin.utils.after

// Parsers to be used during debugging.

// -------------------------------------------------------------------------------------------------

/**
 * Acts like [child], but logs the parsing state beforehand.
 */
fun AfterPrintingState (child: Parser) = Parser(child) { ctx ->
    ctx.log(ctx.stateString())
    child.parse(ctx)
}

// -------------------------------------------------------------------------------------------------

/**
 * Acts like [child], but logs the parsing state afterwards.
 */
fun BeforePrintingState (child: Parser) = Parser(child) { ctx ->
    child.parse(ctx).after { ctx.log(ctx.stateString()) }
}

// -------------------------------------------------------------------------------------------------

/**
 * Acts like [child], but logs the parse trace beforehand.
 */
fun AfterPrintingTrace (child: Parser) = Parser(child) { ctx ->
    ctx.logTrace()
    child.parse(ctx)
}

// -------------------------------------------------------------------------------------------------

/**
 * Acts like [child], but logs the parse trace afterwards.
 */
fun BeforePrintingTrace (child: Parser) = Parser(child) { ctx ->
    child.parse(ctx).after { ctx.logTrace() }
}

// -------------------------------------------------------------------------------------------------

/**
 * Acts like [child], but prints its result afterwards.
 */
fun ThenPrintResult(child: Parser) = Parser(child) { ctx ->
    child.parse(ctx).after { ctx.log("$child: $it") }
}

// -------------------------------------------------------------------------------------------------

/**
 * Acts like [child], but calls [f] beforehand.
 */
fun After (child: Parser, f: Parser.(Context) -> Unit) = Parser { ctx ->
    f(ctx) ; child.parse(ctx)
}

// -------------------------------------------------------------------------------------------------

/**
 * Acts like [child], but calls [f] afterwards.
 */
fun Before (child: Parser, f: Parser.(Context) -> Unit) = Parser { ctx ->
    child.parse(ctx).after { f(ctx) }
}

// -------------------------------------------------------------------------------------------------