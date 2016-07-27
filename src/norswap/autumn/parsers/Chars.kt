package norswap.autumn.parsers
import norswap.autumn.Context
import norswap.autumn.Parser
import norswap.autumn.result.*
import norswap.autumn.utils.kEscape

// Parsers designed to match literal characters.

// -------------------------------------------------------------------------------------------------

private val Context.getc: Char
    get() = text[pos]

// -------------------------------------------------------------------------------------------------

/**
 * Matches any single character.
 */
object AnyChar: Parser()
{
    override fun _parse_(ctx: Context)
        = succeed(ctx) { ctx.getc != '\u0000' } andDo { ++ctx.pos }
}

// -------------------------------------------------------------------------------------------------

/**
 * Matches any single character matching the predicate [p], else fails.
 */
class CharPred (val p: (Char) -> Boolean): Parser()
{
    override fun _parse_(ctx: Context)
        = succeed(ctx) { p(ctx.getc) } andDo { ++ctx.pos }
}

// -------------------------------------------------------------------------------------------------

/**
 * Matches any single character in the (start, end) range.
 */
class CharRange (val start: Char, val end: Char): Parser()
{
    override fun _parse_(ctx: Context): Result
    {
        val c = ctx.getc

        if (start <= c && c <= end) {
            ++ctx.pos
            return Success
        }

        return failure(ctx)
    }
}

// -------------------------------------------------------------------------------------------------

/**
 * Matches any single character contained in [chars], else fails.
 */
class CharSet (vararg val chars: Char): Parser()
{
    init {
        val params = chars.map { "'${it.kEscape()}'" }.joinToString()
        definer = "CharSet(\"$params\")"
    }

    override fun _parse_(ctx: Context)
        = succeed(ctx) { chars.contains(ctx.getc) } andDo { ++ctx.pos }

}

// -------------------------------------------------------------------------------------------------

/**
 * Matches the string [str], else fails.
 */
class Str (val str: String): Parser()
{
    init { definer = "Str(\"${str.kEscape()}\")" }

    override fun _parse_(ctx: Context) =
        succeed(ctx) { ctx.text.regionMatches(ctx.pos, str, 0, str.length) }
            .andDo { ctx.pos += str.length }
}

// -------------------------------------------------------------------------------------------------