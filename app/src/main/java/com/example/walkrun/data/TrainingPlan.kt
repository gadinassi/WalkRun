package com.example.walkrun.data

import com.example.walkrun.tracking.Intervals
import com.example.walkrun.util.TimeText

/** One workout of the plan: warm-up walk, run/walk rounds, cool-down walk. */
data class PlanSession(
    val week: Int,
    val day: Int,
    val runSec: Int,
    val walkSec: Int,
    val repeats: Int,
    val warmupSec: Int = 300,
    val cooldownSec: Int = 300
) {
    val key: String get() = "$week-$day"

    val runTotalMin: Int get() = runSec * repeats / 60

    /** For example "6 × ריצה 1:30 / הליכה 2:00" or "ריצה 30:00 ברצף". */
    val description: String
        get() = if (repeats == 1) "ריצה ${TimeText.mmss(runSec)} ברצף"
        else "$repeats × ריצה ${TimeText.mmss(runSec)} / הליכה ${TimeText.mmss(walkSec)}"

    /** Total time with warm-up and cool-down, in minutes (rounded up). */
    val totalMin: Int
        get() = (warmupSec + repeats * runSec + (repeats - 1) * walkSec + cooldownSec + 59) / 60

    fun intervals() = Intervals(runSec * 1000L, walkSec * 1000L, repeats, warmupSec * 1000L)
}

/**
 * "From 0 to 5 km in 8 weeks": 3 workouts a week. The run time grows step by step, from 8 minutes
 * in week 1 to 30 minutes without a break in week 8 (about 5 km for most beginners).
 */
object TrainingPlan {
    const val NAME = "מ-0 ל-5 ק\"מ ב-8 שבועות"

    private data class Week(val runSec: Int, val walkSec: Int, val repeats: Int)

    private val WEEKS = listOf(
        Week(60, 90, 8),    // 8 min running
        Week(90, 120, 6),   // 9
        Week(120, 90, 6),   // 12
        Week(180, 90, 5),   // 15
        Week(300, 120, 4),  // 20
        Week(480, 120, 3),  // 24
        Week(780, 120, 2),  // 26
        Week(1800, 0, 1)    // 30, without a break
    )

    val sessions: List<PlanSession> = WEEKS.flatMapIndexed { w, wk ->
        (1..3).map { d -> PlanSession(w + 1, d, wk.runSec, wk.walkSec, wk.repeats) }
    }

    fun byKey(key: String?): PlanSession? = sessions.firstOrNull { it.key == key }

    /** The first session not done yet, in order. Null = the plan is complete. */
    fun next(done: Set<String>): PlanSession? = sessions.firstOrNull { it.key !in done }
}
