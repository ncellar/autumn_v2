package norswap.autumn.utils

/**
 * A mutable singly linked list implemented as a pointer to an immutable linked list ([Link])
 * and a size.
 *
 * [size] must be the real size of [link].
 *
 * The [equals] method is shallow: two link lists are equal if their [size] are the same and
 * their [link] are reference-equals.
 */
class LinkList<T: Any> (
    var link: Link<T>? = null,
    var size: Int = link ?. count() ?: 0)
: Cloneable, Iterable<T>
{
    // ---------------------------------------------------------------------------------------------

    /**
     * Builds a mutable linked list from [items].
     *
     * The items are inserted right-to-left (so the last item is in the innermost link, and
     * iteration order is order in which the items appear).
     */
    constructor (vararg items: T): this(null, 0)
    {
        items.reversed().forEach { push(it) }
    }

    // ---------------------------------------------------------------------------------------------

    fun push(item: T)
    {
        link = Link(item, link)
        ++size
    }

    // ---------------------------------------------------------------------------------------------

    fun peek()
        = link ?. item

    // ---------------------------------------------------------------------------------------------

    fun pop()
        = link ?. item ?. after { link = link?.next ; -- size }

    // ---------------------------------------------------------------------------------------------

    val empty: Boolean
        get() = size == 0

    // ---------------------------------------------------------------------------------------------

    /**
     * Return the item at the given depth. The item at depth 0 is the top of the stack.
     */
    fun at (depth: Int): T? =
        if (size <= depth) null
        else elementAt(depth + 1)

    // ---------------------------------------------------------------------------------------------

    /**
     * Pop items from the stack until its size is [target].
     * If `size > target`, does nothing.
     */
    fun truncate(target: Int) {
        while (size > target) pop()
    }

    // ---------------------------------------------------------------------------------------------

    override fun iterator(): Iterator<T>
         = link.iterator()

    // ---------------------------------------------------------------------------------------------

    override public fun clone(): LinkList<T>
        = LinkList(link, size)

    // ---------------------------------------------------------------------------------------------

    override fun toString()
        = joinToString()

    // ---------------------------------------------------------------------------------------------

    /**
     * Returns a stream of the links composing this linked list.
     */
    fun linkIterator(): Iterator<Link<T>>
        = link.linkIterator()

    // ---------------------------------------------------------------------------------------------

    fun linkIterable()
        = Iterable { linkIterator() }

    // ---------------------------------------------------------------------------------------------

    /**
     * Returns a link list similar to this one, excluding its first element.
     * If the list is empty, the result is another empty list.
     */
    fun tail(): LinkList<T>
        = LinkList(link?.next, if (size > 0) size - 1 else 0)

    // ---------------------------------------------------------------------------------------------

    /**
     * Shallow comparison: two link lists are equal if their [size] are the same and
     * their [link] are reference-equals.
     */
    override fun equals(other: Any?) =
        (other is LinkList<*>)
        && other.size === this.size
        && other.link === this.link

    // ---------------------------------------------------------------------------------------------

    override fun hashCode()
        = (link?.hashCode() ?: 0) * 31 + size
}

////////////////////////////////////////////////////////////////////////////////////////////////////

fun <T: Any> Iterable<T>.linkList(): LinkList<T>
{
    val list = LinkList<T>()
    forEach { list.push(it) }
    return list
}

////////////////////////////////////////////////////////////////////////////////////////////////////
