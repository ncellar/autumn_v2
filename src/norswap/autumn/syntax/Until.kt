package norswap.autumn.syntax
import norswap.autumn.Parser
import norswap.autumn.parsers.Until

// -------------------------------------------------------------------------------------------------

/**
 * [Until]`(this, until, matchUntil = true, matchSome = false)`
 */
infix fun Parser.until (until: Parser)
    = Until(this, until, matchUntil = true, matchSome = false)

// -------------------------------------------------------------------------------------------------

/**
 * [Until]`(this, until, matchUntil = true, matchSome = true)`
 */
infix fun Parser.until1 (until: Parser)
    = Until(this, until, matchUntil = true, matchSome = true)

// -------------------------------------------------------------------------------------------------

/**
 * [Until]`(this, until, matchUntil = false, matchSome = false)`
 */
infix fun Parser.until_ (until: Parser)
    = Until(this, until, matchUntil = true, matchSome = false)

// -------------------------------------------------------------------------------------------------

/**
 * [Until]`(this, until, matchUntil = false, matchSome = true)`
 */
infix fun Parser.until1_ (until: Parser)
    = Until(this, until, matchUntil = true, matchSome = true)

// -------------------------------------------------------------------------------------------------