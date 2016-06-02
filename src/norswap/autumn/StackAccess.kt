package norswap.autumn
import norswap.violin.Stack
import norswap.violin.maybe.*
import norswap.violin.stream.*

/**
 * This class defines helpers for accessing the wrapped [stack].
 *
 * The prime use case for this is the manipulation of AST node stacks.
 * The lifecycle of a StackAccess is specifically designed with the
 * [BottomUpStack] restriction in mind (only popping things pushed by descendant parsers) and does
 * not allow the full range of manipulation supported by [ValueStack].
 *
 * Within Autumn, StackAccess is used as both the receiver and first parameter (to enable the
 * `it` syntax) to the callback passed to [Grammar.build] and [Grammar.withStack].
 *
 * # Lifecycle
 *
 * Whenever the StackAccess is created, a special item is pushed on the stack. No items may be
 * popped from the stack that is below this special marker.
 *
 * Items may be pushed on the stack through [push]. In reality, the items are stored on the side,
 * and so do not interfere with the retrieval functions.
 *
 * When done using the stack access, the [commit] function should be called. This will remove the
 * marker from the stack and commit all pushes to the stack (in the same order as the calls to
 * [push]).
 *
 * # Access
 *
 * Use [get] to "index" the stack by depth.
 *
 * StackAccess also implement [PeekStream], which you can use to iterate over the items
 * in the stack. The Stream interface does not interfere with [get].
 *
 * The convenience methods [invoke], [unwrap] and [rest] build on this interface
 * to form a convenient stack access DSL.
 *
 * The stack depth of the next item that will be returned by [next] is available as [cur].
 * This enables depth-based indexing relative to the current position via [get].
 */
@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE", "unused")
class StackAccess(val stack: Stack<Any>): PeekStream<Any>
{
    private object StackMarker
    init { stack.push(StackMarker) }

    private val cache = mutableListOf<Any>()
    private val pushed = mutableListOf<Any>()
    private var overshoot = -1

    /**
     * The depth of the next item in the stack to be returned by the stream.
     */
    var cur = 0

    /**
     * Retrieve the item at the specified depth in the stack (the top item has depth 0).
     * The depth is relative to initial size of the stack when passed to this StackAccess:
     * `get(i)` always refers to the same item for any fixed `i`.
     *
     * If the depth points at or beyond the stack mark, returns null instead.
     */
    fun <T> get(i: Int): T? {
        val v1 = cache.getOrNull(i)
        if (v1 != null) return v1 as T
        for (j in cache.size..i) {
            val v2 = stack.pop()
            if (v2 == StackMarker) {
                overshoot = j
                return null
            }
            cache.add(v2!!)
        }
        return cache[i] as T
    }

    override fun peek(): Any? = get(cur)
    override fun next(): Any? = get(cur++)

    /**
     * Same as [get], but throws an error if the stack depth (as signaled by the mark) is exceeded.
     */
    operator fun <T> invoke(i: Int): T = get(i)
        ?: throw Error("Stack size is $overshoot, yet requiring item at depth $i")

    /**
     * Same as [next], but throws an error if the stack depth (as signaled by the mark) is exceeded.
     */
    inline operator fun <T: Any> invoke(): T = invoke(cur ++)

    /**
     * Uses [invoke] to retrieve an instance of `Maybe<T>` ([Maybe]) and returns
     * a corresponding nullable (through [Maybe.invoke]).
     */
    inline fun <T: Any> unwrap(i: Int): T? = invoke<Maybe<T>>(i)()

    /**
     * Uses [invoke] to retrieve an instance of `Maybe<T>` ([Maybe]) and returns
     * a corresponding nullable (through [Maybe.invoke]).
     */
    inline fun <T: Any> unwrap(): T? = unwrap(cur ++)

    /**
     * Returns the remainder of the stack as an array of the specified type.
     */
    inline fun <reified T: Any> rest(): Array<T> = map { it as T }.array()

    /**
     * Push an item on the stack. Pushes are stored on the side and are only committed after
     * calling [commit]. Pushes do not interface with access functions.
     */
    fun push(item: Any) { pushed.add(item) }

    /**
     * Removes the mark from the stack and commits all pushes to it.
     */
    fun commit() {
        while (stack.pop() != StackMarker);
        pushed.forEach { stack.push(it) }
    }
}