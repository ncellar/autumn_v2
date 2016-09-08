package examply
import norswap.autumn.Context
import norswap.autumn.DEBUG
import norswap.autumn.diagnostic
import norswap.violin.utils.readFile

val grammar = Examply()

fun main(args: Array<String>)
{
    DEBUG = true
    parseFile("example/examply/example1.ply")
    parseFile("example/examply/example2.ply")
}

fun parseFile (path: String)
{
    val input = readFile(path)
    val context = Context(input, grammar)
    val result = context.parse()
    println(diagnostic(context, result))
    println(context.stack.toString(context))
}