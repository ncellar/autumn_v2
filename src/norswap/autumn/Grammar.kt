package norswap.autumn
import norswap.autumn.parsers.*
import norswap.violin.stream.*
import kotlin.reflect.*
import kotlin.reflect.jvm.javaType

/**
 * Subclass this to create new grammars.
 *
 * Within the body of the class, parser-valued fields will be indexed by the name of the property.
 * This enables creating dynamically checked recursive references via [ref] or its [not] shorthand.
 *
 * If the same parser is assigned to multiple properties, the first name is kept as canonical name
 * (only this name can be used with [ref]).
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
    open fun requiredStates(): List<State<*, *>> = emptyList()

    /**
     * The root parser for this grammar. Used by [parse].
     */
    abstract val root: Parser

    /**
     * A map of names to the recursive parser they represent.
     */
    val recs = mutableMapOf<String, Rec>()

    /// INITIALIZATION /////////////////////////////////////////////////////////////////////////////

    /**
     * Streams all properties of this class with type `Parser` (i.e. all parsers of this grammar).
     */
    private fun parsers(): Stream<KProperty<*>> =
        javaClass.kotlin.memberProperties.stream()
            .filter {
                !it.returnType.isMarkedNullable
                    && it.returnType.javaType is Class<*>
                    && Parser::class.java.isAssignableFrom(it.returnType.javaType as Class<*>)
                    && it.name != "tokenParser"
            }

    private var initialized = false

    /**
     * This function is run when the grammar is "initialized", which occurs when [Context.parse]
     * or [Grammar.parse] is called with this grammar for the first time.
     *
     * You can override this if you extend [Grammar], but you must call the super-method.
     */
    open fun initialize() {
        if (initialized) return
        parsers().each {
            val parser = it.call(this) as Parser
            if (parser.name != null) {
                val warning
                    = "Warning: trying to assign a new name (${it.name}) to an already " +
                    "named parser (${parser.name}). Current name will be retained. " +
                    "Use `Alias` to make an alias."
                System.err.println(warning)
            }
            parser.name = parser.name ?: it.name
            // Still register the new name! It just won't be displayed when printing the parser.
            if (parser is Rec) recs.put(it.name, parser)
        }
        recs.values.forEach {
            if (it.child.name == null)
                it.child.name = it.name + " (rec target)"
        }
        initialized = true
    }

    /// SYNTAX /////////////////////////////////////////////////////////////////////////////////////

    /**
     * `!"str"` is a shorthand for [Ref]`("str", this)`.
     */
    operator fun String.not() = Ref(this, this@Grammar)

    /**
     * `!parser` is a shorthand for [Rec]`(parser)`.
     */
    operator fun Parser.not() = Rec(this)

    /**
     * +"str" is a shorthand for `Seq(Str("str"), whitespace)`.
     */
    operator fun String.unaryPlus() = Seq(Str(this), whitespace)

    /**
     * Syntactic sugar for `Seq(Leaf(this, node), whitespace)` (see [Leaf]).
     */
    fun Parser.atom(node: (String) -> Any): Parser = Seq(Leaf(this, node), whitespace)

    /**
     * Syntactic sugar for `Seq(this, whitespace)`.
     */
    val Parser.wp: Parser
        get() = Seq(this, whitespace)

    ////////////////////////////////////////////////////////////////////////////////////////////////
}

/**
 * Creates a grammar from a single parser.
 */
class SingletonGrammar (val parser: Parser): Grammar() {
    override val root = parser
}
