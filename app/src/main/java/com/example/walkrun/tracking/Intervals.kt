package com.example.walkrun.tracking

/**
 * Interval program on active time (pauses do not count): an optional [warmupMs] walk, then
 * [repeats] rounds of [workMs] effort and [restMs] rest. No rest after the last round.
 */
class Intervals(
    private val workMs: Long,
    private val restMs: Long,
    val repeats: Int,
    private val warmupMs: Long = 0
) {

    /**
     * [index]: 0, 1, 2... in order (warm-up when there is one, then work, rest, work...).
     * [round]: 1-based, 0 during the warm-up. Null [remainingMs] = program done.
     */
    data class Phase(val index: Int, val round: Int, val work: Boolean, val remainingMs: Long?, val warmup: Boolean = false)

    val totalMs: Long get() = warmupMs + repeats * workMs + (repeats - 1) * restMs

    fun at(elapsedMs: Long): Phase {
        val shift = if (warmupMs > 0) 1 else 0
        if (elapsedMs < warmupMs) return Phase(0, 0, false, warmupMs - elapsedMs, warmup = true)
        if (elapsedMs >= totalMs) return Phase(2 * repeats - 1 + shift, repeats, false, null)
        val e = elapsedMs - warmupMs
        val cycle = workMs + restMs
        val k = (e / cycle).toInt()
        val within = e % cycle
        return if (within < workMs) Phase(2 * k + shift, k + 1, true, workMs - within)
        else Phase(2 * k + 1 + shift, k + 1, false, cycle - within)
    }
}
