package norswap.autumn.result
import norswap.autumn.utils.JUtils

// -------------------------------------------------------------------------------------------------

private class PanicCarrier(val failure: Failure): JUtils.NoStackTrace(null)

// -------------------------------------------------------------------------------------------------

/**
 * Panic, throwing the given failure, which can be caught with [chill] or
 * [norswap.autumn.parsers.Chill], or will be caught by [parse] as a last resort.
 */
fun panic(e: Failure): Result = throw PanicCarrier(e)

// -------------------------------------------------------------------------------------------------

/**
 * [chill] internal
 * @suppress
 */
fun getOrThrow(e: Throwable, pred: (Failure) -> Boolean): Result {
    val pc = e as PanicCarrier
    if (pred(pc.failure)) return pc.failure
    else throw pc
}

// -------------------------------------------------------------------------------------------------

/**
 * Runs [body] and returns its result. If it panics ([panic]), return the failure if matched
 * by [pred], else propagates the panic.
 */
inline fun chill(noinline pred: (Failure) -> Boolean = { true }, body: () -> Result): Result
    = try { body() }
      catch (e: PanicCarrier) { getOrThrow(e, pred) }

// -------------------------------------------------------------------------------------------------
