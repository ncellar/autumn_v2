package norswap.autumn.parsers
import norswap.autumn.*
import norswap.violin.stream.*
import norswap.violin.utils.after

/// Helpers ////////////////////////////////////////////////////////////////////////////////////////

/**
 * Returns the receiver after setting its definer to [definer].
 */
infix fun Parser.withDefiner(definer: String)
    = this after { this.definer = definer }

/// Parsing Characters /////////////////////////////////////////////////////////////////////////////

/**
 * Matches any single character.
 */
fun AnyChar() = Parser { ctx ->
    ctx.succeed { text[pos] != '\u0000' }
        .andDo { ++ctx.pos }
}

/**
 * Matches any single character matching the predicate [p], else fails.
 */
fun CharPred (p: (Char) -> Boolean) = Parser { ctx ->
    ctx.succeed { p(text[pos]) }
        .andDo { ++ctx.pos }
}

/**
 * Matches any single character in the (start, end) range.
 */
fun CharRange (start: Char, end: Char) = Parser { ctx ->
    ctx.succeed { val p = text[pos] ; start <= p && p <= end }
        .andDo { ++ctx.pos }
}

/**
 * Matches any single character contained in [chars], else fails.
 */
fun CharSet (vararg chars: Char) = Parser { ctx ->
    ctx.succeed { chars.contains(text[pos]) }
        .andDo { ++ctx.pos }
}

/**
 * Matches the string [str], else fails.
 */
fun Str (str: String) = Parser { ctx ->
    ctx.succeed { text.regionMatches(pos, str, 0, str.length) }
        .andDo { ctx.pos += str.length }
}

/// Choices ////////////////////////////////////////////////////////////////////////////////////////

/**
 * Matches the first successful parser in [children], else fails.
 */
fun Choice (vararg children: Parser) = Parser(*children) { ctx ->
    children.stream()
        .map { it.parse(ctx) }
        .upThrough { it is Success }
        .maxWith(Furthest)
        ?: ctx.failure { "empty choice: ${toStringSimple()}" }
}

/**
 * Matches the parser in [children] which successfully matches the most input, else fails.
 */
fun Longest (vararg children: Parser) = Parser(*children) body@ { ctx ->
    val initial = ctx.snapshot()
    var bestSnapshot = initial
    var bestPos = -1
    var furthestFailure: Failure? = null
    for (child in children) {
        val result = child.parse(ctx)
        if (ctx.pos > bestPos) {
            bestPos = ctx.pos
            bestSnapshot = ctx.snapshot()
        }
        if (result !is Failure)
            ctx.restore(initial)
        else if (bestPos == -1)
            furthestFailure = Furthest.max(furthestFailure ?: result, result)
    }

    if (bestPos > -1) {
        ctx.restore(bestSnapshot) ; Success
    } else if (furthestFailure != null) furthestFailure
    else ctx.failure { "empty longest-match choice: ${toStringSimple()}" }
}

/// Lookahead //////////////////////////////////////////////////////////////////////////////////////

/**
 * Succeeds matching nothing if [child] succeeds, else fails.
 */
fun Lookahead (child: Parser) = Parser(child) { ctx ->
    ctx.snapshot().let { child.parse(ctx).andDo { ctx.restore(it) } }
}

/**
 * Succeeds matching nothing if [child] fails, or fails.
 */
fun Not (child: Parser) = Parser(child) { ctx ->
    val snapshot = ctx.snapshot()
    val result = child.parse(ctx)
    if (result is Success) {
        ctx.restore(snapshot)
        ctx.failure { child.toStringSimple() + " succeeded" }
    } else Success
}

/// Sequencing and Optionals ///////////////////////////////////////////////////////////////////////

/**
 * Matches the successful sequential invocation of all parsers in [children], else fails.
 */
fun Seq (vararg children: Parser) = Parser(*children) { ctx ->
    val snapshot = ctx.snapshot()
    children.stream()
        .map { it.parse(ctx) }
        .first { it is Failure }
        ?.apply { ctx.restore(snapshot) }
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
    child.parse(ctx) and { while (child.parse(ctx) is Success); Success }
}

/**
 * Matches [n] repetitions of [child], else fails.
 */
fun NTimes (n: Int, child: Parser) = Parser(child) body@ { ctx ->
    val snapshot = ctx.snapshot()
    for (i in 1..n) {
        val result = child.parse(ctx)
        if (result is Failure) {
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
}   }

/// Until //////////////////////////////////////////////////////////////////////////////////////////

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
        var err: Failure
        var cnt = 0
        while (true) {
            var res = until.parse(ctx)
            if (res is Success) break
            err = res as Failure
            res = repeat.parse(ctx)
            if (res is Failure) {
                ctx.restore(initial)
                return Furthest.max(err, res)
            }
            ++ cnt
            if (!matchUntil) snapshot = ctx.snapshot()
        }
        if (matchSome && cnt == 0) {
            ctx.restore(initial)
            return ctx.failure { "UntilSome did not match any repeatable item" }
        }
        ctx.restore(snapshot)
        return Success
    }

    init {
        definer  = "(${if (!matchUntil) "!" else ""}matchUntil, "
        definer += "${ if (!matchSome) "!" else ""}matchSome)"
    }
}

/// Failure Handling ///////////////////////////////////////////////////////////////////////////////

/**
 * Always succeeds matching nothing.
 */
fun Successful()
    = Parser { Success }

/**
 * Always fails.
 */
fun Failing()
    = Parser { it.failure() }

/**
 * Always fails, using [e] to construct the returned failure.
 */
fun Raise (e: Parser.(Context) -> Failure)
    = Parser { e(it) }

/**
 * Always fails, using [msg] to construct the message of the returned failure.
 */
fun RaiseMsg (msg: Parser.(Context) -> String)
    = Raise { it.failure(msg = msg)  }

/**
 * Throws a panic, using [e] to construct the thrown failure.
 */
fun Panic (e: Parser.(Context) -> Failure)
    = Parser { panic(e(it)) }

/**
 * Throws a panic, using [msg] to construct the message of the thrown failure.
 */
fun PanicMsg (msg: Parser.(Context) -> String)
    = Panic { it.failure(msg = msg)  }

/**
 * Matches [child], else throws the failure it returned.
 */
fun Paranoid (child: Parser) = Parser(child) { ctx ->
    ctx.parse(child) orDo { panic(it) }
}

/**
 * Matches [child], else fails. If [child] throws a failure that matches [pred] (matches
 * everything by default), returns it instead.
 */
fun Chill (child: Parser, pred: (Failure) -> Boolean = { true }) = Parser(child) { ctx ->
    tryParse(pred) { child.parse(ctx) }
}

/**
 * Matches [child], else raises the failure returned by [e].
 */
fun OrRaise (child: Parser, e: Parser.(Context) -> Failure) =  Parser(child) { ctx ->
    ctx.parse(child) or { e(ctx) }
}

/**
 * Matches this parser, else raises a failure with the message returned by [msg].
 */
fun OrRaiseMsg (child: Parser, msg: Parser.(Context) -> String)
    = OrRaise(child) { it.failure(msg = msg) }

/// Debugging //////////////////////////////////////////////////////////////////////////////////////

/**
 * Returns a parser that matches the same as the parser it is called on, but logs
 * the parsing state beforehand.
 */
fun AfterPrintingState (child: Parser) = Parser(child) { ctx ->
    ctx.logStream.println(ctx.stateString())
    child.parse(ctx)
}

/**
 * Returns a parser that matches the same as the parser it is called on, but logs
 * the parsing state afterwards.
 */
fun BeforePrintingState (child: Parser) = Parser(child) { ctx ->
    child.parse(ctx).after { ctx.logStream.println(ctx.stateString()) }
}

/// With ... ///////////////////////////////////////////////////////////////////////////////////////

/**
 * A parser that runs [f].
 * Compared to the [Parser] function, inverts the receiver and the parameter.
 */
fun WithContext(f: Context.(Parser) -> Result)
    = Parser { it.f(this) }

/**
 * Always succeeds after calling [f].
 */
fun DoWithContext(f: Context.(Parser) -> Unit)
    = Parser { it.f(this) ; Success }

/**
 * Returns a parser that invokes [child] then, if successful, returns the result
 * of [f], which is passed the input positions at which [child] was invoked.
 */
fun WithMatchStart (child: Parser, f: Parser.(Context, Int) -> Result) =
    Parser (child) { ctx ->
        val start = ctx.pos
        child.parse(ctx) and { f(ctx, start) }
    }

/**
 * Returns a parser that invokes [child] then, if successful, returns the result
 * of [f], which is passed the text matched by [child].
 */
fun WithMatchString (child: Parser, f: Parser.(Context, String) -> Result) =
    Parser (child) { ctx ->
        val start = ctx.pos
        child.parse(ctx) and { f(ctx, ctx.textFrom(start)) }
    }

/**
 * Like [WithMatchStart] but [f] always succeeds.
 */
fun DoWithMatchStart (child: Parser, f: Parser.(Context, Int) -> Unit)
    = WithMatchStart(child) { ctx, start -> f(ctx, start) ; Success }

/**
 * Like [WithMatchString] but [f] always succeeds.
 */
fun DoWithMatchString (child: Parser, f: Parser.(Context, String) -> Unit)
    = WithMatchString(child) { ctx, str -> f(ctx, str) ; Success }

/// AST Building ///////////////////////////////////////////////////////////////////////////////////

/**
 * Returns a parser that invokes [child] then, if successful, returns the result
 * of [f] with a [StackAccess] receiver.
 */
fun WithStack (child: Parser, f: StackAccess.() -> Result) = Parser(child) { ctx ->
    val stack = StackAccess(ctx, this, ctx.stack)
    child.parse(ctx) and { stack.prepareAccess() ; stack.f() }
}

/**
 * Like [WithStack], except [f] always succeeds.
 */
fun DoWithStack (child: Parser, f: StackAccess.() -> Unit) = Parser(child) { ctx ->
    val stack = StackAccess(ctx, this, ctx.stack)
    child.parse(ctx) andDo { stack.prepareAccess() ; stack.f() }
}

/**
 * Returns a parser that wraps this parser. If the wrapped parser succeeds, calls [node] with a
 * [StackAccess] and pushes the value it returns onto the stack.
 */
fun Build (child: Parser, node: StackAccess.() -> Any) = Parser(child) { ctx ->
    val stack = StackAccess(ctx, this, ctx.stack)
    child.parse(ctx)
        .andDo { stack.prepareAccess() ; stack.node().let { stack.push(it) } }
}

/**
 * Returns a parser that wraps this parser. If the wrapped parser succeeds, calls [node] with
 * the matched text as parameter, then push the returned object onto [Context.stack].
 *
 * Also see [Grammar.leaf].
 */
fun BuildLeaf (child: Parser, node: (String) -> Any)
    = child doWithMatchString { ctx, str -> ctx.stack.push(node(str)) } withDefiner "BuildLeaf"
/**
 * Returns a parser wrapping this parser. If the wrapped parser succeeds, tries to pop an item
 * from the result of [stack], returning an instance of [Maybe] depending on the result.
 */
fun BuildMaybe (child: Parser)
    = DoWithStack(child) {
        val maybe: Any? = maybe()
        if (maybe != null) stack.push(maybe)
    } withDefiner "BuildMaybe"

/**
 * Syntactic sugar for `BuildMaybe(Optional(child))`.
 * See [BuildMaybe], [Optional].
 */
fun BuildOptional (child: Parser)
    = BuildMaybe(Optional(child)) withDefiner "BuildOptional"

/**
 * Same as [Optional] but pushes a boolean on the stack depending on whether the parser matched.
 */
fun AsBool (child: Parser) = Parser (child) { ctx ->
    child.parse(ctx) or { Success } after { ctx.stack.push(it == Success) }
}

/**
 * Syntactic sugar for `Build(child) { rest<T>() }`.
 */
fun <T: Any> Collect (child: Parser)
    = Build(child) { rest<T>() } withDefiner "Collect"

/// Misc ///////////////////////////////////////////////////////////////////////////////////////////

/**
 * Returns a parser that uses [generator] to generate a parser at parse-time, run it and returns
 * and its result.
 */
fun Dynamic(generator: Parser.(Context) -> Parser)
    = Parser { ctx -> generator(ctx).parse(ctx) }

/**
 * Succeeds if [pred] holds, or fails with the failure generated by [fail]
 * (by default: [Parser.failure]).
 */
fun Predicate (fail: Parser.(Context) -> Failure = { it.failure() }, pred: Context.() -> Boolean)
    = Parser { ctx -> if (ctx.pred()) Success else fail(ctx) }

/**
 * Succeeds if [pred] holds, or fails with a failure using the message generated by [msg].
 */
fun PredicateMsg (msg: Parser.(Context) -> String, pred: Context.() -> Boolean)
    = Parser { ctx -> if (ctx.pred()) Success else ctx.failure(msg) }

////////////////////////////////////////////////////////////////////////////////////////////////////