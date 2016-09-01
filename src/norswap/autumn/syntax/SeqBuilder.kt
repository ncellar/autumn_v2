package norswap.autumn.syntax
import norswap.autumn.Parser
import norswap.autumn.ParserBuilder
import norswap.autumn.parsers.Seq
import norswap.autumn.utils.mutable

// =================================================================================================

// TODO change
/**
 * Class enabling the `(x .. y .. z).seq` sugar for `Seq(x, y, z)`.
 */
data class SeqBuilder (val list: MutableList<Parser>): ParserBuilder
{
    // ---------------------------------------------------------------------------------------------

    var commited = false
    lateinit var built: Seq

    // ---------------------------------------------------------------------------------------------

    override fun build (): Seq
    {
        if (!commited) {
            commited = true
            built = Seq(*list.toTypedArray())
        }
        return built
    }

    // ---------------------------------------------------------------------------------------------

    // TODO
    operator fun rangeTo (right: ParserBuilder): SeqBuilder
    {
        if (commited)
            return SeqBuilder(mutable(list + right.build()))

        list.add(right.build())
        return this
    }
}

// =================================================================================================

/**
 * Constructs a new [SeqBuilder] initially containing
 * a parser built from the received and [right].
 */
operator fun ParserBuilder.rangeTo (right: ParserBuilder)
    = SeqBuilder(mutableListOf(this.build(), right.build()))

// =================================================================================================