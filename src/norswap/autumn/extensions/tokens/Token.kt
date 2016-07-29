package norswap.autumn.extensions.tokens
import norswap.autumn.Context

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Holds a type identifier for the token, its location within the input, as well as its derived
 * value (as determined by [Grammar.token]).
 */
data class Token<out T: Any> (
    val type: Int,
    val start: Int,
    val end: Int,
    val wEnd: Int,
    val value: T?)
{
    /**
     * Prints out "Token<T>" or "Token<Nothing>" if `value == null`.
     */
    override fun toString()
        = "Token<${value?.javaClass?.simpleName ?: "Nothing"}>"

    /**
     * Like [toString] but adds pretty-printed position information.
     */
    fun toStringWithPos (ctx: Context)
        = "$this (${ctx.rangeToString(start, end)} (+ ${wEnd - end}): $value"
}

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Indicates that no tokens could be matched.
 */
val NO_RESULT = Token<Nothing>(-1, -1, -1, -1, null)

////////////////////////////////////////////////////////////////////////////////////////////////////
