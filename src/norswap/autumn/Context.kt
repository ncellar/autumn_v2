@file:Suppress("CanBePrimaryConstructorProperty")
package norswap.autumn
import norswap.autumn.result.*
import norswap.autumn.utils.LinkList
import norswap.autumn.utils.dontRecordFailures
import norswap.autumn.utils.expandTabsAndNullTerminate
import norswap.autumn.utils.linkList
import java.io.PrintStream
import kotlin.reflect.KClass

/**
 * The container for all shared state during a parse.
 */
class Context (input: String = "", grammar: Grammar, vararg stateArgs: State<*,*>)
{
    /**
     * The grammar to which this context is associated.
     */
    val grammar = grammar

    /**
     * The current input position. Update this within parsers as they match input.
     *
     * This is in fact handled by an internal [State] but stored here for convenience.
     */
    var pos = 0

    /**
     * The furthest failure encountered while parsing the current parser. [Success] means no
     * failures where encountered. Inhibit this recording by using [dontRecordFailures].
     *
     * This is in fact handled by an internal [State] but stored here for convenience.
     */
    var failure: Result = Success

    /**
     * State to build some result form the parse (typically an AST).
     * A parser must not pop things from the stack that were not pushed by its descendants.
     */
    var stack = LinkList<Any>()

    /**
     * State to handle left-recursion. Left public in case you want to implement something
     * similar, but you probably don't want to mess with this.
     */
    val seeds = Seeds()

    /**
     * The input string, null-terminated.
     */
    val text: String = input.expandTabsAndNullTerminate(TAB_SIZE)

    /**
     * Syntactic sugar for `this.text.substring(origin, this.pos)`.
     */
    fun textFrom(origin: Int) = text.substring(origin, pos)

    /**
     * The line map can be used to convert between input positions and
     */
    val lineMap by lazy { LineMap(text) }

    /**
     * This controls how positions are reported in diagnostic and failure messages.
     * Change this function if you want to use some other positioning scheme than (line, column),
     * in which case you should also change [rangeToString].
     */
    var posToString: (Int) -> String = { lineMap.offsetToString(it) }

    /**
     * This controls how input ranges (i.e. two input positions, first one inclusive, second one
     * exclusive) are reported in diagnostic and failure messages. Change this function if you want
     * to use some other positioning scheme than (line, column),
     * in which case you should also change [posToString].
     */
    var rangeToString: (Int, Int) -> String = { s, e -> lineMap.offsetRangeToString(s, e) }

    /**
     * A shorthand for `posToString(pos)`.
     */
    val posStr: String /**/ get() = posToString(pos)

    /**
     * The stream on which log information will appear. Default: [System.out]
     */
    var logStream: PrintStream = System.out

    /**
     * A stack of parsers (+ position) whose invocation is ongoing, recorded if [DEBUG] is true.
     */
    val trace = LinkList<Pair<Parser, Int>>()

    internal val states: List<State<*,*>>
    private  val stateMap: MutableMap<Class<out State<*,*>>, State<*,*>>

    init {
        stateMap = mutableMapOf(seeds.javaClass to seeds)
        grammar.requiredStates().forEach { stateMap.put(it.javaClass, it) }
        stateArgs               .forEach { stateMap.put(it.javaClass, it) }
        states = stateMap.values.toList()
    }

    /// Start Parse --------------------------------------------------------------------------------

    /**
     * This is how a parse is started.
     *
     * Parses [text] using [grammar]'s root ([Grammar.root]).
     * Parse the given [text] using this grammar's [root], building a context that contains
     * this grammar's required [states] as well as [moreStates].
     *
     * If the parser throws an exception it will be caught and encapsulated in a [DebugFailure]
     * that will be returned.
     *
     * If the root does not match the whole input, return the furthest encountered error.
     * If this is not the desired behaviour, use [parsePrefix].
     */
    fun parse(): Result
    {
        var r = parsePrefix()

        if (r is Success && pos < text.length - 1)
            if (failure is Failure && (failure as Failure).pos >= pos)
                r = failure
            else
                r = grammar.root.failure(this) { "Input remaining, matched up to $posStr" }

        return r
    }

    /**
     * Same as [parse], but allows the grammar root to match only a prefix of the input.
     */
    fun parsePrefix(): Result
    {
        grammar.initialize()
        return try { grammar.root.parse(this) }
        catch (e: Throwable) { grammar.root.failure(this, e) }
    }

    /// State Retrieval ----------------------------------------------------------------------------

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
        state_(klass) ?: throw Error("Unknown state type: '${klass.canonicalName}'")

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
    fun snapshot (): Snapshot
        = Snapshot(pos, stack.clone(), states.map { it.snapshot() })

    // ---------------------------------------------------------------------------------------------

    /**
     * Maps [State.restore] over all states, using a return value of [snapshot].
     */
    fun restore (snap: Snapshot)
    {
        pos = snap.pos
        if (stack != snap.stack)
            stack = snap.stack.clone()

        snap.elems.forEachIndexed { i, s -> states[i].restore(s) }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Maps [State.diff] over all states, using a return value of [snapshot].
     */
    fun diff (snap: Snapshot): Delta
    {
        fun illegalState() =
            IllegalStateException("Supplied snapshot could not be a prefix of current stack.")

        val snapstack = snap.stack

        if (snapstack.size > stack.size)
            illegalState()

        val stream = stack.linkIterable()
        val sizeDiff = stack.size - snapstack.size
        val stackDiff = stream.take(sizeDiff).map { it.item }.linkList()

        if (stream.elementAtOrNull(sizeDiff) !== snapstack.link)
            illegalState()

        return Delta(pos, stackDiff, snap.elems.mapIndexed { i, d -> states[i].diff(d) })
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Maps [State.merge] over all states, using a return value of [diff].
     */
    fun merge (delta: Delta)
    {
        pos = delta.pos
        delta.stackDiff.forEach { stack.push(it) }
        delta.elems.forEachIndexed { i, d -> states[i].merge(d) }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Maps [State.equiv] over all states, using a return value of [snapshot].
     */
    fun equiv (snap: Snapshot): Boolean
    {
        var i = 0
        return pos == snap.pos && states.all { it.equiv(pos, snap.elems[i++]) }
    }

    /// Diagnostic ---------------------------------------------------------------------------------

    /**
     * Syntactic sugar for [logStream]`.println(msg)`.
     */
    fun log(msg: Any)
        = logStream.println(msg)

    /**
     * If [DEBUG] is true, prints a parse trace (using [trace]) to [logStream].
     */
    fun logTrace() {
        if (!DEBUG) return
        val failure = DebugFailure(pos, trace.peek()!!.first, {""}, trace.link, snapshot())
        val trace = trace(this, failure)
        logStream.println("Trace\n" + trace.removeRange(0..trace.indexOf('\n')))
    }

    /**
     * Return a string representation of all states maintained by this context.
     */
    fun stateString(): String
        = snapshot().toString(this)
}

////////////////////////////////////////////////////////////////////////////////////////////////////
