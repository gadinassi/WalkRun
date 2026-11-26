package com.example.walkrun.tracking

/**
 * Interval program on active time (pauses do not count): [repeats] rounds of
 * [workMs] effort and [restMs] rest. No rest after the last round.
 */
class Intervals(private val workMs: Long, private val restMs: Long, val repeats: Int) {

    /** [index]: 0, 1, 2... (work, rest, work...). [round]: 1-based. Null [remainingMs] = program done. */
    data class Phase(val index: Int, val round: Int, val work: Boolean, val remainingMs: Long?)

    val totalMs: Long get() = repeats * workMs + (repeats - 1) * restMs

    fun at(elapsedMs: Long): Phase {
        if (elapsedMs >= totalMs) return Phase(2 * repeats - 1, repeats, false, null)
        val cycle = workMs + restMs
        val k = (elapsedMs / cycle).toInt()
        val within = elapsedMs % cycle
        return if (within < workMs) Phase(2 * k, k + 1, true, workMs - within)
        else Phase(2 * k + 1, k + 1, false, cycle - within)
    }
}
