package norswap.autumn.example
import norswap.autumn.Grammar
import norswap.autumn.parsers.*

object examply: Grammar() {
    val digit = CharPred(Char::isDigit)
    val integer = OneMore(digit)
}