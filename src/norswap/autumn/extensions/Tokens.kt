package norswap.autumn.extensions
import norswap.autumn.*
import norswap.autumn.parsers.*
import norswap.violin.utils.plusAssign
import norswap.violin.stream.*
import norswap.autumn.extensions.TokenGrammar.TokenDisambiguation.*

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Holds a type identifier for the token, its location within the input, as well as its derived
 * value (as determined by [Grammar.token]).
 */
data class Token<T: Any> (val type: Int, val start: Int, val end: Int, val value: T) {
    fun toString(ctx: Context)
        = "Token<${value.javaClass.simpleName}> (${ctx.rangeToString(start, end)}): $value"
}

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Adds lexical analysis (tokenization) emulation to [Grammar].
 *
 * The basic rule is that at each input position, there is at most one token (i.e. any ambiguities
 * must be resolved at the lexical level). Users can register new token types with the [token]
 * function, which returns a parser.
 *
 * All parsers returned by [token] determine the type of token (if any) present at the given input
 * position. If multiple types of tokens could match, they are disambiguated by one of two methods:
 * [ORDERING] or [LONGEST_MATCH], depending on the value of [tokenDisambiguation]. The parser
 * then checks if the matched token is of the required type. If so, it pushes a [Token] value onto
 * [Context.stack].
 *
 * You can enable caching for tokens by passing a [TokenCache] to the [Context].
 */
abstract class TokenGrammar: Grammar()
{
    /// SETTINGS ///////////////////////////////////////////////////////////////////////////////////

    /**
     * If multiple token types can match at an input position, how to select the correct
     * token type.
     */
    enum class TokenDisambiguation {
        /**
         * Select the correct token type by placing all token type syntax in an ordered
         * choice ([Choice]), in order of declaration.
         */
        ORDERING,
        /**
         * Select the correct token type by longest-match ([Longest]).
         */
        LONGEST_MATCH
    }

    /**
     * If multiple token types can match at an input position, how to select the correct
     * token type.
     */
    open val tokenDisambiguation = ORDERING;

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * This it the parser that matches a single token (of any kind).
     */
    private lateinit var tokenParser: Parser

    /**
     * The token type ID for the next token to be registered.
     */
    private var nextTokenType = 0

    /**
     * A list of parsers that parse a specific type of token, and if successful push the token
     * onto [Context.stack].
     */
    private var typedTokenParsers = mutableListOf<Parser>()

    override fun initialize() {
        super.initialize()
        val msg = "Could not match any token"
        val array = typedTokenParsers.toTypedArray()

        tokenParser = when (tokenDisambiguation) {
            ORDERING      -> Choice  (*array).orRaiseMsg { msg }
            LONGEST_MATCH -> Longest (*array).orRaiseMsg { msg }
        }   }

    /**
     * Returns a parser for a token whose syntax is defined by this parser and whose value
     * is built by [value], a function that takes the string matched by this parser as parameter.
     */
    fun <T: Any> Parser.token(value: (String) -> T): Parser
    {
        val type = nextTokenType ++

        /** See [typedTokenParsers]. */
        typedTokenParsers.add(Parser(this) { ctx ->
            val pos = ctx.pos
            this.parse(ctx) andDo {
                ctx.stack.push(
                    Token(type, pos, ctx.pos, value(ctx.text.substring(pos, ctx.pos))))
                whitespace.parse(ctx)
            }
        })

        /**
         * Returns the requested parser, which will match the next token (using the [TokenCache]
         * if available, or with [tokenParser]), then ensure the matched token is of
         * the requested type.
         */
        return Parser body@ { ctx ->
            val pos = ctx.pos
            val cache: TokenCache? = ctx.state_()
            cache?.get(pos)?.let {
                ctx.pos = it.end
                if (it.token != null) ctx.stack.push(it.token)
                return@body it.result
            }
            val result = tokenParser.parse(ctx)
            val token = ctx.stack.peek() as Token<*>?
            cache?.put(pos, TokenCacheEntry(result, ctx.pos, token))
            return@body succeed(ctx) { token?.type == type }
    }   }

    /**
     * Sugar for `this.token { it }`.
     */
    val Parser.token: Parser
        get() = token { it }

    /**
     * Sugar for `Str(this).token { it }`.
     */
    val String.keyword: Parser
        get() = Str(this).token { it }
}

////////////////////////////////////////////////////////////////////////////////////////////////////

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

////////////////////////////////////////////////////////////////////////////////////////////////////