package com.fast.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.widget.FrameLayout

class GlassFrame(
    ctx: Context,
    private val bgView: NatureView,
    private val radius: Float,
    private val border: Float
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
        drawBlur(c, bgView.blurCur, 255)
        val n = bgView.blurNxt
        if (n != null) drawBlur(c, n, (bgView.fadeValue * 255).toInt())
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(105, 10, 12, 22)
        c.drawRect(rect, paint)
        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            Color.argb(60, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
        )
        c.drawRect(rect, paint)
        paint.shader = null
        c.restore()

        val half = border / 2f
        rect.set(half, half, w - half, h - half)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = border
        paint.shader = LinearGradient(
            0f, 0f, w, h,
            Color.argb(210, 255, 255, 255), Color.argb(50, 255, 255, 255), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, radius - half, radius - half, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
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
