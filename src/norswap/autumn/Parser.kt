package norswap.autumn
import norswap.violin.utils.after

/**
 * The parent of all parser classes.
 *
 * # Instantiation
 *
 * There are two ways to define a parser. You can use [invoke]:
 *
 * ```
 * fun MyParser (vararg children: Parser) = Parser(*children) { ctx ->
 *     ...
 *     Success
 * }
 * ```
 *
 * or override the [_parse_] method:
 *
 * ```
 * fun MyParser (vararg children: Parser) = object: Parser(*children) {
 *     override fun _parse_(ctx: Context): Result {
 *          ...
 *          return Success
 *     }
 * }
 * ```
 *
 * The first method is terser, but the second allows adding new fields and methods to the parser.
 *
 * Note that additional public fields are usually not necessary, and if you follow the style
 * guidelines (use named parsers liberally, no parser definition longer than one line in your
 * grammar), overriding the string methods is little help.
 *
 * TODO write and link said guidelines
 *
 * See `com/norswap/autumn/Parsers.kt` for examples.
 *
 * # Contract
 *
 * Either [parse] succeeds returning [Success] or fails returning a [Failure], in which case it must
 * revert all changes it and its descendants made to the state. This can be achieved using
 * [Context.snapshot] and [Context.restore] or the [transact] shorthand.
 *
 * ## Panics
 *
 * A parser can exit through a panic ([panic]) which throws an exception, so you shouldn't
 * rely on the code after any [Parser.invoke] invocation being executed. If it is nevertheless
 * necessary, use a `finally` block or [tryParse]. It is however not necessary to use those
 * mechanisms to restore the state, as it must be restored to an earlier snapshot when/if the panic
 * is caught.
 *
 * # Name
 *
 * A parser can optionally have a [name], which will be used when printing the parser. This name
 * is also significant as the target of recursive references ([Ref]). As such, parser names should
 * be unique.
 *
 * # Definer
 *
 * Since some parsers are created through [Parser.invoke] their actual class name is something ugly
 * like `Seq##inlined##invoke#1`. To clean this up we introduce the notion of *definer*, which
 * is either the class name if subclassing directly, or the name of the function that holds the
 * [Parser.invoke] call.
 */
abstract class Parser (vararg val children: Parser)
{
    companion object {
        /**
         * Use this builder to create subclasses of [Parser]. e.g.:
         *
         * ```
         * fun MyParser (vararg children: Parser) = Parser(*children) { ctx ->
         *     ...
         * }
         * ```
         *
         * If you are defining a singleton parser, you can also supply its [name] directly.
         */
        inline operator fun invoke(
            vararg children: Parser,
            name: String? = null,
            crossinline body: Parser.(Context) -> Result
        ): Parser
            = object : Parser(*children) {
                init { this.name = name }
                override fun _parse_(ctx: Context) = body(ctx)
    }       }

    /**
     * See [Parser]
     */
    var name: String? = null

    /**
     * The name of this class, or the name of the function containing the [Parser.invoke]
     * call that created this parser (possibly suffixed by $2, $3, ...) if the function contains
     * more than one [Parser.invoke] invocation.
     *
     * You can set this explicitly in order to more precisely define the nature of the
     * parser (e.g. if a parser class has important parameters, or for syntactic sugars).
     */
    var definer: String
        = javaClass.simpleName
        .removeSuffix("\$\$inlined\$invoke\$1")
        .replace("\$\$inlined\$invoke", "")

    /**
     * If true, don't trace the children of this parser when [Context.logTrace] is set.
     * Default: false.
     */
    var noTrace = false

    private var traceSuppressedAt = -1

    /// Parse --------------------------------------------------------------------------------------

    /**
     * Implementation of [parse]. Never call this directly, call [parse] instead.
     */
    abstract fun _parse_(ctx: Context): Result
    // NOTE: _parse_ is not protected, because this would generate an extra method call indirection

    internal class LogState {
        var lastResMsg: String = ""
        var depth = 1
    }

    /**
     * Called by [parse] before [_parse_].
     * @suppress
     */
    final fun beforeParse(ctx: Context) {
        if (ctx.debug) {
            ctx.trace.push(this)
            ctx.debugTraceBeforeHook(ctx, this)
        }

        if (ctx.logTrace) {
            ctx.dbg.lastResMsg = ""
            ctx.logStream.println("${"-|".repeat(ctx.dbg.depth)} ${toStringSimple()} ($ctx.posStr)")
            ++ctx.dbg.depth
            if (noTrace) {
                traceSuppressedAt = ctx.dbg.depth
                ctx.logTrace = false
                ++ ctx.dbg.depth
            }
        }
        else if (noTrace && traceSuppressedAt != -1) {
            ++ ctx.dbg.depth
        }
    }

    /**
     * Called by [parse] after [_parse_].
     * @suppress
     */
    final fun afterParse(ctx: Context, res: Result) {

        if (noTrace && traceSuppressedAt != -1) {
            -- ctx.dbg.depth
            if (traceSuppressedAt == ctx.dbg.depth) {
                traceSuppressedAt = -1
                ctx.logTrace = true
            }
        }

        if (ctx.logTrace) {
            --ctx.dbg.depth
            val resMsg = res.toString()
            if (resMsg != ctx.dbg.lastResMsg) {
                ctx.dbg.lastResMsg = resMsg
                ctx.logStream.println("${"-|".repeat(ctx.dbg.depth)}-> $resMsg")
            }
        }

        if (ctx.debug) {
            ctx.trace.pop()
            ctx.debugTraceAfterHook(ctx, this)
        }
    }

    /**
     * See [Parser]. Implement through [invoke] preferably, or through [_parse_].
     */
    @Suppress("NOTHING_TO_INLINE")
    final inline fun parse(ctx: Context): Result {
        beforeParse(ctx)
        return _parse_(ctx) after { afterParse(ctx, it) }
    }

    /// Strings ------------------------------------------------------------------------------------

    /**
     * Prints the [name] of the Parser, if it has one, and its definer.
     */
    fun toStringSimple(): String
        = if (name != null) "$name (${definer})" else definer

    /**
     * Prints the parser: either its [name], or its definer and children.
     */
    final override fun toString()
        = name ?: "${definer}(${children.joinToString()})"

    /// Failures -----------------------------------------------------------------------------------

    /**
     * Builds a failure whose message will be generated by [msg].
     * It is advised to use [failure] instead.
     *
     * If [Context.debug] is true, this generates instances of [DebugFailure], giving more info at
     * the cost of performance.
     */
    fun plainFailure(ctx: Context, msg: Parser.(Context) -> String): Failure {
        val msg2 = { this.msg(ctx) }
        return if (!ctx.debug) Failure(ctx.pos, msg2)
        else DebugFailure(ctx.pos, msg2, StackTrace(), ctx.trace.link, ctx.snapshot())
    }

    /**
     * Builds a failure (through [plainFailure]) whose message will be generated by [msg] and
     * annotated with the name of the parser and the current input input position.
     */
    fun failure(ctx: Context, msg: () -> String): Failure {
        val pos = ctx.pos
        return plainFailure(ctx) { msg() + " (in ${toStringSimple()} at ${ctx.posToString(pos)})" }
    }

    /**
     * Builds a failure (see [plainFailure]) with a default message including the name of this
     * parser and the current input position.
     */
    fun failure(ctx: Context): Failure {
        val pos = ctx.pos
        return plainFailure(ctx) { "in ${toStringSimple()} at ${ctx.posToString(pos)}" }
    }

    /**
     * Panic, throwing the given failure, which can be caught with [tryParse] or
     * [norswap.autumn.parsers.Chill], or will be caught by [parse] as a last resort.
     */
    fun panic(e: Failure): Result = throw Carrier(e)

    /// Utilities ----------------------------------------------------------------------------------

    /**
     * Calls [f] after making a snapshot.
     * If the result is [Failure], restores the snapshot.
     * Returns the result of [f].
     */
    inline fun transact(ctx: Context, f: () -> Result): Result {
        val snapshot = ctx.snapshot()
        return f().apply { if (this is Failure) ctx.restore(snapshot) }
    }

    /**
     * Shorthand for `if (cond()) Success else failure()` (see [failure]).
     */
    inline fun succeed(ctx: Context, cond: () -> Boolean): Result
        = if (cond()) Success else failure(ctx)

    /**
     * Shorthand for `if (cond) Success else failure(msg)` (see [failure]).
     */
    fun succeed(ctx: Context, cond: Boolean, msg: () -> String): Result
        = if (cond) Success else failure(ctx, msg)

    /// --------------------------------------------------------------------------------------------
}
