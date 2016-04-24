package norswap.autumn
import norswap.autumn.Result.*
import norswap.violin.link.LinkList
import norswap.violin.stream.*
import norswap.violin.utils.*
import java.io.PrintStream
import kotlin.reflect.KClass

/**
 * The container for all shared state during a parse.
 */
class Context (input: String = "", vararg stateArgs: State<*,*>)
{
    /**
     * The current input position. Update this within parsers as they match input.
     *
     * This is in fact handled by an internal [State] but stored here for convenience.
     */
    var pos = 0

    /**
     * State to build some result form the parse (typically an AST).
     * A parser must not pop things from the stack that were not pushed by its descendants.
     */
    val stack = BottomUpStack<Any>()

    /**
     * State to handle left-recursion. Left public in case you want to implement something
     * similar, but you probably don't want to mess with this.
     */
    val seeds = Seeds()

    /**
     * The input string, null-terminated.
     */
    val text: String = input + "\u0000"

    /**
     * The line map can be used to convert between input positions and
     */
    val lineMap by lazy { LineMap(text) }

    /**
     * This controls how positions are reported in diagnostic and error messages.
     * Change this function if you want to use some other positioning scheme than (line, column),
     * in which case you should also change [rangeToString].
     */
    var posToString: (Int) -> String = { lineMap.offsetToString(it) }

    /**
     * This controls how input ranges (i.e. two input positions, first one inclusive, second one
     * exclusive) are reported in diagnostic and error messages. Change this function if you want
     * to use some other positioning scheme than (line, column),
     * in which case you should also change [posToString].
     */
    var rangeToString: (Int, Int) -> String = { s, e -> lineMap.offsetRangeToString(s, e) }

    /**
     * A shorthand for `posToString(pos)`.
     */
    val posStr: String /**/ get() = posToString(pos)

    /**
     * If debug mode is active, [error] will generate instances of [DebugError], which
     * include a lot more diagnostic information, at the cost of performance. Default: false
     */
    var debug = false

    /**
     * If true, prints a trace of the execution as a tree of parser invocations. Default: false
     */
    var logTrace = false

    /**
     * If [debug], this gets called before invoking any parser and logging its invocation.
     */
    var debugTraceBeforeHook: Context.(Parser) -> Unit = {}

    /**
     * If [debug], this gets called after invoking any parser and logging its invocation.
     */
    var debugTraceAfterHook: Context.(Parser) -> Unit = {}

    /**
     * The stream on which log information will appear. Default: [System.out]
     */
    var logStream: PrintStream = System.out

    /**
     * A stack of parsers whose invocation is ongoing, recorded if [debug] is true.
     */
    val trace = LinkList<Parser>()

    internal val dbg = Parser.LogState()
    internal val states: List<State<*,*>>
    private val stateMap: MutableMap<Class<out State<*,*>>, State<*,*>>
    private val position = Position(this)

    init {
        stateMap = mutableMapOf(
            position.javaClass to position,
            stack.javaClass to stack,
            seeds.javaClass to seeds)
        stateArgs.forEach { stateMap.put(it.javaClass, it) }
        states = stateMap.values.toList()
    }

    ///---------------------------------------------------------------------------------------------

    /**
     * This is how you start a parse!
     *
     * If the parser throws an exception it will be caught and encapsulated in a [DebugError]
     * that will be returned. For panics, the error is simply returned as such.
     */
    fun parse(p: Parser): Result {
        try {
            return p.parse(this)
        } catch (e: Carrier) {
            return e.error
        } catch (e: Exception) {
            return DebugError(pos, { "exception thrown by parser" }, e, trace.link, snapshot())
        }
    }

    // State Retrieval -------------------------------------------------------------------------



    /**
     * Returns the state instance of the class given by the type parameter or by [klass], if any,
     * or throw an exception.
     */
    inline fun <reified T: State<*,*>> state(klass: KClass<T> = T::class) = state(klass.java)

    /**
     * Retrieve the state instance of the given class, if any, or throw an exception.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T: State<*,*>> state(klass: Class<T>): T =
        state_(klass) ?: throw java.lang.Error("Unknown state type: '${klass.canonicalName}'")

    /**
     * Retrieve the state instance of the class given by the type parameter or by [klass], if any,
     * or null.
     */
    inline fun <reified T: State<*,*>> state_(klass: KClass<T> = T::class) = state_(klass.java)

    /**
     * Retrieve the state instance of the given class, if any, or null.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T: State<*,*>> state_(klass: Class<T>): T? = stateMap[klass] as T?

    /// State Operations -----------------------------------------------------------------------

    /**
     * Maps [State.snapshot] over all states.
     */
    fun snapshot(): Snapshot
        = Snapshot(states.stream().map { state -> state.snapshot() }.list())

    /**
     * Maps [State.restore] over all states, using a return value of [snapshot].
     */
    fun restore(snap: Snapshot)
        = snap.elems.forEachIndexed { i, s -> states[i].restore(s) }

    /**
     * Maps [State.diff] over all states, using a return value of [snapshot].
     */
    fun diff(snap: Snapshot): Delta
        = Delta(snap.elems.stream().indexed().map { states[it.index].diff(it.value) }.list())

    /**
     * Maps [State.merge] over all states, using a return value of [diff].
     */
    fun merge(delta: Delta)
         = delta.elems.forEachIndexed { i, d -> states[i].merge(d) }

    /**
     * Maps [State.equiv] over all states, using a return value of [snapshot].
     */
    fun equiv (pos: Int, snap: Snapshot): Boolean
        = states.stream().zip(snap.elems.stream()).all { it.first.equiv(pos, it.second) }

    /// Diagnostic ---------------------------------------------------------------------------------

    /**
     * Return a string representation of all states maintained by this context.
     */
    fun stateString(): String
        = snapshot().toString(this)

    /**
     * Returns a complete diagnostic of the result.
     *
     * This will include the reached input position in case of success, and the error message in case
     * of error. If the result is a [DebugError], a parse trace ([DebugError.trace]) will be
     * printed, along with either the exception or error message, and the state at the time of the
     * error.
     */
    fun diagnostic(result: Result): String {
        val b = StringBuilder()
        if (result is DebugError) {
            b += result.trace()
            b += "\n"
            if (result.throwable !is StackTrace)
                b += "Exception message: " + result.throwable.message
            else
                b += result
            b += "\n"
            b += result.snapshot.toString(this)
        }
        else if (result is Error)
            b += result
        else
            b += "Success up to $posStr"
        return b.toString()
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////

internal class Position (val ctx: Context): State<Int, Int>
{
    override fun snapshot() = ctx.pos
    override fun restore(snap: Int) { ctx.pos = snap }
    override fun diff(snap: Int) = ctx.pos
    override fun merge(delta: Int) { ctx.pos = delta }
    override fun equiv(pos: Int, snap: Int) = ctx.pos == snap
    override fun snapshotString(snap: Int, ctx: Context) = "$snap (${ctx.posToString(snap)})"
}

////////////////////////////////////////////////////////////////////////////////////////////////////
