package norswap.autumn.syntax
import norswap.autumn.Parser
import norswap.autumn.parsers.*

// -------------------------------------------------------------------------------------------------

/**
 * [Opt]`(this)`
 */
val Parser.opt: Parser
    get() = Opt(this)

// -------------------------------------------------------------------------------------------------

/**
 * [ZeroMore]`(this)`
 */
val Parser.repeat: Parser
    get() = ZeroMore(this)

// -------------------------------------------------------------------------------------------------

/**
 * [OneMore]`(this)`
 */
val Parser.repeat1: Parser
    get() = OneMore(this)

// -------------------------------------------------------------------------------------------------

/**
 * [Repeat]`(n, this)`
 */
fun Parser.repeat (n: Int)
    = Repeat(n, this)

// -------------------------------------------------------------------------------------------------

/**
 * [Around]`(this, inside)`
 */
infix fun Parser.around (inside: Parser)
    = Around(this, inside)

// -------------------------------------------------------------------------------------------------

/**
 * [Around1]`(this, inside)`
 */
infix fun Parser.around1 (inside: Parser)
    = Around1(this, inside)

// -------------------------------------------------------------------------------------------------