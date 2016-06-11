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
 * The actual state is held in the container [C], which is copied wholesale when snapshot,
 * diffing, restoring and merging. This works well for states that just comprise a few fixed fields.
 *
 * Two copy states are equivalent only if their content are identical,
 * hence [snapshot] == [diff] and [restore] == [merge].
 */
open class CopyState<C: Copyable>(var get: C): State<C, C> {
    override fun snapshot(): C = get.copycast()
    override fun restore(snap: C) { get = snap.copycast() }
    override fun diff(snap: C): C = get.copycast()
    override fun merge(delta: C) { get = delta.copycast() }
    override fun equiv(pos: Int, snap: C) = this == snap
    override fun snapshotString(snap: C, ctx: Context) = "$get"
}

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Pass value between parsers by pushing and popping them on the stack.
 *
 * Two stacks are equivalent only if they are identical,
 * hence [snapshot] == [diff] and [restore] == [merge].
 */
open class ValueStack<T: Any> (private var stack: LinkList<T> = LinkList())
: State<LinkList<T>, LinkList<T>>, Stack<T> by stack
{
    override fun snapshot() = stack.clone()
    override fun restore(snap: LinkList<T>) { stack = snap.clone() }
    override fun diff(snap: LinkList<T>): LinkList<T> = stack.clone()
    override fun merge(delta: LinkList<T>) { stack = delta.clone() }
    override fun equiv(pos: Int, snap: LinkList<T>) = stack == snap
    override fun snapshotString(snap: LinkList<T>, ctx: Context) = "$snap"
}

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * A stack with a special restriction: you can't call pass a snapshot to any operations if you might
 * have popped items from the stack since the snapshot was taken.
 *
 * Given that a parser does not know how its ancestors will use state operations, this means that
 * a parser can only pop objects that were pushed on the stack by itself and its children.
 *
 * !! These properties must be enforced by the user.
 *
 * The properties are naturally respected when using the stack to build an AST
 * in a bottom-up fashion.
 */
open class BottomUpStack<T: Any>(private var stack: LinkList<T> = LinkList())
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
        IllegalStateException("Supplied snapshot could not be a prefix of current stack.")

    override fun merge(delta: LinkList<T>)
        = delta.stream().each { stack.push(it) }

    override fun equiv(pos: Int, snap: LinkList<T>) = true
    override fun snapshotString(snap: LinkList<T>, ctx: Context) = "$snap"
}

////////////////////////////////////////////////////////////////////////////////////////////////////
