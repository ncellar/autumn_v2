package norswap.autumn.syntax
import norswap.autumn.Parser
import norswap.autumn.parsers.Alias

/**
 * [Alias]`(this)`
 */
val Parser.alias: Parser
    get() = Alias(this)
