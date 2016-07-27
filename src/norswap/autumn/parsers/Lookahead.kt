package norswap.autumn.parsers
import norswap.autumn.Context
import norswap.autumn.Parser
import norswap.autumn.result.*

// Parsers that do not advance the input position.

// -------------------------------------------------------------------------------------------------

/**
 * Succeeds matching nothing if [child] succeeds, else fails.
 */
class Ahead (val child: Parser): Parser(child)
{
    override fun _parse_(ctx: Context)
        = ctx.snapshot().let { child.parse(ctx) andDo { ctx.restore(it) } }
}

// -------------------------------------------------------------------------------------------------

/**
 * Succeeds matching nothing if [child] fails, or fails.
 */
class Not (val child: Parser): Parser(child)
{
    override fun _parse_(ctx: Context): Result {
        val snapshot = ctx.snapshot()
        val result = child.parse(ctx)
        if (result is Success) {
            ctx.restore(snapshot)
            return failure(ctx) { "not operand succeeded" }
        }
        else return Success
    }
}

// -------------------------------------------------------------------------------------------------