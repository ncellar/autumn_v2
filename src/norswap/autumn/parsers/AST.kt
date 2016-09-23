package norswap.autumn.parsers
import norswap.autumn.Context
import norswap.autumn.Parser
import norswap.autumn.BuildEnv
import norswap.autumn.result.*
import norswap.autumn.withStack
import norswap.autumn.utils.Maybe
import norswap.autumn.utils.None
import norswap.autumn.utils.after

// Parsers used to build AST nodes.

// -------------------------------------------------------------------------------------------------

/**
 * Returns a parser that invokes [child] then, if successful, returns the result
 * of [f] with a [BuildEnv] receiver. Nodes pushed by [child] will be popped from the stack
 * if [pop] is true (default: true).
 */
class WithStack (
    val child: Parser,
    val backargs: Int = 0,
    val pop: Boolean = true,
    val f: BuildEnv.() -> Result)
: Parser(child)
{
    override fun _parse_(ctx: Context): Result
    {
        val stack = BuildEnv(ctx, this, backargs, pop)
        return child.parse(ctx) and { stack.prepareAccess() ; stack.f() }
    }
}

// -------------------------------------------------------------------------------------------------

/**
 * Returns a parser that wraps this parser. If the wrapped parser succeeds, calls [node] with a
 * [BuildEnv] and pushes the value it returns onto the stack. Nodes pushed by [child] will be
 * popped from the stack.
 */

class Build (
    val child: Parser,
    val backargs: Int = 0,
    val node: BuildEnv.() -> Any)
: Parser(child)
{
    override fun _parse_(ctx: Context): Result
        = withStack(ctx, this, child, backargs) { push(node()) }
}

// -------------------------------------------------------------------------------------------------

/**
 * Returns a parser wrapping this parser. If the wrapped parser succeeds, tries to pop an item
 * from [Context.stack], then pushes [Some] or [None] depending on whether the pop suceeded.
 * If it fails, pushes [None].
 */
class BuildMaybe (val child: Parser): Parser(child)
{
    override fun _parse_(ctx: Context): Result
    {
        val stack = BuildEnv(ctx, this, 0, pop = true)
        val result = child.parse(ctx)
        if (result is Success) {
            stack.prepareAccess()
            stack.push(Maybe(stack.items.getOrNull(0)))
        }
        else {
            stack.push(None)
        }
        return Success
    }
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
        val size0 = ctx.stack.size
        val res = child.parse(ctx) andDo { ctx.stack.truncate(size0) }
        return res after { ctx.stack.push(it == Success) } or { Success }
    }
}

// -------------------------------------------------------------------------------------------------

/**
 * Collects all items pushed on the stack by [child] (assumed to be of type [T]) into a list,
 * which is itself pushed on the stack.
 */
class Collect <T: Any> (val child: Parser, val backargs: Int = 0): Parser(child)
{
    override fun _parse_(ctx: Context): Result
        = withStack(ctx, this, child, backargs) { push(rest<T>()) }
}

// -------------------------------------------------------------------------------------------------

/**
 * Pops two items from the stack, creates a [Pair] out of them (left = first pushed) and pushes
 * it on the stack. Any other items pushed on the stack by [child] are discarded.
 */
class BuildPair (val child: Parser, val backargs: Int = 0): Parser(child)
{
    override fun _parse_(ctx: Context): Result
        = withStack(ctx, this, child, backargs) { push(Pair<Any, Any>(get(), get())) }
}

// -------------------------------------------------------------------------------------------------