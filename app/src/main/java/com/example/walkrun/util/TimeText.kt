package com.example.walkrun.util

/** Times typed in the settings, as m:ss. */
object TimeText {
    /** 125 -> "2:05". */
    fun mmss(sec: Int) = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

    /** "2:05" -> 125, "2" -> 120 (minutes). Null when the text is not a time. */
    fun parse(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val parts = t.split(':')
        return when (parts.size) {
            1 -> parts[0].toIntOrNull()?.takeIf { it >= 0 }?.times(60)
            2 -> {
                val m = parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
                val s = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
                m * 60 + s
            }
            else -> null
        }
    }
}
