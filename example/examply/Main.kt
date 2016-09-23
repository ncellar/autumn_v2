package examply
import norswap.autumn.Context
import norswap.autumn.DEBUG
import norswap.autumn.diagnostic
import norswap.autumn.utils.readFile

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
    val ctx = Context(input, grammar)
    val result = ctx.parse()
    println(diagnostic(ctx, result))
    println(ctx.stack)
}