package norswap.autumn.extensions
import norswap.autumn.*
import norswap.autumn.parsers.*
import norswap.violin.utils.plusAssign
import norswap.violin.stream.*
import norswap.autumn.extensions.TokenGrammar.TokenDisambiguation.*
import norswap.autumn.result.*

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Holds a type identifier for the token, its location within the input, as well as its derived
 * value (as determined by [Grammar.token]).
 */
data class Token<T: Any> (val type: Int, val start: Int, val end: Int, val wEnd: Int, val value: T?)
{
    override fun toString()
        = "Token<${value?.javaClass?.simpleName ?: "Nothing"}>"

    fun toStringWithPos (ctx: Context)
        = "$this (${ctx.rangeToString(start, end)}): $value"
}

////////////////////////////////////////////////////////////////////////////////////////////////////

val NO_RESULT = Token<Nothing>(-1, -1, -1, -1, null)

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
    open val tokenDisambiguation = ORDERING

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * This it the parser that matches a single token (of any kind).
     */
    internal lateinit var tokenParser: Parser

    /**
     * The token type ID for the next token to be registered.
     */
    private var nextTokenType = 0

    /**
     * List of [TokenTypeParser] for each registered token, indexed by token type ID.
     */
    internal var typeParsers = mutableListOf<TokenTypeParser>()

    /**
     * List of [TokenCheckParser] for each registered token, indexed by token type ID.
     */
    internal var checkParsers = mutableListOf<TokenCheckParser>()

    override fun initialize() {
        super.initialize()
        val msg = "Could not match any token"
        val array = typeParsers.toTypedArray()

        tokenParser = when (tokenDisambiguation) {
            ORDERING      -> Choice  (*array).orFail { failure(it) { msg } }
            LONGEST_MATCH -> Longest (*array).orFail { failure(it) { msg } }
        }   }

    /**
     * Returns a parser for a token whose syntax is defined by this parser and whose value
     * is built by [value], a function that takes the string matched by this parser as parameter.
     *
     * !! Excepted for the position, no state manipulation is allowed inside a token parser.
     */
    fun <T: Any> Parser.token(info: Boolean = false, value: (String) -> T?): Parser
    {
        val type = nextTokenType ++
        val typeParser = TokenTypeParser(type, this, value, this@TokenGrammar)
        val checkParser = TokenCheckParser(type, info, this@TokenGrammar)
        typeParsers.add(typeParser)
        checkParsers.add(checkParser)
        return checkParser
    }

    /**
     * Sugar for `this.token { it }`.
     */
    val Parser.token: Parser
        get() = token { it }

    /**
     * Sugar for `Str(this).token { null }`.
     */
    val String.keyword: Parser
        get() = Str(this).token { null }

    /**
     * Sugar for `Str(this).token { it }`.
     */
    val String.token: Parser
        get() = Str(this).token { it }
}

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Calls [target], and if successful, uses [value] to generate a value from the matched string,
 * wraps it into a [Token], and pushes this token onto [Context.stack].
 */
class TokenTypeParser (
    val type: Int,
    val target: Parser,
    val value: (String) -> Any?,
    val grammar: Grammar)
: Parser(target)
{
    override fun _parse_(ctx: Context): Result {
        val pos = ctx.pos
        val result = target.parse(ctx)
        if (result is Success) {
            val end = ctx.pos
            grammar.whitespace.parse(ctx)
            val token = Token(type, pos, end, ctx.pos, value(ctx.textFrom(pos)))
            ctx.stack.push(token)
        }
        return result
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Attempts to match a token, succeeding if the matched token is of type [type]. If successful,
 * pushes the [Token] onto [Context.stack] if [info], else pushes only its value (if present).
 */
class TokenCheckParser (val type: Int, val info: Boolean, val grammar: TokenGrammar): Parser()
{
    fun result(pos: Int, ctx: Context, token: Token<*>): Result
    {
        if (token === NO_RESULT) {
            ctx.pos = pos
            return failure(ctx) { "Could not match any token" }
        }

        if (token.type != type) {
            ctx.pos = pos
            return failure(ctx) {
                val expected
                    = this.name ?: grammar.typeParsers[type].fullString()
                val actual
                    = grammar.checkParsers[token.type].name ?: grammar.typeParsers[type].fullString()
                "Expected token type [$expected] but got [$actual] instead"
            }
        }

        if (info)
            ctx.stack.push(token)
        else if (token.value != null)
            ctx.stack.push(token.value)

        // TODO unfix here to check furthest
        ctx.pos = token.wEnd
        return Success
    }

    override fun _parse_(ctx: Context): Result
    {
        val pos = ctx.pos
        val cache: TokenCache? = ctx.state_()

        // use cached result if possible
        cache?.get(pos)?.let {
            return result(pos, ctx, it)
        }

        val result = grammar.tokenParser.parse(ctx)
        val token =
            if (result is Success) ctx.stack.pop() as Token<*>
            else NO_RESULT

        cache?.put(pos, token)
        return result(pos, ctx, token)
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Memoizes matched tokens by input position.
 * This is optional and must be added to the [Context] to be used.
 */
class TokenCache(val map: MutableMap<Int, Token<*>> = mutableMapOf())
: InertState<TokenCache>, MutableMap<Int, Token<*>> by map
{
    override fun snapshotString(snap: TokenCache, ctx: Context): String {
        val b = StringBuilder()
        b += "{"
        map.entries
            .stream().array()
            .apply { sortBy { it.key } }
            .stream()
            .each {
                val (pos, tok) = it
                if (tok !== NO_RESULT)
                    b += "\n  from ${ctx.rangeToString(pos, tok.end)}: $tok"
                else
                    b += "\n  at ${ctx.posToString(pos)}: no token"
            }
        if (!map.isEmpty()) b += "\n"
        b += "}"
        return b.toString()
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
