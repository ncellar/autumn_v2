package norswap.autumn.parsers
import norswap.autumn.Context
import norswap.autumn.Parser
import norswap.autumn.result.*

// Parsers that deal with failures.

// -------------------------------------------------------------------------------------------------

/**
 * Always succeeds matching nothing.
 */
class Succeed (): Parser()
{
    override fun _parse_(ctx: Context)
        = Success
}

// -------------------------------------------------------------------------------------------------

/**
 * Always fails with the failure returned by [f] (default: `this.failure(ctx)`).
 */
class Fail (val f: Parser.(Context) -> Failure = { failure(it) }): Parser()
{
    override fun _parse_(ctx: Context)
        = f(ctx)
}

// -------------------------------------------------------------------------------------------------

/**
 * Matches [child], else raises the failure returned by [f].
 */
class OrFail (val child: Parser, val f: Parser.(Context) -> Failure):  Parser(child)
{
    override fun _parse_(ctx: Context)
        = child.parse(ctx) or { f(ctx) }
}

// -------------------------------------------------------------------------------------------------

/**
 * Matches this parser. If it throws an exception, which satisfies [pred]
 * (matches everything by default), catch it and return a failure indicating this exception was
 * caught.
 */
class Catch (val child: Parser, val pred: (Throwable) -> Boolean = { true }): Parser(child)
{
    override fun _parse_(ctx: Context): Result
    {
        try {
            return child.parse(ctx)
        }
        catch (e: Throwable) {
            if (pred(e)) return failure(ctx, e)
            else throw e
        }
    }
}

// -------------------------------------------------------------------------------------------------