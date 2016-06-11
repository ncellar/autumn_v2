package norswap.autumn
import norswap.violin.utils.plusAssign
import norswap.violin.stream.*

/**
 * Holds a type identifier for the token, its location within the input, as well as its derived
 * value (as determined by [Grammar.token]).
 */
data class Token<T: Any> (val type: Int, val start: Int, val end: Int, val value: T) {
    fun toString(ctx: Context)
        = "Token<${value.javaClass.simpleName}> (${ctx.rangeToString(start, end)}): $value"
}

data class TokenCacheEntry(
    val result: Result,
    val end: Int,
    val token: Token<*>?)

/**
 * Memoizes matched tokens by input position.
 * This is optional and must be added to the [Context] to be used.
 */
class TokenCache(val map: MutableMap<Int, TokenCacheEntry> = mutableMapOf())
: InertState<TokenCache>, MutableMap<Int, TokenCacheEntry> by map
{
    override fun snapshotString(snap: TokenCache, ctx: Context): String {
        val b = StringBuilder()
        b += "{"
        map.entries
            .stream().array()
            .apply { sortBy { it.key } }
            .stream()
            .each {
                val (k, v) = it
                if (v.result is Success)
                    b += "\n  from ${ctx.rangeToString(k, v.end)}: ${v.token.toString()}"
                else
                    b += "\n  at ${ctx.posToString(k)}: ${v.result.toString()}"
            }
        if (!map.isEmpty()) b += "\n"
        b += "}"
        return b.toString()
    }
}