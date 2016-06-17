package norswap.autumn
import norswap.violin.link.*
import norswap.violin.stream.*
import norswap.violin.utils.plusAssign

data class Seed (
    val pos: Int,
    val parser: Parser,
    val res: Result,
    val delta: Delta,
    var used: Boolean = false)
{
    fun toString(ctx: Context): String {
        val b = StringBuilder()
        b += "seed at ${ctx.posToString(pos)} (${if (used) "used" else "unused"})\n"
        b += "  res: $res\n"
        b += "  delta: $delta"
        return b.toString()
    }
}

class Seeds: ValueStack<Seed>() {
    /**
     * The state and the snapshot must have the same entries for all positions higher than the
     * current position. Currently, there should only be entries for the current position.
     */
    override fun equiv(pos: Int, snap: LinkList<Seed>)
        = snap.stream().takeWhile { it.pos >= pos }.set() ==
               stream().takeWhile { it.pos >= pos }.set()

    fun get(pos: Int, parser: Parser): Seed?
        = stream()  .takeWhile { it.pos >= pos }
                    .first { it.parser == parser }

    override fun snapshotString(snap: LinkList<Seed>, ctx: Context): String {
        val b = StringBuilder()
        b += "{"
        stream().each { seed ->
            b += "\n"
            b += seed.toString(ctx).prependIndent("  ")
        }
        if (!empty) b += "\n"
        b += "}"
        return b.toString()
    }
}

class Ref (val ref: String): Parser()
{
    lateinit var child: Parser
    init { name = "Ref($ref)" }

    override fun _parse_(ctx: Context): Result {
        // recursive Ref#parse call
        ctx.seeds.get(ctx.pos, child)?.let {
            it.used = true
            ctx.merge(it.delta)
            return it.res
        }

        val snapshot = ctx.snapshot()
        // initial seed is failure
        var seed = Seed(ctx.pos, child, failure(ctx), ctx.diff(snapshot))
        ctx.seeds.push(seed)

        // iterate until the seed stops growing
        while (true) {
            val res = child.parse(ctx)
            ctx.seeds.pop()
            if (!seed.used) { // triggers on first iteration or never
                return seed.res
            } else if (ctx.pos <= seed.pos) {
                ctx.restore(snapshot)
                ctx.merge(seed.delta)
                return seed.res
            } else {
                seed = Seed(ctx.pos, child, res, ctx.diff(snapshot))
                ctx.restore(snapshot)
                ctx.seeds.push(seed)
            }
        }
    }
}
