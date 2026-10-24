package com.fast.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.widget.FrameLayout
import kotlin.math.max

class GlassFrame(
    ctx: Context,
    private val bgView: NatureView,
    private val radius: Float,
    private val border: Float,
    private val showBorder: Boolean = true
) : FrameLayout(ctx) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val clip = Path()
    private val loc = IntArray(2)
    private val bgLoc = IntArray(2)
    private val src = Rect()
    private val dst = Rect()

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w < 2f || h < 2f) return
        rect.set(0f, 0f, w, h)
        clip.reset()
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        c.save()
        c.clipPath(clip)

        val blur = bgView.blurCur
        drawBlur(c, blur, 255)
        val n = bgView.blurNxt
        if (n != null) drawBlur(c, n, (bgView.fadeValue * 255).toInt())

        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(if (blur != null) 100 else 78, 12, 14, 24)
        c.drawRect(rect, paint)

        paint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.65f,
            Color.argb(66, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
        )
        c.drawRect(rect, paint)

        paint.shader = RadialGradient(
            w * 0.14f, 0f, max(w, h) * 0.75f,
            Color.argb(70, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
        )
        c.drawRect(rect, paint)

        paint.shader = LinearGradient(
            0f, h * 0.5f, 0f, h,
            Color.argb(0, 0, 0, 0), Color.argb(55, 0, 0, 0), Shader.TileMode.CLAMP
        )
        c.drawRect(rect, paint)
        paint.shader = null
        c.restore()

        if (showBorder && border > 0f) {
            val half = border / 2f
            rect.set(half, half, w - half, h - half)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = border
            paint.shader = LinearGradient(
                0f, 0f, w, h,
                Color.argb(220, 255, 255, 255), Color.argb(45, 255, 255, 255), Shader.TileMode.CLAMP
            )
            c.drawRoundRect(rect, radius - half, radius - half, paint)

            val inset = border * 1.8f
            rect.set(inset, inset, w - inset, h - inset)
            paint.strokeWidth = border * 0.6f
            paint.shader = LinearGradient(
                0f, 0f, 0f, h,
                Color.argb(70, 255, 255, 255), Color.argb(10, 255, 255, 255), Shader.TileMode.CLAMP
            )
            val r2 = max(0f, radius - inset)
            c.drawRoundRect(rect, r2, r2, paint)
            paint.shader = null
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawBlur(c: Canvas, b: Bitmap?, alpha: Int) {
        if (b == null || bgView.width == 0 || bgView.height == 0 || alpha <= 0) return
        getLocationInWindow(loc)
        bgView.getLocationInWindow(bgLoc)
        val sx = b.width.toFloat() / bgView.width
        val sy = b.height.toFloat() / bgView.height
        val l = (loc[0] - bgLoc[0]) * sx
        val t = (loc[1] - bgLoc[1]) * sy
        src.set(
            l.toInt().coerceIn(0, b.width - 1),
            t.toInt().coerceIn(0, b.height - 1),
            (l + width * sx).toInt().coerceIn(1, b.width),
            (t + height * sy).toInt().coerceIn(1, b.height)
        )
        if (src.width() < 1 || src.height() < 1) return
        dst.set(0, 0, width, height)
        paint.alpha = alpha
        c.drawBitmap(b, src, dst, paint)
        paint.alpha = 255
    }
}
