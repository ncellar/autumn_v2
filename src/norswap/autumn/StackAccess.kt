package norswap.autumn
import norswap.violin.Stack
import norswap.violin.maybe.*
import norswap.violin.stream.*

/**
 * This class gives a parser (the consumer) access to the nodes pushed on [stack] by another
 * parser (the producer). This is specifically designed with [BottomUpStack] in mind,
 * typically to manipulate a stack of AST nodes.
 *
 * This works by pushing a marker on the stack before invoking the producer. After invoking
 * the producer, a call to [prepareAccess] unwinds the stack up to the marker, and exposes
 * the pushed items as a FIFO list (the first item pushed by the producer will be the first item
 * of the list).
 *
 * The fact that the stack is unwound means that the items pushed by the producer are lost
 * if the consumer doesn't use them.
 *
 * The pushed items may be accessed through the list [items], but more commonly it is accessed
 * through the [next], [invoke], [maybe], and [rest].
 *
 * Items may be pushed on the stack through [push].
 *
 * Within Autumn, StackAccess is used as both the receiver and first parameter (to enable the
 * `it` syntax) to the callback passed to [Grammar.build].
 *
 * The index of the next item that will be returned by [next] is available as [cur].
 * This enables indexing relative to the current position.
 */
@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE", "unused")
class StackAccess(val stack: Stack<Any>)
{
    private object StackMarker
    init { stack.push(StackMarker) }
    private lateinit var items: List<Any>

    /**
     * Called before the StackAccess is passed to the user.
     */
    fun prepareAccess() {
        items = stack.stream()
            .upTo { it == StackMarker }
            .apply { stack.pop() }
            .list()
            .asReversed()

        stack.pop() // pop the marker
    }

    /**
     * The index of the next item in the stack to be returned by [next].
     */
    var cur = 0

    /**
     * Retrieve the item at the specified index.
     * @throws Error if the required position doesn't exist
     */
    fun <T: Any> next(i: Int = cur++): T = items.getOrNull(i) as T?
        ?: throw Error("No items at index $i (only ${items.size} items available)")

    /**
     * Syntactic sugar for `next(pos)`.
     */
    inline operator fun <T: Any> invoke(pos: Int = cur++): T = next(pos)

    /**
     * Uses [next] to retrieve an instance of `Maybe<T>` ([Maybe]) and returns
     * a corresponding nullable (through [Maybe.invoke]).
     */
    inline fun <T: Any> maybe(pos: Int = cur++): T? = next<Maybe<T>>(pos)()

    /**
     * Returns the remaining items as a list of the specified type.
     */
    fun <T: Any> rest(): List<T> = items.subList(cur, items.size) as List<T>

    /**
     * Push an item on the stack.
     */
    fun push(item: Any) { stack.push(item) }
}