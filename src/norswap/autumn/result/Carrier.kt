package norswap.autumn.result
import norswap.autumn.utils.JUtils

// -------------------------------------------------------------------------------------------------

/**
 * Carries failure during panics.
 */
internal class Carrier (val failure: Failure): JUtils.NoStackTrace(null)

// -------------------------------------------------------------------------------------------------

/**
 * Runs [body] and returns its result. If it panics ([Parser.panic]), return the failure if matched
 * by  [pred], else propagates the panic.
 */
fun tryParse(pred: (Failure) -> Boolean = { true }, body: () -> Result): Result
    = try { body() }
      catch (e: Carrier) {
          if (pred(e.failure)) e.failure
          else throw e
      }

// -------------------------------------------------------------------------------------------------
