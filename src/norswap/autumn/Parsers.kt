@file:Suppress("PackageDirectoryMismatch")
package norswap.autumn.parsers
import norswap.autumn.*
import norswap.autumn.result.*
import norswap.violin.stream.*
import norswap.violin.utils.after

/// Parsing Characters /////////////////////////////////////////////////////////////////////////////

/**
 * Matches any single character.
 */
fun AnyChar() = Parser { ctx ->
    ctx.succeed { text[pos] != '\u0000' }
        .ifSuccess { ++ctx.pos }
}

/**
 * Matches any single character matching the predicate [p], else fails.
 */
fun CharPred (p: (Char) -> Boolean) = Parser { ctx ->
    ctx.succeed { p(text[pos]) }
        .ifSuccess { ++ctx.pos }
}

/**
 * Matches any single character in the (start, end) range.
 */
fun CharRange (start: Char, end: Char) = Parser { ctx ->
    ctx.succeed { val p = text[pos] ; start <= p && p <= end }
        .ifSuccess { ++ctx.pos }
}

/**
 * Matches any single character contained in [chars], else fails.
 */
fun CharSet (vararg chars: Char) = Parser { ctx ->
    ctx.succeed { chars.contains(text[pos]) }
        .ifSuccess { ++ctx.pos }
}

/**
 * Matches the string [str], else fails.
 */
fun Str (str: String) = Parser { ctx ->
    ctx.succeed { text.regionMatches(pos, str, 0, str.length) }
        .ifSuccess { ctx.pos += str.length }
}

/// Choices ////////////////////////////////////////////////////////////////////////////////////////

/**
 * Matches the first successful parser in [children], else fails.
 */
fun Choice (vararg children: Parser) = Parser(*children) { ctx ->
    children.stream().map { it.parse(ctx) }
        .upThrough { it is Success }
        .maxWith(Furthest)
        ?: ctx.error { "empty choice: ${toStringSimple()}" }
}

/**
 * Matches the parser in [children] which successfully matches the most input, else fails.
 */
fun Longest (vararg children: Parser) = Parser(*children) body@ { ctx ->
    val initial = ctx.snapshot()
    var bestSnapshot = initial
    var bestPos = -1
    var furthestError: Error? = null
    for (child in children) {
        val result = child.parse(ctx)
        if (ctx.pos > bestPos) {
            bestPos = ctx.pos
            bestSnapshot = ctx.snapshot()
        }
        if (result !is Error)
            ctx.restore(initial)
        else if (bestPos == -1)
            furthestError = Furthest.max(furthestError ?: result, result)
    }

    if (bestPos > -1) {
        ctx.restore(bestSnapshot) ; Success
    } else if (furthestError != null) furthestError
    else ctx.error { "empty longest-match choice: ${toStringSimple()}" }
}

/// Lookahead //////////////////////////////////////////////////////////////////////////////////////

/**
 * Succeeds matching nothing if [child] succeeds, else fails.
 */
fun Lookahead (child: Parser) = Parser(child) { ctx ->
    ctx.snapshot().let { child.parse(ctx).ifSuccess { ctx.restore(it) } }
}

/**
 * Succeeds matching nothing if [child] fails, or fails.
 */
fun Not (child: Parser) = Parser(child) { ctx ->
    val snapshot = ctx.snapshot()
    val result = child.parse(ctx)
    if (result is Success) {
        ctx.restore(snapshot)
        ctx.error { child.toStringSimple() + " succeeded" }
    } else Success
}

/// Sequencing and Optionals ///////////////////////////////////////////////////////////////////////

/**
 * Matches the successful sequential invocation of all parsers in [children], else fails.
 */
fun Seq (vararg children: Parser) = Parser(*children) { ctx ->
    children.stream().map { it.parse(ctx) }
        .first { it is Error }
        ?: Success
}

/**
 * Matches the same thing as [child], else succeeds matching nothing.
 */
fun Optional (child: Parser) = Parser(child) { ctx ->
    child.parse(ctx).or { Success }
}

/**
 * Matches the maximum number of successful sequential invocation of [child].
 */
fun ZeroMore (child: Parser) = Parser(child) { ctx ->
    while (child.parse(ctx) is Success) ; Success
}

/**
 * Matches the maximum number of successful sequential invocation of [child].
 * Fails if no invocation succeeds.
 */
fun OneMore (child: Parser) = Parser(child) { ctx ->
    child.parse(ctx).and { while (child.parse(ctx) is Success); Success }
}

/**
 * Matches zero or more repetition of [repeat] (that do not match [until]), followed by [until].
 *
 * [matchUntil] (true by default) determines whether [until] is part of the final match.
 *
 * [matchSome] (false by default) determines whether at [repeat] should be matched at least once.
 *
 * Here are some equivalent parser, depending on the value of parameters:
 * - matchUntil, matchSome: `Seq(OneMore(Seq(Not(until), repeat)), until)`
 * - matchUntil, !matchSome: `Seq(ZeroMore(Seq(Not(until), repeat)), until)`
 * - !matchUntil, matchSome: `OneMore(Seq(Not(until), repeat))`
 * - !matchUntil, !matchSome: `ZeroMore(Seq(Not(until), repeat))`
 */
class Until (
    val repeat: Parser,
    val until: Parser,
    val matchUntil: Boolean = true,
    val matchSome: Boolean = false)
: Parser(repeat, until)
{
    override fun _parse_(ctx: Context): Result {
        val initial = ctx.snapshot()
        var snapshot = initial
        var err: Error
        var cnt = 0
        while (true) {
            var res = until.parse(ctx)
            if (res is Success) break
            err = res as Error
            res = repeat.parse(ctx)
            if (res is Error) {
                ctx.restore(initial)
                return Furthest.max(err, res)
            }
            ++ cnt
            if (!matchUntil) snapshot = ctx.snapshot()
        }
        if (matchSome && cnt == 0) {
            ctx.restore(initial)
            return ctx.error { "UntilSome did not match any repeatable item" }
        }
        ctx.restore(snapshot)
        return Success
    }

    override fun definer() = super.definer() +
        "(${if (!matchUntil) "!" else ""}matchUntil, ${ if (!matchSome) "!" else ""}matchSome)"
}

/**
 * Matches [n] repetitions of [child], else fails.
 */
fun NTimes (n: Int, child: Parser) = Parser(child) body@ { ctx ->
    val snapshot = ctx.snapshot()
    for (i in 1..n) {
        val result = child.parse(ctx)
        if (result is Error) {
            ctx.restore(snapshot)
            return@body result
        }
    }
    return@body Success
}

/**
 * Matches one or more repetition of [item], separated by [sep].
 *
 * Equivalent to `Seq(item, ZeroMore(Seq(sep, item)))`
 */
fun Separated (item: Parser, sep: Parser) = Parser(item, sep) { ctx ->
    item.parse(ctx) and {
        while (ctx.transact { sep.parse(ctx) and { item.parse(ctx) } } is Success);
        Success
    }
}

/// Error Handling /////////////////////////////////////////////////////////////////////////////////

/**
 * Always succeeds matching nothing.
 */
fun Successful()
    = Parser { Success }

/**
 * Always fails.
 */
fun Failure()
    = Parser { it.error() }

/**
 * Always fails, using [e] to construct the returned error.
 */
fun Raise (e: Parser.(Context) -> Error)
    = Parser { e(it) }

/**
 * Always fails, using [msg] to construct the message of the returned error.
 */
fun RaiseMsg (msg: Parser.(Context) -> String)
    = Raise { it.error(msg = msg)  }

/**
 * Throws a panic, using [e] to construct the thrown error.
 */
fun Panic (e: Parser.(Context) -> Error)
    = Parser { panic(e(it)) }

/**
 * Throws a panic, using [msg] to construct the message of the thrown error.
 */
fun PanicMsg (msg: Parser.(Context) -> String)
    = Panic { it.error(msg = msg)  }

/**
 * Matches [child], else throws the error it returned.
 */
fun Paranoid (child: Parser) = Parser(child) { ctx ->
    ctx.parse(child)
        .ifError { panic(it) }
}

/**
 * Matches [child], else fails. If [child] throws an error that matches [pred] (matches
 * everything by default), returns it instead.
 */
fun Chill (child: Parser, pred: (Error) -> Boolean = { true }) = Parser(child) { ctx ->
    tryParse(pred) { child.parse(ctx) }
}

/**
 * Matches this parser, else raises the error returned by [e].
 */
fun Parser.orRaise(e: Parser.(Context) -> Error) = Parser(this) { ctx ->
    ctx.parse(this@orRaise).or { e(ctx) }
}

/**
 * Matches this parser, else raises an error with the message returned by [msg].
 */
fun Parser.orRaiseMsg(msg: Parser.(Context) -> String) = this.orRaise { it.error(msg = msg) }

/**
 * Returns a parser that matches the same as the parser it is called on, but logs
 * the parsing state beforehand.
 */
fun Parser.afterPrintingState() = Parser(this) { ctx ->
    ctx.logStream.println(ctx.stateString())
    this@afterPrintingState.parse(ctx)
}

/**
 * Returns a parser that matches the same as the parser it is called on, but logs
 * the parsing state afterwards.
 */
fun Parser.beforePrintingState() = Parser(this) { ctx ->
    this@beforePrintingState.parse(ctx).after { ctx.logStream.println(ctx.stateString()) }
}

/// Misc ///////////////////////////////////////////////////////////////////////////////////////////

/**
 * Returns a parser that uses [generator] to generate a parser at parse-time, run it and returns
 * and its result.
 */
fun Dynamic(generator: (Context) -> Parser) = Parser { ctx -> generator(ctx).parse(ctx) }


/**
 * Always succeeds after calling [f].
 */
fun Perform(f: Context.() -> Unit)
    = Parser { it.f() ; Success }

/**
 * Succeeds if [pred] holds, or fails with the error generated by [err] (by default: `ctx.error()`).
 */
fun Predicate(err: Parser.(Context) -> Error = { it.error() }, pred: Context.() -> Boolean)
    = Parser { ctx -> if (ctx.pred()) Success else err(ctx) }

/**
 * Succeeds if [pred] holds, or fails with an error using the message generated by [msg].
 */
fun PredicateMsg(pred: Context.() -> Boolean, msg: Parser.(Context) -> String)
    = Parser { ctx -> if (ctx.pred()) Success else ctx.error(msg) }

////////////////////////////////////////////////////////////////////////////////////////////////////
