package norswap.autumn.example
import norswap.autumn.Context
import norswap.autumn.Result
import norswap.autumn.Success
import norswap.violin.utils.readFile
import kotlin.system.measureTimeMillis

fun main(args: Array<String>) {
    val input = readFile("src/norswap/autumn/example/example.ply")
    val context = Context(input, Examply)
    context.debug = true
    val result = context.parse()
    println(context.diagnostic(result))
    println(context.stack.toString(context))
}