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
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import java.io.File
import java.util.Random
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class NatureView(ctx: Context) : View(ctx) {

    private class Scene(val sky: IntArray, val layers: IntArray, val sun: Int, val sx: Float, val sy: Float)
    private class Frame(val full: Bitmap, val blur: Bitmap)
    private class Blob(
        val color: Int, val bx: Float, val by: Float, val ax: Float, val ay: Float,
        val sx: Float, val sy: Float, val px: Float, val py: Float, val rad: Float
    )

    var onFrame: (() -> Unit)? = null
    val blurCur: Bitmap? get() = if (mode == 2) cur?.blur else null
    val blurNxt: Bitmap? get() = if (mode == 2) nxt?.blur else null
    val fadeValue: Float get() = fade

    // 0 = aurore animée, 1 = noir, 2 = paysages
    private var mode = 0
    private var running = false
    private val t0 = SystemClock.uptimeMillis()

    private val blobs = listOf(
        Blob(0xFFE50914.toInt(), 0.25f, 0.30f, 0.20f, 0.18f, 0.00031f, 0.00027f, 0f, 1.0f, 0.55f),
        Blob(0xFF5B3BD6.toInt(), 0.70f, 0.25f, 0.22f, 0.20f, 0.00023f, 0.00035f, 2.0f, 0.5f, 0.60f),
        Blob(0xFF0E7ACC.toInt(), 0.50f, 0.85f, 0.30f, 0.12f, 0.00027f, 0.00021f, 4.0f, 2.5f, 0.55f)
    )
    private var shaders: List<RadialGradient> = emptyList()
    private var radii = FloatArray(0)
    private val mat = Matrix()
    private val apaint = Paint(Paint.ANTI_ALIAS_FLAG)

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

    private var cur: Frame? = null
    private var nxt: Frame? = null
    private var fade = 0f
    private var index = 0
    private var busy = false
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = Rect()
    private val ui = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private val rotate = Runnable { advance() }
    private val loop = object : Runnable {
        override fun run() {
            if (running && mode == 0) {
                invalidate()
                ui.postDelayed(this, 66)
            }
        }
    }

    private fun changed() {
        invalidate()
        onFrame?.invoke()
    }

    fun setMode(m: Int) {
        mode = m
        ui.removeCallbacks(loop)
        ui.removeCallbacks(rotate)
        if (running) resume()
        changed()
    }

    fun resume() {
        running = true
        if (mode == 0) ui.post(loop)
        if (mode == 2) {
            ensureNature()
            if (cur != null) schedule()
        }
    }

    fun pause() {
        running = false
        ui.removeCallbacks(loop)
        ui.removeCallbacks(rotate)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (w > 0 && h > 0) {
            radii = FloatArray(blobs.size) { blobs[it].rad * h }
            shaders = blobs.mapIndexed { i, b ->
                RadialGradient(
                    0f, 0f, radii[i],
                    intArrayOf(withAlpha(b.color, 130), withAlpha(b.color, 0)), null, Shader.TileMode.CLAMP
                )
            }
            ensureNature()
        }
    }

    private fun ensureNature() {
        if (mode == 2 && cur == null && width > 0 && height > 0 && !busy) {
            busy = true
            val w = width
            val h = height
            exec.execute {
                val f = render(0, w, h)
                ui.post { cur = f; busy = false; changed(); schedule() }
            }
        }
    }

    private fun schedule() {
        ui.removeCallbacks(rotate)
        if (running && mode == 2) ui.postDelayed(rotate, 25000)
    }

    private fun advance() {
        if (mode != 2) return
        if (busy || width == 0) { schedule(); return }
        busy = true
        index++
        val i = index
        val w = width
        val h = height
        exec.execute {
            val f = render(i, w, h)
            ui.post {
                nxt = f
                val anim = ValueAnimator.ofFloat(0f, 1f)
                anim.duration = 2500
                anim.addUpdateListener { fade = it.animatedValue as Float; changed() }
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: Animator) {
                        cur = nxt
                        nxt = null
                        fade = 0f
                        busy = false
                        changed()
                        schedule()
                    }
                })
                anim.start()
            }
        }
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.BLACK)
        when (mode) {
            0 -> drawAurora(c)
            2 -> {
                dst.set(0, 0, width, height)
                cur?.let { paint.alpha = 255; c.drawBitmap(it.full, null, dst, paint) }
                nxt?.let {
                    paint.alpha = (fade * 255).toInt()
                    c.drawBitmap(it.full, null, dst, paint)
                    paint.alpha = 255
                }
            }
        }
    }

    private fun drawAurora(c: Canvas) {
        if (shaders.isEmpty()) return
        val t = (SystemClock.uptimeMillis() - t0).toFloat()
        for (i in blobs.indices) {
            val b = blobs[i]
            val cx = width * (b.bx + b.ax * sin(t * b.sx + b.px))
            val cy = height * (b.by + b.ay * cos(t * b.sy + b.py))
            mat.setTranslate(cx, cy)
            shaders[i].setLocalMatrix(mat)
            apaint.shader = shaders[i]
            c.drawCircle(cx, cy, radii[i], apaint)
        }
    }

    private fun photoFiles(): List<File> = try {
        File(Environment.getExternalStorageDirectory(), "Wallpapers").listFiles()
            ?.filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            ?.sortedBy { it.name } ?: emptyList()
    } catch (e: Throwable) { emptyList() }

    private fun makeFrame(full: Bitmap): Frame {
        val a = Bitmap.createScaledBitmap(full, max(2, full.width / 4), max(2, full.height / 4), true)
        val b = Bitmap.createScaledBitmap(a, max(2, full.width / 20), max(2, full.height / 20), true)
        if (a !== b) a.recycle()
        return Frame(full, b)
    }

    private fun render(i: Int, w: Int, h: Int): Frame {
        val s = if (w > 1280) 1280f / w else 1f
        val rw = max(2, (w * s).toInt())
        val rh = max(2, (h * s).toInt())
        val photos = photoFiles()
        if (photos.isNotEmpty()) {
            decodePhoto(photos[i % photos.size], rw, rh)?.let { return makeFrame(it) }
        }
        return makeFrame(drawScene(scenes[i % scenes.size], rw, rh))
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
