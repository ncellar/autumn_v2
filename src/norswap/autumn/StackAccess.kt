package norswap.autumn
import norswap.violin.Maybe
import norswap.violin.Stack
import norswap.violin.stream.*

/**
 * This class gives a parser (the consumer) access to the nodes pushed on [stack] by another
 * parser (the producer). This is specifically designed with [BottomUpStack] in mind,
 * typically to manipulate a stack of AST nodes.
 *
 * This works by saving the stack size before invoking the producer. After invoking
 * the producer, a call to [prepareAccess] collects the nodes pushed by the producer
 * into the [items] list (in the same order as they were pushed - FIFO instead of LIFO).
 *
 * The pushed items are usually accessed through [get], [maybe] and [rest].
 * Items may be pushed on the stack through [push].
 *
 * The index of the next item that will be returned by [get] is available as [cur].
 * This enables indexing relative to the current position.
 *
 * The [pop] parameter determines whether the nodes pushed by the producer should be popped
 * from the stack after having been collected into [items].
 *
 * Within Autumn, StackAccess is used as the receiver and to the callback passed to [Grammar.build].
 */
class StackAccess(val ctx: Context, val parser: Parser, val stack: Stack<Any>, val pop: Boolean)
{
    private val size0: Int
    init { size0 = stack.size }
    private lateinit var items: List<Any>

    /**
     * Called before the StackAccess is passed to the user.
     */
    fun prepareAccess() {
        items = stack.stream()
            .limit(stack.size - size0)
            .apply { if (pop) stack.pop() }
            .list()
            .asReversed()
    }

    /**
     * The index of the next item in the stack to be returned by [get].
     */
    var cur = 0

    /**
     * Retrieve the item at the specified index (default: [cur]++).
     * @throws Failure if the required position doesn't exist
     */
    @Suppress("UNCHECKED_CAST")
    fun <T: Any> get(i: Int = cur++): T
        = items.getOrNull(i) as T?
        ?: throw Error("No items at index $i (only ${items.size} items available)")

    /**
     * Same as [get] parameterized with [Any].
     */
    fun get_(i: Int = cur++): Any = get(i)

    /**
     * Uses [get] to retrieve an instance of `Maybe<T>` ([Maybe]) and returns
     * a corresponding nullable (through [Maybe.invoke]).
     */
    fun <T: Any> maybe(pos: Int = cur++): T?
        = get<Maybe<T>>(pos)()

    /**
     * Returns the remaining items as a list of the specified type.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T: Any> rest(): List<T>
        = items.subList(cur, items.size) as List<T>

    /**
     * Push an item on the stack.
     */
    fun push(item: Any) { stack.push(item) }

    /**
     * Ensures that all items pushed on the stack by the receiver are popped.
     */
    fun popAll() {
        if (!pop) repeat(stack.size - size0) { stack.pop() }
    }
}
