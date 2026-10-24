package com.fast.home

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import java.io.File
import java.util.Random
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.sin

class NatureView(ctx: Context) : View(ctx) {

    private class Scene(val sky: IntArray, val layers: IntArray, val sun: Int, val sx: Float, val sy: Float)

    private fun cols(vararg v: Long) = IntArray(v.size) { v[it].toInt() }

    private val scenes = listOf(
        Scene(cols(0xFF2B3A67, 0xFFE8946A, 0xFFF9D9A0), cols(0xFF6E6A8A, 0xFF4D5575, 0xFF2F4153, 0xFF1B2D33),
            0xFFFFF0C0.toInt(), 0.72f, 0.60f),
        Scene(cols(0xFF9FC5C0, 0xFFCFE3D6, 0xFFF1F0DE), cols(0xFF9DBFAF, 0xFF6E9A86, 0xFF3F6E5C, 0xFF214A3F),
            0xFFFFFFFF.toInt(), 0.30f, 0.33f),
        Scene(cols(0xFF0E1B3D, 0xFF6B4A7A, 0xFFF08A6B), cols(0xFF3C3A66, 0xFF2B3160, 0xFF1E2A55, 0xFF111B3D),
            0xFFFFD9A8.toInt(), 0.50f, 0.68f),
        Scene(cols(0xFF5E8FC7, 0xFFA9CBE8, 0xFFE8F1F7), cols(0xFFB7C9D9, 0xFF8FA9BF, 0xFF5D7A92, 0xFF2F4A5A),
            0xFFFFFFFF.toInt(), 0.80f, 0.25f),
        Scene(cols(0xFF3A2A5A, 0xFFD96C55, 0xFFFFC27A), cols(0xFFB5543F, 0xFF8E3F36, 0xFF5E2B2E, 0xFF3A1D24),
            0xFFFFE3A0.toInt(), 0.25f, 0.60f)
    )

    private var cur: Bitmap? = null
    private var nxt: Bitmap? = null
    private var fade = 0f
    private var index = 0
    private var busy = false
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = Rect()
    private val ui = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private val rotate = Runnable { advance() }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (w > 0 && h > 0 && cur == null) {
            exec.execute {
                val b = render(0, w, h)
                ui.post { cur = b; invalidate(); schedule() }
            }
        }
    }

    fun resume() { if (cur != null) schedule() }
    fun pause() { ui.removeCallbacks(rotate) }

    private fun schedule() {
        ui.removeCallbacks(rotate)
        ui.postDelayed(rotate, 25000)
    }

    private fun advance() {
        if (busy || width == 0) { schedule(); return }
        busy = true
        index++
        val i = index
        val w = width
        val h = height
        exec.execute {
            val b = render(i, w, h)
            ui.post {
                nxt = b
                val anim = ValueAnimator.ofFloat(0f, 1f)
                anim.duration = 2500
                anim.addUpdateListener { fade = it.animatedValue as Float; invalidate() }
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: Animator) {
                        cur = nxt
                        nxt = null
                        fade = 0f
                        busy = false
                        invalidate()
                        schedule()
                    }
                })
                anim.start()
            }
        }
    }

    override fun onDraw(c: Canvas) {
        dst.set(0, 0, width, height)
        c.drawColor(Color.BLACK)
        cur?.let { paint.alpha = 255; c.drawBitmap(it, null, dst, paint) }
        nxt?.let { paint.alpha = (fade * 255).toInt(); c.drawBitmap(it, null, dst, paint); paint.alpha = 255 }
    }

    private fun photoFiles(): List<File> = try {
        File(Environment.getExternalStorageDirectory(), "Wallpapers").listFiles()
            ?.filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            ?.sortedBy { it.name } ?: emptyList()
    } catch (e: Throwable) { emptyList() }

    private fun render(i: Int, w: Int, h: Int): Bitmap {
        val s = if (w > 1280) 1280f / w else 1f
        val rw = max(2, (w * s).toInt())
        val rh = max(2, (h * s).toInt())
        val photos = photoFiles()
        if (photos.isNotEmpty()) {
            decodePhoto(photos[i % photos.size], rw, rh)?.let { return it }
        }
        return drawScene(scenes[i % scenes.size], rw, rh)
    }

    private fun decodePhoto(f: File, rw: Int, rh: Int): Bitmap? = try {
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeFile(f.path, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= rw && o.outHeight / (sample * 2) >= rh) sample *= 2
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = sample
        val src = BitmapFactory.decodeFile(f.path, o2)
        if (src == null) null else {
            val out = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
            val sc = max(rw.toFloat() / src.width, rh.toFloat() / src.height)
            val sw = rw / sc
            val sh = rh / sc
            val l = (src.width - sw) / 2f
            val t = (src.height - sh) / 2f
            Canvas(out).drawBitmap(
                src,
                Rect(l.toInt(), t.toInt(), (l + sw).toInt(), (t + sh).toInt()),
                Rect(0, 0, rw, rh),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            src.recycle()
            out
        }
    } catch (e: Throwable) { null }

    private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

    private fun darken(c: Int) = Color.rgb(
        (Color.red(c) * 0.72f).toInt(), (Color.green(c) * 0.72f).toInt(), (Color.blue(c) * 0.72f).toInt()
    )

    private fun drawScene(sc: Scene, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val wf = w.toFloat()
        val hf = h.toFloat()

        p.shader = LinearGradient(0f, 0f, 0f, hf * 0.75f, sc.sky, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, wf, hf, p)

        val sx = sc.sx * wf
        val sy = sc.sy * hf
        p.shader = RadialGradient(
            sx, sy, hf * 0.45f,
            intArrayOf(withAlpha(sc.sun, 200), withAlpha(sc.sun, 0)), null, Shader.TileMode.CLAMP
        )
        c.drawCircle(sx, sy, hf * 0.45f, p)
        p.shader = null
        p.color = sc.sun
        c.drawCircle(sx, sy, hf * 0.045f, p)

        val rnd = Random(sc.sun.toLong() + sc.layers[0])
        for ((k, col) in sc.layers.withIndex()) {
            val base = hf * (0.52f + 0.1f * k)
            val amp = hf * (0.17f - 0.028f * k)
            val f1 = 1.2f + rnd.nextFloat() * 1.2f
            val f2 = 3f + rnd.nextFloat() * 3f
            val p1 = rnd.nextFloat() * 6.28f
            val p2 = rnd.nextFloat() * 6.28f
            val path = Path()
            path.moveTo(0f, hf)
            var x = 0
            while (x <= w) {
                val t = x.toFloat() / wf * 6.2832f
                val y = base - amp * (0.55f * sin(t * f1 + p1) + 0.3f * sin(t * f2 + p2) + 0.6f)
                path.lineTo(x.toFloat(), y)
                x += 6
            }
            path.lineTo(wf, hf)
            path.close()
            p.shader = LinearGradient(0f, base - amp, 0f, hf, col, darken(col), Shader.TileMode.CLAMP)
            c.drawPath(path, p)
        }
        return bmp
    }
}
