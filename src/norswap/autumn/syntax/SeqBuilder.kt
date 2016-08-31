package norswap.autumn.syntax
import norswap.autumn.Parser
import norswap.autumn.ParserBuilder
import norswap.autumn.parsers.Seq

// =================================================================================================

// TODO change
/**
 * Class enabling the `(x .. y .. z).seq` sugar for `Seq(x, y, z)`.
 */
data class SeqBuilder (val list: MutableList<Parser>): ParserBuilder
{
    // ---------------------------------------------------------------------------------------------

    var commited = false

    // ---------------------------------------------------------------------------------------------

    override fun build (): Parser
    {
        if (commited)
            throw Exception("Trying to build a seq builder more than once.")

        commited = true
        return Seq(*list.toTypedArray())
    }

    // ---------------------------------------------------------------------------------------------

    // TOOD
    operator fun rangeTo (right: Parser): SeqBuilder
    {
        if (commited)
            throw Exception("Trying to mutate a seq builder after it has been built.")

        list.add(right)
        return this
    }

    // ---------------------------------------------------------------------------------------------

    // TODO kill
    val seq: Parser
        get() = build()
}

// =================================================================================================

/**
 * Constructs a new [SeqBuilder] initially containing
 * a parser built from the received and [right].
 */
operator fun Parser.rangeTo (right: Parser)
    = SeqBuilder(mutableListOf(this.build(), right))

// =================================================================================================