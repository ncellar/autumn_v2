package norswap.autumn
import norswap.autumn.parsers.*
import norswap.autumn.Grammar.TokenDisambiguation.*
import norswap.violin.stream.*
import kotlin.reflect.*
import kotlin.reflect.jvm.javaType

/**
 * Subclass this to create new grammars.
 *
 * As **last** statement of the subclass, put in the line:
 * `override val status = READY()`
 *
 * Within the body of the class, parser-valued fields will be indexed by the name of the property.
 * This enables creating dynamically checked recursive references via [ref] or its [not] shorthand.
 * If the same parser is assigned to multiple properties, keep the first name as the display name
 * and use the other names as referable aliases.
 * (This is enacted by the call to `READY()`).
 *
 * Don't forget to override [requiredStates] if required.
 *
 * # Tokenization
 *
 * The class also bundles special provisions for tokenization.  The basic rule is that at each input
 * position, there is at most one token (i.e. any ambiguities must be resolved at the lexical level
 * (the level of the token)). Users can register new token types with the [token] function, which
 * returns a parser.
 *
 * All parsers returned by [token] determine the type of token (if any) present at the given input
 * position. If multiple tokens could match, they are disambiguated by one of two methods
 * ([ORDERING] or [LONGEST_MATCH], depending on the value of [tokenDisambiguation]. The parsers
 * then check if the matched token is of the required type. If so, they push a [Token] value onto
 * [Context.stack].
 *
 * You can enable caching for tokens by passing a [TokenCache] to the [Context].
 */
abstract class Grammar
{
    /// SETTINGS ///////////////////////////////////////////////////////////////////////////////////

    /**
     * The parser used to skip whitespace after matching a token or a [plusAssign] string.
     */
    open val whitespace = ZeroMore(CharPred(Char::isWhitespace))

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

    /**
     * Override this function to indicate which states are required to parse the grammar correctly.
     */
    open fun requiredStates(): List<State<*,*>> = emptyList()

    /**
     * The root parser for this grammar. Used by [parse].
     */
    abstract val root: Parser

    /// NAME / PARSER MAPPING //////////////////////////////////////////////////////////////////////

    /**
     * Streams all properties of this class with type `Parser` (i.e. all parsers of this grammar).
     */
    private fun parsers(): Stream<KProperty<*>> =
        javaClass.kotlin.memberProperties.stream()
        .filter {
            !it.returnType.isMarkedNullable
            && Parser::class.java.isAssignableFrom(it.returnType.javaType as Class<*>)
            && it.name != "tokenParser"
        }

    /**
     * The set of all parser names in this grammar.
     */
    private val names = parsers().map { it.name }.set()

    /**
     * Maps parser names to the parser they designate.
     */
    private val map = mutableMapOf<String, Parser>()

    /**
     * Adding `override val status = READY()` at the end of the grammar assigns its
     * name to each parser and fills [map].
     */
    protected inner class READY() {
        init {
            parsers().each {
                val parser = it.call(this@Grammar) as Parser?
                parser ?: throw Error(
                     "`override val status = READY()` must appear **last** in the grammar.")

                // Multiple assignment from the same parser: keep the first name, use the others
                // as aliases.
                parser.name = parser.name ?: it.name
                map.put(it.name, parser)
            }

            val msg = "Could not match any token"
            val array = typedTokenParsers.toTypedArray()

            tokenParser = when(tokenDisambiguation) {
                ORDERING        -> Choice  (*array).orRaiseMsg { msg }
                LONGEST_MATCH   -> Longest (*array).orRaiseMsg { msg }
    }   }   }

    /**
     * Implement this as the **last** statement in the class with value `READY()`.
     */
    abstract protected val status: READY

    /// TOKENS /////////////////////////////////////////////////////////////////////////////////////

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
            this.parse(ctx).ifSuccess {
                ctx.stack.push(
                    Token(type, pos, ctx.pos, value(ctx.text.substring(pos, ctx.pos))))
                whitespace.parse(ctx)
            }   })

        /**
         * Returns the requested parser, which will match the next token (using the [TokenCache]
         * if available, or with [tokenParser]), then ensure the matched token is of
         * the requested type.
         */
        return Parser body@ { ctx ->
            val pos = ctx.pos
            val cache: TokenCache? = ctx.state_()
            cache?.get(pos) ?.let {
                ctx.pos = it.end
                if (it.token != null) ctx.stack.push(it.token)
                return@body it.result
            }
            val result = tokenParser.parse(ctx)
            val token = ctx.stack.peek() as Token<*>?
            cache?.put(pos, TokenCacheEntry(result, ctx.pos, token))
            return@body ctx.succeed { token?.type == type }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Creates a reference to the parser named by the string.
     * This reference is *not* dynamic: it will be resolved the first time it is used.
     *
     * Throws an error if the grammar does not possess a parser with the given name.
     */
    fun ref(name: String): Ref {
        if (!names.contains(name)) throw Error("unknown parser: $name")
        return Ref(name)
    }

    /**
     * `!"str" is a shorthand for `ref("str")` (see [ref]).
     */
    operator fun String.not(): Ref = ref(this)

    /**
     * +"str" is a shorthand for `Seq(Str("str"), whitespace)`.
     */
    operator fun String.unaryPlus() = Seq(Str(this), whitespace)

    /**
     * Returns the parse named [name] in this grammar.
     */
    operator fun get(name: String): Parser
        = map[name] ?: throw Error("unknown parser: $name")

    /**
     * Parse the given [text] using this grammar's [root], building a context that contains
     * this grammar's required [states] as well as [moreStates].
     */
    fun parse(text: String, vararg moreStates: State<*, *>): Result
        = Context(text, *(requiredStates().toTypedArray() + moreStates)).parse(root)

    /**
     * Syntactic sugar for `Seq(buildLeaf(node), whitespace)` ([buildLeaf]).
     */
    fun Parser.leaf(node: (String) -> Any) = Seq(buildLeaf(node), whitespace)

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
