package norswap.autumn
import norswap.autumn.result.*
import norswap.violin.Maybe
import norswap.violin.Stack
import norswap.violin.stream.*

// =================================================================================================

/**
 * This class gives a parser (the consumer) access to the nodes pushed on [stack] by another
 * parser (the producer). This is specifically designed with [MonotonicStack] in mind,
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
class StackAccess(
    val ctx: Context,
    val parser: Parser,
    val backargs: Int,
    val pop: Boolean,
    val stack: Stack<Any> = ctx.stack)
{
    private val size0: Int
    init { size0 = stack.size }
    lateinit var items: List<Any>

    // ---------------------------------------------------------------------------------------------

    /**
     * Position recorded when the stack access is created (in predefined parsers, always before
     * invoking any children).
     */
    val start = ctx.pos

    // ---------------------------------------------------------------------------------------------

    /**
     * Called before the StackAccess is passed to the user.
     */
    fun prepareAccess()
    {
        if (stack.size < size0)
            throw Exception("Stack shrunk beyond initial size")

        if (size0 < backargs)
            throw Exception("Requiring more back args than present on the stack")

        items = stack.stream()
            .limit(stack.size - size0 + backargs)
            .after { if (pop) stack.pop() }
            .list()
            .asReversed()
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * The string matched by [parser].
     */
    val str by lazy { ctx.textFrom(start) }

    // ---------------------------------------------------------------------------------------------

    /**
     * The index of the next item in the stack to be returned by [get].
     */
    var cur = 0

    // ---------------------------------------------------------------------------------------------

    /**
     * Retrieve the item at the specified index (default: [cur]++).
     * @throws Failure if the required position doesn't exist
     */
    @Suppress("UNCHECKED_CAST")
    fun <T: Any> get(i: Int = cur++): T
        = items.getOrNull(i) as T?
        ?: throw Error("No items at index $i (only ${items.size} items available)")

    // ---------------------------------------------------------------------------------------------

    /**
     * Uses [get] to retrieve an instance of [Maybe]`<T>` and returns
     * a corresponding nullable (through [Maybe.invoke]).
     */
    fun <T: Any> maybe(pos: Int = cur++): T?
        = get<Maybe<T>>(pos)()

    // ---------------------------------------------------------------------------------------------

    /**
     * Uses [get] to retrieve an instance of [Maybe]`<List<T>>` and returns
     * the contained list if there is one, or an empty list instead.
     */
    fun <T: Any> maybeList(pos: Int = cur++): List<T>
         = get<Maybe<List<T>>>(pos)() ?: listOf<T>()

    // ---------------------------------------------------------------------------------------------

    /**
     * Returns the remaining items as a list of the specified type.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T: Any> rest(): List<T>
        = items.subList(cur, items.size) as List<T>

    // ---------------------------------------------------------------------------------------------

    /**
     * Push an item on the stack.
     */
    fun push(item: Any) { stack.push(item) }

    // ---------------------------------------------------------------------------------------------

    /**
     * Ensures that all items pushed on the stack by the receiver are popped.
     */
    fun popAll() {
        if (!pop) repeat(stack.size - size0) { stack.pop() }
    }
}

// =================================================================================================

inline fun withStack (
    ctx: Context,
    parser: Parser,
    child: Parser,
    backargs: Int = 0,
    pop: Boolean = true,
    f: StackAccess.() -> Unit)
: Result
{
    val stack = StackAccess(ctx, parser, backargs, pop)
    val result = child.parse(ctx)
    if (result is Success) {
        stack.prepareAccess()
        stack.f()
    }
    return result
}

// =================================================================================================