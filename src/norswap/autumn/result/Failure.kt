package norswap.autumn.result
/**
 * The parser invocation was unsuccessful: the parser didn't "match" the input.
 *
 * Failures are usually constructed through [Parser.failure].
 */
open class Failure (pos: Int, msg: () -> String): Result()
{
    // ---------------------------------------------------------------------------------------------

    /**
     * The position at which the failure occurred.
     */
    val pos = pos

    // ---------------------------------------------------------------------------------------------

    /**
     * The message to display to the user for this failure. Since most failures are never
     * shown, making this a lazily-evaluated function is cheaper.
     */
    val msg = msg
}
