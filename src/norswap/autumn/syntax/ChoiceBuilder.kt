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

    override fun build (): Choice
    {
        if (commited)
            throw Exception("Trying to build a choice builder more than once.")

        commited = true
        return Choice(*list.toTypedArray())
    }

    // ---------------------------------------------------------------------------------------------

    // TODO
    operator fun div (right: ParserBuilder): ChoiceBuilder
    {
        if (commited)
            throw Exception("Trying to mutate a choice builder after it has been built.")

        list.add(right.build())
        return this
    }
}

// =================================================================================================

/**
 * Constructs a new [ChoiceBuilder] initially containing
 * a parser built from the received and [right].
 */
operator fun ParserBuilder.div (right: ParserBuilder)
    = ChoiceBuilder(mutableListOf(this.build(), right.build()))

// =================================================================================================

class ChoiceBuilderEnv
{
    val builder = ChoiceBuilder(mutableListOf())

    inline fun or (clause: () -> ParserBuilder): ChoiceBuilder
        = builder / clause()
}

// =================================================================================================

/**
 * Builds a [Choice] out of the supplied ChoiceBuilder.
 * This is just an alias for [rule] applying only to choices.
 */
inline fun choice (body: ChoiceBuilderEnv.() -> ChoiceBuilder): Choice
    = ChoiceBuilderEnv().body().build()

// =================================================================================================

/**
 * Build a [Longest] parser out of the supplied ChoiceBuilder.
 *
 * This is analoguous to [choice] except it returns a [Longest] instead
 * of a [Choice] (hence, it does *not* call [ChoiceBuilder.build] although it does provide
 * the same guarantees).
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