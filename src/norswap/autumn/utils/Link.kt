package norswap.autumn.utils

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Immutable singly linked list data structure. Use null as nil.
 */
class Link<out T: Any> (val item: T, val next: Link<T>?): Iterable<T>, Cloneable
{
    // ---------------------------------------------------------------------------------------------

    override fun iterator() = object: Iterator<T>
    {
        var link: Link<@UnsafeVariance T>? = this@Link

        override fun hasNext()
            = link != null

        override fun next(): T
            = link!!.item after { link = link?.next }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Returns a stream of the links composing this linked list.
     */
    fun linkIterator() = object: Iterator<Link<T>>
    {
        var link: Link<@UnsafeVariance T>? = this@Link

        override fun hasNext()
            = link != null

        override fun next(): Link<T>
            = link!! after { link = link?.next }
    }

    // ---------------------------------------------------------------------------------------------

    override public fun clone(): Link<T>
        = this

    // ---------------------------------------------------------------------------------------------

    override fun toString()
        = joinToString()
}

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Builds an immutable singly linked list from [items].
 *
 * The items are inserted right-to-left (so the last item is in the innermost link, and
 * iteration order is order in which the items appear).
 */
fun <T: Any> Link(vararg items: T): Link<T>?
    = items.foldRight<T, Link<T>?>(null) { it, r -> Link(it, r) }

/// Nullable Overloads /////////////////////////////////////////////////////////////////////////////

// -------------------------------------------------------------------------------------------------

/**
 * Returns the iterator of a potentially empty immutable linked list.
 */
operator fun <T: Any> Link<T>?.iterator(): Iterator<T>
    = this?.iterator() ?: emptyList<T>().iterator()

// -------------------------------------------------------------------------------------------------

/**
 * Returns the iterator of a potentially empty immutable linked list.
 */
fun <T: Any> Link<T>?.linkIterator(): Iterator<Link<T>>
    = this?.linkIterator() ?: emptyList<Link<T>>().iterator()

// -------------------------------------------------------------------------------------------------

/**
 * Returns a string representing a potentially empty immutable linked list.
 */
fun <T: Any> Link<T>?.toString()
    = this ?. joinToString() ?: ""

////////////////////////////////////////////////////////////////////////////////////////////////////