package norswap.autumn.result
import norswap.autumn.Autumn
import norswap.autumn.Parser
import norswap.autumn.Snapshot
import norswap.autumn.utils.isMethod
import norswap.violin.link.Link
import norswap.violin.link.stream
import norswap.violin.stream.*
import norswap.violin.utils.plusAssign

/**
 * A class of failures generated by [Parser.failure] whenever [Context.debug] is true.
 *
 * It is also returned by [Context.parse] (independently of [Context.debug]) if the  parser throws
 * an error (but not a panic).
 */
class DebugFailure(
    pos: Int,
    parser: Parser,
    msg: () -> String,
    parserTrace: Link<Parser>?,
    snapshot: Snapshot,
    throwable: Throwable = StackTrace()
)
: Failure(pos, parser, msg)
{
    // ---------------------------------------------------------------------------------------------

    private class StackTrace : Throwable()

    // ---------------------------------------------------------------------------------------------

    /**
     * Either something that the parser threw and that was caught by [Context.parse],
     * or an instance of [StackTrace] in debug mode.
     */
    val throwable = throwable

    // ---------------------------------------------------------------------------------------------

    /**
     * If [Context.debug] is set: The parsers instances involved in [throwable], in the same order.
     * null otherwise.
     */
    val parserTrace = parserTrace

    // ---------------------------------------------------------------------------------------------

    /**
     * A snapshot of the state at the time of failure.
     */
    val snapshot = snapshot

    // ---------------------------------------------------------------------------------------------

    /**
     * Returns a stream of pairs that pairs elements of [throwable] and [parserTrace]
     *
     * Each element of the stack trace that is a parser invocation is matched to the corresponding
     * parser from the parser trace. If there is no parser trace, the parser element is null.
     * Other elements of the stack trace are dropped.
     */
    fun locatedParserTrace(): Stream<Pair<StackTraceElement, Parser?>>
        = throwable.stackTrace.stream()
            .filter {  it.isMethod(Parser::class, "_parse_") }
            .ziplong(parserTrace.stream())
            .map { Pair(it.first!!, it.second) }

    // ---------------------------------------------------------------------------------------------

    /**
     * Returns a string describing the chain of parser invocations that led to the failure.
     */
    fun trace(): String
    {
        val b = StringBuilder()

        if (throwable is StackTrace)
            b += this
        else
            // (Usually) adds "$className: $message"
            b += throwable

        b += "\n"

        // NOTE:
        // I wanted to handle stack overflows specially, but isolating the loop is not easy.
        // The best approach is to implement an algorithm that finds the longest sub-sequence that
        // repeats itself OR an algorithm that finds the sub-sequence that repeats itself the most
        // **sequentially**. A suffix tree is probably the way to go.

        if (throwable !is StackTrace) {
            // Prints the part of the stacktrace occuring underneath a leaf parser.
            throwable.stackTrace.stream()
                .upTo { it.isMethod(Parser::class, "_parse_") }
                .each { b += "  at $it\n" }
        }

        locatedParserTrace().each body@ {
            pair -> val (elem, parser) = pair
            b += "  at "

            if (parser == null) {
                b += Class.forName(elem.className).simpleName
                return@body
            }

            b += parser

            if (Autumn.DEBUG) {
                b += "\n    defined\n      "
                b += parser.definitionLocation()
                b += "\n    constructed\n      "
                b +=  parser.constructionLocation()
                parser.useLocation() ?.let { b += "\n    used\n      $it" }
            }

            b += "\n"
        }

        return b.toString()
    }
}
