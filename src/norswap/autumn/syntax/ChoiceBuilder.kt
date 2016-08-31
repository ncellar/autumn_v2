package norswap.autumn.syntax
import norswap.autumn.Parser
import norswap.autumn.ParserBuilder
import norswap.autumn.parsers.Choice
import norswap.autumn.parsers.Longest

// =================================================================================================

// TODO change
/**
 * Class enabling the `(x / y / z).choice` sugar for `Choice(x, y, z)`.
 */
data class ChoiceBuilder (val list: MutableList<Parser>): ParserBuilder
{
    // ---------------------------------------------------------------------------------------------

    /** @suppress */
    var commited = false

    // ---------------------------------------------------------------------------------------------

    override fun build (): Parser
    {
        if (commited)
            throw Exception("Trying to build a choice builder more than once.")

        commited = true
        return Choice(*list.toTypedArray())
    }

    // ---------------------------------------------------------------------------------------------

    // TODO
    operator fun div (right: Parser): ChoiceBuilder
    {
        if (commited)
            throw Exception("Trying to mutate a choice builder after it has been built.")

        list.add(right)
        return this
    }

    // ---------------------------------------------------------------------------------------------

    // TODO kill
    val choice: Parser
        get() = build()
}

// =================================================================================================

/**
 * Constructs a new [ChoiceBuilder] initially containing
 * a parser built from the received and [right].
 */
operator fun ParserBuilder.div (right: Parser)
    = ChoiceBuilder(mutableListOf(this.build(), right))

// =================================================================================================

/**
 * Build a [Longest] parser out of the supplied ChoiceBuilder.
 *
 * This is analoguous to [ChoiceBuilder.build] except it returns a [Longest] instead
 * of a [Choice].
 */
inline fun longest (body: () -> ChoiceBuilder): Longest
{
    val b = body()

    if (b.commited)
        throw Exception("Trying to build a choice builder more than once.")

    b.commited = true
    return Longest(*b.list.toTypedArray())
}

// =================================================================================================