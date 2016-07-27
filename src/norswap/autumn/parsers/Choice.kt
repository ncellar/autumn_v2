package norswap.autumn.parsers
import norswap.autumn.Context
import norswap.autumn.Parser
import norswap.autumn.result.*

// Parsers designed to select a matching parser amongst multiple alternatives.

// -------------------------------------------------------------------------------------------------

/**
 * Matches the first successful parser in [children], else fails.
 */
class Choice (vararg children: Parser): Parser(*children)
{
    override fun _parse_(ctx: Context): Result
    {
        var fail: Failure? = null

        for (child in children) {
            val r = child.parse(ctx)
            if (r !is Failure) return r
            else if (fail == null) fail = r
            else fail = Furthest.max(fail, r)
        }

        return fail ?: failure(ctx) { "empty choice" }
    }
}

// -------------------------------------------------------------------------------------------------

/**
 * Matches the parser in [children] which successfully matches the most input, else fails.
 * If there is a tie (two parsers match the same amount of input), the first one wins.
 */
class Longest (vararg children: Parser): Parser(*children)
{
    override fun _parse_(ctx: Context): Result
    {
        val initial = ctx.snapshot()
        var bestSnapshot = initial
        var bestPos = -1
        var fail: Failure? = null

        for (child in children)
        {
            val r = child.parse(ctx)

            if (r !is Failure) {
                if (ctx.pos > bestPos) {
                    bestPos = ctx.pos
                    bestSnapshot = ctx.snapshot()
                }
                ctx.restore(initial)
            }
            else if (bestPos == -1) {
                fail = Furthest.max(fail ?: r, r)
            }
        }

        if (bestPos > -1) {
            ctx.restore(bestSnapshot)
            return Success
        }
        else {
            return fail ?: failure(ctx) { "empty longest-match choice" }
        }
    }
}

// -------------------------------------------------------------------------------------------------