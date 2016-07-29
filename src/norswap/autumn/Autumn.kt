package norswap.autumn
import norswap.autumn.extensions.tokens.TokenGrammar

/**
 * Controls the debug mode. Applies to all [Context]s.
 * Within this mode, more information is recorded
 * (e.g. [Parser.lineage], [DebugFailure], [Context.debugTraceBeforeHook]).
 */
var DEBUG = false

/**
 * If true, hides all parser below the token level in traces ([trace]) when using a
 * [TokenGrammar].
 */
var HIDE_TOKENS_IN_TRACE: Boolean = true
