package norswap.autumn.result
import norswap.autumn.Parser
import norswap.autumn.Snapshot
import norswap.autumn.utils.Link

/**
 * A class of failures generated by [Parser.failure] whenever [DEBUG] is true.
 *
 * It is also returned by [Context.parse] (independently of [DEBUG]) if the  parser throws
 * an error.
 */
class DebugFailure constructor(
    pos: Int,
    parser: Parser,
    msg: () -> String,
    trace: Link<Pair<Parser, Int>>?,
    snapshot: Snapshot,
    cause: Throwable? = null
)
: Failure(pos, parser, msg, cause)
{
    // ---------------------------------------------------------------------------------------------

    /**
     * If [Context.debug] is set: The parsers instances involved in [throwable], in the same order.
     * null otherwise.
     */
    val trace = trace

    // ---------------------------------------------------------------------------------------------

    /**
     * A snapshot of the state at the time of failure.
     */
    val snapshot = snapshot
}
