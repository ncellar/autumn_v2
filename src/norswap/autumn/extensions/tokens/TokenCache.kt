package norswap.autumn.extensions.tokens
import norswap.autumn.Context
import norswap.autumn.utils.plusAssign
import java.util.HashMap

/**
 * Memoizes matched tokens by input position.
 * Can be enabled/disable through [TokenGrammar.cacheTokens].
 */
class TokenCache: HashMap<Int, Token<*>>()
{
    fun toString (ctx: Context): String
    {
        val b = StringBuilder()
        b += "{"
        entries.toTypedArray()
            .apply { sortBy { it.key } }
            .forEach {
                val (pos, tok) = it
                if (tok !== NO_RESULT) {
                    val name = (ctx.grammar as TokenGrammar).parserName(tok.type)
                    b += "\n  from ${ctx.rangeToString(pos, tok.end)}: $tok - $name"
                }
                else
                    b += "\n  at ${ctx.posToString(pos)}: no token"
            }
        if (!isEmpty()) b += "\n"
        b += "}"
        return b.toString()
    }
}