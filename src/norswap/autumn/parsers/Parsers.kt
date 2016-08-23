package norswap.autumn.parsers
import norswap.autumn.*
import norswap.autumn.result.*
import norswap.autumn.utils.dontRecordFailures
import norswap.violin.Maybe
import norswap.violin.stream.*
import norswap.violin.utils.after

/**
 * This file defines all of Autumn's pre-defined parsers.
 *
 * Syntactic sugars for many of those are to be found in file `Syntax.kt` of this package.
 */

/// Helpers ////////////////////////////////////////////////////////////////////////////////////////

/**
 * Returns the receiver after setting its definer to [definer].
 */
infix fun Parser.withDefiner(definer: String)
    = this after { this.definer = definer }

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
 * of [f] with a [StackAccess] receiver. Nodes pushed by [child] will be popped from the stack
 * if [pop] is true (default: true).
 */
fun WithStack (child: Parser, pop: Boolean = true, f: StackAccess.() -> Result) =
    Parser(child) { ctx ->
        val stack = StackAccess(ctx, this, pop)
        child.parse(ctx) and { stack.prepareAccess() ; stack.f() }
    }

/**
 * Like [WithStack], except [f] always succeeds.
 */
fun DoWithStack (child: Parser, pop: Boolean = true, f: StackAccess.() -> Unit) =
    Parser(child) { ctx ->
        val stack = StackAccess(ctx, this, pop)
        child.parse(ctx) andDo { stack.prepareAccess() ; stack.f() }
    }

/**
 * Returns a parser that wraps this parser. If the wrapped parser succeeds, calls [node] with a
 * [StackAccess] and pushes the value it returns onto the stack. Nodes pushed by [child] will be
 * popped from the stack.
 */
fun Build (child: Parser, node: StackAccess.() -> Any) = Parser(child) { ctx ->
    val stack = StackAccess(ctx, this, pop = true)
    child.parse(ctx)
        .andDo { stack.prepareAccess() ; stack.push(stack.node()) }
}

/**
 * Returns a parser that wraps this parser. If the wrapped parser succeeds, calls [node] with
 * the matched text as parameter, then push the returned object onto [Context.stack].
 *
 * Also see [Grammar.atom].
 */
fun Leaf (child: Parser, node: (String) -> Any)
    = child doWithMatchString { ctx, str -> ctx.stack.push(node(str)) } withDefiner "Leaf"

/**
 * Returns a parser wrapping this parser. If the wrapped parser succeeds, tries to pop an item
 * from [Context.stack], then push an instance of [Maybe] corresponding to the result.
 */
fun BuildMaybe (child: Parser)
    = DoWithStack(child) { stack.push(Maybe(items.getOrNull(0))) } withDefiner "BuildMaybe"

/**
 * Same as [Opt] but pushes a boolean on the stack depending on whether the parser matched.
 */
fun AsBool (child: Parser) = Parser (child) { ctx ->
    child.parse(ctx) after { ctx.stack.push(it == Success) } or { Success }
}

/**
 * Syntactic sugar for `Build(child) { rest<T>() }`.
 */
fun <T: Any> Collect (child: Parser)
    = Build(child) { rest<T>() } withDefiner "Collect"

/**
 * Invokes[child] and, if successful, pops an item of type [T] and a list of [T]s from
 * [Context.stack], then uses the [assoc] function to build a right associative structure
 * from these items. If the list is empty, returns the item directly. The right branch of the
 * structure is the left parameter (named r = result).
 *
 * example: `Binary(expr, +"=", expr).leftAssoc<Expr> { r, t -> Assign(r, t) }`
 */
fun <T : Any> RightAssoc(child: Parser, assoc: (r: T, t: T) -> T) =
    child.build {
        val list: List<T> = get(1)
        if (list.isEmpty()) get(0)
        else (Stream(get<T>(0)) then list.stream()).reduceRight(assoc)!!
    } withDefiner "RightAssoc"

/// Misc ///////////////////////////////////////////////////////////////////////////////////////////

/**
 * Matches [child], but don't track its internal failures to determine [Context.failure].
 */
class DontRecordFailures (val child: Parser): Parser(child)
{
    override fun _parse_(ctx: Context)
        = ctx.dontRecordFailures { child.parse(ctx) }
}

/**
 * Delegates the parsing to [child]. Use to create an alias of [child] with a different name.
 */
fun Alias (child: Parser)
    = Parser (child) { child.parse(it) }

/**
 * A parser that always succeeds, after executing [f].
 */
fun Perform (f: Parser.(Context) -> Unit)
    = Parser { ctx -> f(ctx) ; Success }

/**
 * Returns a parser that uses [generator] to generate a parser at parse-time, run it and returns
 * and its result.
 */
fun Dynamic (generator: Parser.(Context) -> Parser)
    = Parser { ctx -> generator(ctx).parse(ctx) }

/**
 * Succeeds if [pred] holds, or fails with the failure generated by [fail]
 * (by default: [Parser.failure]).
 */
fun Predicate (fail: Parser.(Context) -> Failure = { failure(it) }, pred: Parser.(Context) -> Boolean)
    = Parser { ctx -> if (pred(ctx)) Success else fail(ctx) }

/**
 * Succeeds if [pred] holds, or fails with a failure using the message generated by [msg].
 */
fun PredicateMsg (msg: Parser.(Context) -> String, pred: Parser.(Context) -> Boolean)
    = Parser { ctx -> if (pred(ctx)) Success else failure(ctx) { msg(ctx) } }

/**
 * Invokes [around] with a new context whose input is the text matched by [source].
 * The new context does not share state with the current context.
 */
fun Bounded (source: Parser, around: Parser) =
    WithMatchString(source) { ctx, str ->
        chill { around.parse(Context(str, SingletonGrammar(around))) }
    } withDefiner "Bounded"

/**
 * Invokes [f] with a new context whose input is the text matched by [source].
 * The new context does not share state with the current context, but [f] may access the old context
 * through lambda capture.
 */
fun Bounded (child: Parser, f: Parser.(Context) -> Result)
    = WithMatchString(child) { ctx, str ->
        chill { f(Context(str, SingletonGrammar(child))) }
    } withDefiner "Bounded"

////////////////////////////////////////////////////////////////////////////////////////////////////
