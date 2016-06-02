@file:Suppress("UNCHECKED_CAST")
package norswap.autumn
import norswap.violin.Copyable
import norswap.violin.stream.*
import norswap.violin.link.*
import norswap.violin.Stack

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * For states that never change, whose changes do not impact parsing, or where backtracking
 * over state changes never has adverse consequences.
 *
 * !! Carefully check that you're within the above parameter before using this kind of state.
 */
interface InertState<Self: InertState<Self>>: State<Self, InertState.SameState> {
    object SameState
    override fun snapshot() = this as Self
    override fun restore(snap: Self) {}
    override fun diff(snap: Self) = SameState
    override fun merge(delta: SameState) {}
    override fun equiv(pos: Int, snap: Self) = this === snap
    override fun snapshotString(snap: Self, ctx: Context) = "$this"
}

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * The actual state is held in the container [C], which is copied wholesale when snapshotting,
 * diffing, restoring and merging. This works well for states that just comprise a few fixed fields.
 */
open class CopyState<C: Copyable>(var container: C): State<C, C> {
    override fun snapshot(): C = container.copycast()
    override fun restore(snap: C) { container = snap.copycast() }
    override fun diff(snap: C) = snap
    override fun merge(delta: C) { container = delta.copycast() }
    override fun equiv(pos: Int, snap: C) = this == snap
    override fun snapshotString(snap: C, ctx: Context) = "$container"
}

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Pass value between parsers by pushing and popping them on the stack.
 */
open class ValueStack<T: Any> (private var stack: LinkList<T> = LinkList())
: State<LinkList<T>, LinkList<T>>, Stack<T> by stack
{
    override fun snapshot() = stack.clone()

    override fun restore(snap: LinkList<T>) { stack = snap.clone() }

    override fun diff(snap: LinkList<T>): LinkList<T> {
        if (snap.size > stack.size) illegalState()
        val stream = stack.linkStream()
        val diff = stream.limit(stack.size - snap.size).map { it.item }.linkList()
        if (stream.next() !== snap.link) illegalState()
        return diff
    }

    private fun illegalState() =
        IllegalAccessException("Supplied snapshot could not be a prefix of current stack.")

    override fun merge(delta: LinkList<T>)
        = delta.stream().each { stack.push(it) }

    override fun equiv(pos: Int, snap: LinkList<T>)
        = stack == snap

    override fun snapshotString(snap: LinkList<T>, ctx: Context)
        = "$snap"
}

/*

NOTE(norswap)

Here is an idea for a more efficient data-structure to support the value stack.
One would need to test it to see if it has any worth.

Currently, snapshot and restore are O(1), while diff and merge are proportional to the size
of the diff (O(size_diff)).

Instead of using a linked list, we could use an unbalanced binary tree. Snapshot and restore don't
move, but merge becomes O(1).

Diff has two distinct phases: first find the end of the prefix (O(size_diff)), then build
the diff (also O(size_diff)). Using a tree, the first phase does not move, but there is a
potential gain for building the diff: if we build it as a tree itself, we can reuse one branch of
the current tree as we make our way up. The downside: you need to build a diff tree instead of a
Bar, resulting in more allocations and memory use.

I envision the tree as a (value, after, afterafter) triplet. push/pop can be implemented naively
(emulate a linked list, don't use afterafter) or exploit the tree nature. But in that case, pop
will cause allocations, which is arguably worse. One unused field seems like the lesser evil.

 */

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * A stack that is scoped along the tree. A parser can only pop objects that were pushed on the
 * stack by its children. This property must be enforced by the user.
 */
open class BottomUpStack<T: Any>: ValueStack<T>() {
    override fun equiv(pos: Int, snap: LinkList<T>) = true
}

////////////////////////////////////////////////////////////////////////////////////////////////////
