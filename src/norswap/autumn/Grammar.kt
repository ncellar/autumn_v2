package norswap.autumn
import norswap.autumn.parsers.*
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
 */
abstract class Grammar
{
    /// SETTINGS ///////////////////////////////////////////////////////////////////////////////////

    /**
     * The parser used to skip whitespace after matching a token or a [plusAssign] string.
     */
    open val whitespace = ZeroMore(CharPred(Char::isWhitespace))

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
    open protected inner class READY() {
        init {
            parsers().each {
                val parser = it.call(this@Grammar) as Parser?
                parser ?: throw Error(
                     "`override val status = READY()` must appear **last** in the grammar.")

                // Multiple assignment from the same parser: keep the first name, use the others
                // as aliases.
                parser.name = parser.name ?: it.name
                map.put(it.name, parser)
    }   }   }

    /**
     * Implement this as the **last** statement in the class with value `READY()`.
     */
    abstract protected val status: READY

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Parse the given [text] using this grammar's [root], building a context that contains
     * this grammar's required [states] as well as [moreStates].
     */
    fun parse(text: String, vararg moreStates: State<*, *>): Result
        = Context(text, *(requiredStates().toTypedArray() + moreStates)).parse(root)

    /// REFS ///////////////////////////////////////////////////////////////////////////////////////

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
     * Returns the parse named [name] in this grammar.
     */
    operator fun get(name: String): Parser
        = map[name] ?: throw Error("unknown parser: $name")

    /// SYNTAX /////////////////////////////////////////////////////////////////////////////////////

    /**
     * `!"str" is a shorthand for `ref("str")` (see [ref]).
     */
    operator fun String.not(): Ref = ref(this)

    /**
     * +"str" is a shorthand for `Seq(Str("str"), whitespace)`.
     */
    operator fun String.unaryPlus() = Seq(Str(this), whitespace)

    /**
     * Syntactic sugar for `Seq(buildLeaf(node), whitespace)` ([buildLeaf]).
     */
    fun Parser.leaf(node: (String) -> Any) = Seq(buildLeaf(node), whitespace)

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
