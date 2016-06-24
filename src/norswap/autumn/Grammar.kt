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

    /// INITIALIZATION /////////////////////////////////////////////////////////////////////////////

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

    private val refs = mutableListOf<Ref>()
    private val recs = mutableMapOf<String, Rec>()
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
            // Multiple assignment from the same parser: keep the first name, use the others
            // as aliases.
            parser.name = parser.name ?: it.name
            if (parser is Ref) refs.add(parser)
            if (parser is Rec) recs.put(it.name, parser)
        }
        refs.forEach {
            val rec = recs[it.ref]
            if (rec == null) throw Exception("Unresolved reference: ${it.ref}")
            else it.child = rec
        }
        recs.clear()
        refs.clear()
        initialized = true
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * This is how a parse is started.
     *
     * Parse the given [text] using this grammar's [root], building a context that contains
     * this grammar's required [states] as well as [moreStates].
     *
     * If the parser throws an exception it will be caught and encapsulated in a [DebugFailure]
     * that will be returned. For panics, the failure is simply returned as such.
     */
    fun parse(text: String, vararg moreStates: State<*, *>): Result {
        initialize()
        val ctx = Context(text, *(requiredStates().toTypedArray() + moreStates))
        try {
            return root.parse(ctx)
        } catch (e: Carrier) {
            return e.failure
        } catch (e: Exception) {
            return DebugFailure(ctx.pos, { "exception thrown by parser" }, e,
                ctx.trace.link, ctx.snapshot())
        }
    }

    /// SYNTAX /////////////////////////////////////////////////////////////////////////////////////

    /**
     * `!"str"` is a shorthand for [Ref]`("str")`.
     */
    operator fun String.not() = Ref(this)

    /**
     * `!parser` is a shorthand for [Rec]`(parser)`.
     */
    operator fun Parser.not() = Rec(this)

    /**
     * +"str" is a shorthand for `Seq(Str("str"), whitespace)`.
     */
    operator fun String.unaryPlus() = Seq(Str(this), whitespace)

    /**
     * Syntactic sugar for `Seq(BuildLeaf(node), whitespace)` (see [Leaf]).
     */
    fun Parser.atom(node: (String) -> Any): Parser = Seq(Leaf(this, node), whitespace)

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
