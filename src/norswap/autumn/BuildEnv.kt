package norswap.autumn
import norswap.autumn.result.*
import norswap.autumn.parsers.Build
import norswap.violin.Maybe
import norswap.violin.Stack
import norswap.violin.stream.*

// =================================================================================================

/**
 * This class is meant to be used by a parser (the consumer) to ease operations on stack items
 * generated by another parser (the producer) as well as on the text it matched.
 *
 * The class must be instantiated before invoking the producer.
 * Afterwards, [prepareAccess] must be called before using the instance.
 *
 * # Basic Stack Access
 *
 * The consumer is given access to the nodes pushed on [stack] by the producer.
 * This is specifically designed with [MonotonicStack] (e.g. [ctx.stack]) in mind, typically to
 * manipulate a stack of AST nodes.
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
 * # Back Args
 *
 * In addition to items pushed by the producer, the consumer can also access earlier stack
 * elements. Such elements are called "back args" and their number is controlled by the [backargs]
 * parameter. They are included in [items] like other elements (also in FIFO order).
 *
 * An important restriction on back args: they can't cross [BuildEnv] boundaries.
 *
 * For instance, in the example below, if A's body pops an item that was on the stack when the env
 * for B was instantiated, it will throw off B's body.
 *
 * parser
 *   .build(1) { ... } // A
 *   .build    { ... } // B
 *
 * # Start Position and Matched Text
 *
 * The class also gives access to the position at which the producer was invoked [start] and
 * to the matched text [str].
 *
 * # As Receiver
 *
 * It is typical to use the class as a receiver for callbacks passed to parser constructors
 * (e.g. [Build]).
 */
class BuildEnv(
    val ctx: Context,
    val parser: Parser,
    val backargs: Int,
    val pop: Boolean)
{
    private val size0: Int
    init { size0 = ctx.stack.size }
    lateinit var items: List<Any>

    // ---------------------------------------------------------------------------------------------

    /**
     * Position recorded when the stack access is created (in predefined parsers, always before
     * invoking any children).
     */
    val start = ctx.pos

    // ---------------------------------------------------------------------------------------------

    /**
     * To call before the the instance is used.
     */
    fun prepareAccess()
    {
        val stack = ctx.stack

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
    fun push(item: Any) { ctx.stack.push(item) }

    // ---------------------------------------------------------------------------------------------

    /**
     * Ensures that all items pushed on the stack by the receiver are popped.
     */
    fun popAll() {
        if (!pop) repeat(ctx.stack.size - size0) { ctx.stack.pop() }
    }
}

// =================================================================================================

/**
 * A shorthand for the process of instantiating a [BuildEnv], invoking [producer], calling
 * [BuildEnv.prepareAccess] and calling [f] (if the invocation succeeds).
 */
inline fun withStack (
    ctx: Context,
    producer: Parser,
    child: Parser,
    backargs: Int = 0,
    pop: Boolean = true,
    f: BuildEnv.() -> Unit)
: Result
{
    val env = BuildEnv(ctx, producer, backargs, pop)
    val result = child.parse(ctx)
    if (result is Success) {
        env.prepareAccess()
        env.f()
    }
    return result
}

// =================================================================================================