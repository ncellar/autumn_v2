package norswap.autumn.utils

/**
 * Is the character in the ASCII character set (unicode 0-127)?
 */
fun Char.isAscii()
    = this < 128.toChar()

/**
 * Is the character in the extended ASCII character set (unicode 0-255)?
 */
fun Char.isExtendedAscii()
    = this < 256.toChar()

/**
 * Encode the character to its hexadecimal representation (no prefix is included).
 */
fun Char.hexEncode()
    = String.format("%04x", this)

/**
 * Is the character an octal digit?
 */
fun Char.isOctalDigit()
    = '0' <= this && this <= '7'

/**
 * Is the character an hexadecimal digit?
 */
fun Char.isHexDigit()
    =  '0' <= this && this <= '9'
    || 'a' <= this && this <= 'f'
    || 'A' <= this && this <= 'F'

/**
 * Can the character be printed using the ASCII character set?
 */
fun Char.isAsciiPrintable()
    = isAscii() && "\t\n\r".contains(this) || ' ' <= this && this <= '~'