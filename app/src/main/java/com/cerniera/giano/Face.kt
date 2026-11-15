package com.cerniera.giano

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import com.cerniera.kit.Kit
import com.cerniera.kit.withAlpha
import kotlin.math.min
import kotlin.math.sin

/**
 * The outward face: what the room sees on the cover screen. A card in Italiana above a rear light,
 * a rosso bar like the Giulietta's, breathing slowly. Drawn for whichever way the glass is held:
 * on a short, wide cover the words take one line and the light runs nearly the full width.
 */
class Face(ctx: Context, var words: String = "Giano") : View(ctx) {
    private val ground = Paint().apply { color = 0xFF0A0E0C.toInt() }
    private val type = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Kit.display(ctx); color = 0xFFF6F1E6.toInt(); textAlign = Paint.Align.CENTER }
    private val lamp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hair = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(0xFFD9BB86.toInt(), 0.35f); strokeWidth = ctx.resources.displayMetrics.density }
    private val t0 = SystemClock.uptimeMillis()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawRect(0f, 0f, w, h, ground)
        val wide = w > h
        // The card: as large as the glass allows, one line
        var size = min(h * (if (wide) 0.30f else 0.16f), w * 0.9f / (0.55f * words.length.coerceAtLeast(1)))
        type.textSize = size
        while (type.measureText(words) > w * 0.86f && size > 12f) { size *= 0.94f; type.textSize = size }
        val cy = h * (if (wide) 0.42f else 0.40f)
        c.drawText(words, w / 2f, cy + size * 0.32f, type)
        c.drawLine(w * 0.3f, cy + size * 0.75f, w * 0.7f, cy + size * 0.75f, hair)

        // The light: breathing on the family's 5.2 s
        val breath = 0.62f + 0.38f * ((sin((SystemClock.uptimeMillis() - t0) / 5200.0 * 2 * Math.PI) + 1) / 2).toFloat()
        val bw = w * (if (wide) 0.82f else 0.66f)
        val bh = (h * 0.012f).coerceAtLeast(6f)
        val by = h * (if (wide) 0.80f else 0.78f)
        val bar = RectF((w - bw) / 2f, by - bh / 2f, (w + bw) / 2f, by + bh / 2f)
        glow.shader = RadialGradient(w / 2f, by, bw * 0.62f, intArrayOf(withAlpha(0xFFD7262E.toInt(), 0.45f * breath), 0), null, Shader.TileMode.CLAMP)
        c.save(); c.scale(1f, 0.22f, w / 2f, by); c.drawCircle(w / 2f, by, bw * 0.62f, glow); c.restore()
        lamp.shader = LinearGradient(bar.left, 0f, bar.right, 0f,
            intArrayOf(withAlpha(0xFFD7262E.toInt(), 0.15f), withAlpha(0xFFD7262E.toInt(), breath), withAlpha(0xFFD7262E.toInt(), 0.15f)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(bar, bh / 2f, bh / 2f, lamp)
        postInvalidateDelayed(50)
    }
}
