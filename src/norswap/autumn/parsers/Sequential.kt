package norswap.autumn.parsers
import norswap.autumn.Context
import norswap.autumn.Parser
import norswap.autumn.result.*
import norswap.violin.utils.after

// Parsers that match items sequentially.

// -------------------------------------------------------------------------------------------------

/**
 * Matches the successful sequential invocation of all parsers in [children], else fails.
 */
class Seq (vararg children: Parser): Parser(*children)
{
    override fun _parse_(ctx: Context): Result
    {
        val snapshot = ctx.snapshot()

        for (child in children) {
            val r = child.parse(ctx)
            if (r is Failure) {
                ctx.restore(snapshot)
                return r
            }
        }
        return Success
    }
}

// -------------------------------------------------------------------------------------------------

/**
 * Matches the same thing as [child], else succeeds matching nothing.
 */
class Opt (val child: Parser): Parser(child)
{
    override fun _parse_(ctx: Context)
        = child.parse(ctx) or { Success }
}

// -------------------------------------------------------------------------------------------------

/**
 * Matches the maximum number of successful sequential invocation of [child].
 */
class ZeroMore (val child: Parser): Parser(child)
{
    override fun _parse_(ctx: Context): Result
        = Success after { while (child.parse(ctx) is Success) ; }
}

// -------------------------------------------------------------------------------------------------

/**
 * Matches the maximum number of successful sequential invocation of [child].
 * Fails if no invocation succeeds.
 */
class OneMore (val child: Parser): Parser(child)
{
    override fun _parse_(ctx: Context)
        = child.parse(ctx) andDo { while (child.parse(ctx) is Success) ; }
}

// -------------------------------------------------------------------------------------------------

/**
 * Matches [n] repetitions of [child], else fails.
 */
class Repeat (val n: Int, val child: Parser): Parser(child)
{
    override fun _parse_(ctx: Context): Result
    {
        val snapshot = ctx.snapshot()
        for (i in 1..n) {
            val r = child.parse(ctx)
            if (r is Failure) {
                ctx.restore(snapshot)
                return r
            }
        }
        return Success
    }
}

// -------------------------------------------------------------------------------------------------

/**
 * Matches zero or more repetition of [item], separated by [sep].
 *
 * Equivalent to `Optional(Seq(item, ZeroMore(Seq(sep, item))))`
 */
class Around (val item: Parser, val sep: Parser): Parser(item, sep)
{
    override fun _parse_(ctx: Context): Result
    {
        val r = item.parse(ctx)
        if (r is Success) {
            while (true) {
                val r2 = transact(ctx) { sep.parse(ctx) and { item.parse(ctx) } }
                if (r2 is Failure) break;
            }
        }
        return Success
    }
}

// -------------------------------------------------------------------------------------------------

/**
 * Matches one or more repetition of [item], separated by [sep].
 *
 * Equivalent to `Seq(item, ZeroMore(Seq(sep, item)))`
 */
class Around1 (val item: Parser, val sep: Parser): Parser(item, sep)
{
    override fun _parse_(ctx: Context): Result {
        val r = item.parse(ctx)
        if (r is Success) {
            while (true) {
                val r2 = transact(ctx) { sep.parse(ctx) and { item.parse(ctx) } }
                if (r2 is Failure) break;
            }
            return Success
        } else return r
    }
}

// -------------------------------------------------------------------------------------------------