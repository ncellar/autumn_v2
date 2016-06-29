import norswap.autumn.Context
import norswap.violin.utils.readFile

fun main(args: Array<String>) {
    val input = readFile("example/example.ply")
    val context = Context(input, Examply)
    context.debug = true
    val result = context.parse()
    println(context.diagnostic(result))
    println(context.stack.toString(context))
}