package com.example.walkrun.report

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream

/** Chart images (PNG) for the email reports. Drawn with android.graphics, no library. */
object ChartImages {
    private const val W = 1080
    private const val H = 620
    private const val LEFT = 110f
    private const val RIGHT = 40f
    private const val TOP = 130f
    private const val BOTTOM = 90f

    private val BLUE = Color.rgb(0x0D, 0x47, 0xA1)
    private val GRAY = Color.rgb(0x9E, 0xA7, 0xB3)
    private val GRID = Color.rgb(0xE3, 0xE7, 0xEE)
    private val TEXT = Color.rgb(0x1B, 0x23, 0x30)

    /**
     * Grouped bars: [current] in dark blue, [previous] in gray, one group per label.
     * [format] writes a value for the top of each blue bar.
     */
    fun bars(
        file: File, title: String, labels: List<String>,
        current: List<Double>, previous: List<Double>,
        currentName: String, previousName: String, format: (Double) -> String
    ) = draw(file, title, listOf(currentName to BLUE, previousName to GRAY)) { c ->
        // 20% headroom, so the value above the highest bar does not touch the legend.
        val max = ((current + previous).maxOrNull()?.takeIf { it > 0 } ?: 1.0) * 1.2
        val plotW = W - LEFT - RIGHT
        val plotH = H - TOP - BOTTOM
        grid(c, 0.0, max, format)
        val group = plotW / labels.size
        val barW = group * 0.32f
        labels.forEachIndexed { i, label ->
            val x0 = LEFT + group * i + group * 0.15f
            fun bar(x: Float, v: Double, color: Int) {
                val h = (v / max * plotH).toFloat()
                c.drawRect(RectF(x, TOP + plotH - h, x + barW, TOP + plotH), fill(color))
            }
            bar(x0 + barW, previous.getOrElse(i) { 0.0 }, GRAY)
            bar(x0, current.getOrElse(i) { 0.0 }, BLUE)
            val v = current.getOrElse(i) { 0.0 }
            if (v > 0) c.drawText(format(v), x0 + barW / 2, TOP + plotH - (v / max * plotH).toFloat() - 10, small(TEXT, Paint.Align.CENTER))
            c.drawText(label, LEFT + group * i + group / 2, H - BOTTOM + 40, small(TEXT, Paint.Align.CENTER))
        }
    }

    /**
     * Pace lines (seconds per km). Faster is drawn higher.
     * A null value is a gap. [second] is drawn dashed gray.
     */
    fun paceLines(
        file: File, title: String, labels: List<String>,
        first: List<Double?>, firstName: String,
        second: List<Double?>?, secondName: String?, format: (Double) -> String
    ) = draw(
        file, title,
        listOfNotNull(firstName to BLUE, secondName?.let { it to GRAY })
    ) { c ->
        val all = (first + (second ?: emptyList())).filterNotNull()
        if (all.isEmpty()) return@draw
        var lo = all.min()
        var hi = all.max()
        if (hi - lo < 30) { lo -= 15; hi += 15 }
        val plotW = W - LEFT - RIGHT
        val plotH = H - TOP - BOTTOM
        // Inverted axis: the fastest pace (lowest number) at the top.
        grid(c, hi, lo, format)
        val n = labels.size
        fun x(i: Int) = if (n == 1) LEFT + plotW / 2 else LEFT + plotW * i / (n - 1)
        fun y(v: Double) = (TOP + (v - lo) / (hi - lo) * plotH).toFloat()
        fun line(values: List<Double?>, color: Int, dashed: Boolean) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; style = Paint.Style.STROKE; strokeWidth = 6f
                if (dashed) pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
            }
            val path = Path()
            var open = false
            values.forEachIndexed { i, v ->
                if (v == null) { open = false; return@forEachIndexed }
                if (open) path.lineTo(x(i), y(v)) else path.moveTo(x(i), y(v))
                open = true
                c.drawCircle(x(i), y(v), 9f, fill(color))
            }
            c.drawPath(path, p)
        }
        second?.let { line(it, GRAY, true) }
        line(first, BLUE, false)
        labels.forEachIndexed { i, l -> c.drawText(l, x(i), H - BOTTOM + 40, small(TEXT, Paint.Align.CENTER)) }
    }

    // ---- Helpers ----

    private fun draw(file: File, title: String, legend: List<Pair<String, Int>>, body: (Canvas) -> Unit): File {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT; textSize = 44f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT
        }
        c.drawText(title, W - RIGHT, 60f, t)
        // Legend, right to left under the title.
        var x = W - RIGHT
        legend.forEach { (name, color) ->
            c.drawRect(RectF(x - 28, 82f, x, 106f), fill(color))
            val p = small(TEXT, Paint.Align.RIGHT)
            c.drawText(name, x - 40, 103f, p)
            x -= 40 + p.measureText(name) + 50
        }
        body(c)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }

    /** Four grid lines from [top] value (at the top) to [bottom] value (at the bottom). */
    private fun grid(c: Canvas, bottom: Double, top: Double, format: (Double) -> String) {
        val plotH = H - TOP - BOTTOM
        val line = Paint().apply { color = GRID; strokeWidth = 2f }
        for (k in 0..4) {
            val y = TOP + plotH * k / 4
            c.drawLine(LEFT, y, W - RIGHT, y, line)
            val v = top + (bottom - top) * k / 4
            c.drawText(format(v), LEFT - 14, y + 10, small(GRAY, Paint.Align.RIGHT))
        }
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun small(color: Int, align: Paint.Align) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; textSize = 30f; textAlign = align
    }
}
