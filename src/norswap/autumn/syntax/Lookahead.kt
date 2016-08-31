package norswap.autumn.syntax
import norswap.autumn.Parser
import norswap.autumn.parsers.Ahead
import norswap.autumn.parsers.Not

// -------------------------------------------------------------------------------------------------

/**
 * [Ahead]`(this)`
 */
val Parser.ahead: Parser
    get() = Ahead(this)

// -------------------------------------------------------------------------------------------------

/**
 * [Not]`(this)`
 */
val Parser.not: Parser
    get() = Not(this)

// -------------------------------------------------------------------------------------------------