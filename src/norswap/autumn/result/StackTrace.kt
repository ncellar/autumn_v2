package norswap.autumn.result

/**
 * Used to supply a stack trace to an instance of [DebugFailure] when [Context.debug] is set.
 * Instantiated by [Parser.failure] (so this method will trail the stack trace).
 */
class StackTrace: Throwable()
