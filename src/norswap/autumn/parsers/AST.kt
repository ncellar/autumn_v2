package norswap.autumn.parsers
import norswap.autumn.Context
import norswap.autumn.Parser
import norswap.autumn.StackAccess
import norswap.autumn.result.*
import norswap.autumn.syntax.build
import norswap.autumn.withStack
import norswap.violin.Maybe
import norswap.violin.stream.*
import norswap.violin.utils.after

// Parsers used to build AST nodes.
// TODO dowithStack funciton

// -------------------------------------------------------------------------------------------------

/**
 * Returns a parser that invokes [child] then, if successful, returns the result
 * of [f] with a [StackAccess] receiver. Nodes pushed by [child] will be popped from the stack
 * if [pop] is true (default: true).
 */
class WithStack (
    val child: Parser,
    val pop: Boolean = true,
    val f: StackAccess.() -> Result)
: Parser(child)
{
    override fun _parse_(ctx: Context): Result
    {
        val stack = StackAccess(ctx, this, pop)
        return child.parse(ctx) and { stack.prepareAccess() ; stack.f() }
    }
}

// -------------------------------------------------------------------------------------------------

/**
 * Like [WithStack], except [f] always succeeds.
 */
class DoWithStack (
    val child: Parser,
    val pop: Boolean = true,
    val f: StackAccess.() -> Unit)
: Parser(child)
{
    override fun _parse_(ctx: Context): Result
        = withStack(ctx, this, child, pop, f)
}

// -------------------------------------------------------------------------------------------------

/**
 * Returns a parser that wraps this parser. If the wrapped parser succeeds, calls [node] with a
 * [StackAccess] and pushes the value it returns onto the stack. Nodes pushed by [child] will be
 * popped from the stack.
 */

class Build (
    val child: Parser,
    val node: StackAccess.() -> Any)
: Parser(child)
{
    override fun _parse_(ctx: Context): Result
        = withStack(ctx, this, child) { push(node()) }
}

// -------------------------------------------------------------------------------------------------

/**
 * Returns a parser that wraps this parser. If the wrapped parser succeeds, calls [node] with
 * the matched text as parameter, then push the returned object onto [Context.stack].
 *
 * Also see [Grammar.atom].
 */
class Leaf (
    val child: Parser,
    val node: (String) -> Any)
: Parser(child)
{
    override fun _parse_(ctx: Context): Result
    {
        val start = ctx.pos
        return child.parse(ctx) andDo {
            val match = ctx.textFrom(start)
            ctx.stack.push(node(match))
        }
    }
}

// -------------------------------------------------------------------------------------------------

/**
 * Returns a parser wrapping this parser. If the wrapped parser succeeds, tries to pop an item
 * from [Context.stack], then push an instance of [Maybe] corresponding to the result.
 */
class BuildMaybe (val child: Parser): Parser(child)
{
    override fun _parse_(ctx: Context): Result
        = withStack(ctx, this, child) { push(Maybe(items.getOrNull(0))) }
}

// -------------------------------------------------------------------------------------------------

/**
 * Same as [Opt] but pushes a boolean on the stack depending on whether the parser matched.
 * All items that [child] may have pushed on the stack are popped from it.
 */
class AsBool (val child: Parser): Parser(child)
{
    override fun _parse_(ctx: Context): Result
    {
        // StackAccess is used only to pop items pushed by child in case of success.
        val stack = StackAccess(ctx, this, pop = true)
        val res = child.parse(ctx) andDo { stack.prepareAccess() }
        return res after { ctx.stack.push(it == Success) } or { Success }
    }
}

// -------------------------------------------------------------------------------------------------

/**
 * Syntactic sugar for `Build(child) { rest<T>() }`.
 */
class Collect <T: Any> (val child: Parser): Parser(child)
{
    override fun _parse_(ctx: Context): Result
        = withStack(ctx, this, child) { push(rest<T>()) }
}

// -------------------------------------------------------------------------------------------------

class BuildPair (val child: Parser): Parser(child)
{
    override fun _parse_(ctx: Context): Result
        = withStack(ctx, this, child) { push(Pair<Any, Any>(get(), get())) }
}

// -------------------------------------------------------------------------------------------------

// TODO ??

/**
 * Invokes [child] and, if successful, pops an item of type [T] and a list of [T]s from
 * [Context.stack], then uses the [assoc] function to build a right associative structure
 * from these items. If the list is empty, returns the item directly. The right branch of the
 * structure is the left parameter (named r = result).
 *
 * example: `Binary(expr, +"=", expr).leftAssoc<Expr> { r, t -> Assign(r, t) }`
 */
fun <T: Any> RightAssoc (child: Parser, assoc: (r: T, t: T) -> T) =
    child.build {
        val list: List<T> = get(1)
        if (list.isEmpty()) get(0)
        else (Stream(get<T>(0)) then list.stream()).reduceRight(assoc)!!
    }


/**
 * See [RightAssoc].
 */
fun <T: Any> Parser.rightAssoc(assoc: (r: T, t: T) -> T)
    = RightAssoc<T>(this, assoc)

// -------------------------------------------------------------------------------------------------