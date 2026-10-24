package com.fast.home

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private class App(val pkg: String, val label: String, val intent: Intent, val ri: ResolveInfo)

    private val favDefaults =
        "com.netflix.ninja,com.amazon.firetv.youtube,com.canal.android.canal,tv.molotov.app,fr.tf1.mytf1"

    private lateinit var bg: NatureView
    private lateinit var clockTv: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private lateinit var favRow: LinearLayout
    private lateinit var dockRow: LinearLayout
    private val glass = ArrayList<GlassFrame>()
    private var unit = 1f
    private val ui = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("home", MODE_PRIVATE) }
    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            ui.postDelayed(this, 15000)
        }
    }

    private fun p(v: Int) = (v * unit).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        unit = resources.displayMetrics.widthPixels / 1920f
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        bg.resume()
        ui.post(tick)
        refresh(currentFocus?.tag as? String)
    }

    override fun onPause() {
        super.onPause()
        bg.pause()
        ui.removeCallbacks(tick)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (favRow.childCount > 0) favRow.getChildAt(0).requestFocus()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
    }

    private fun tv(sizePx: Int, color: Int, font: String = "sans-serif"): TextView {
        val t = TextView(this)
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, p(sizePx).toFloat())
        t.setTextColor(color)
        t.typeface = Typeface.create(font, Typeface.NORMAL)
        t.setShadowLayer(p(10).toFloat(), 0f, p(2).toFloat(), Color.argb(110, 0, 0, 0))
        return t
    }

    private fun glassPill(r: Int): GlassFrame {
        val g = GlassFrame(this, bg, p(r).toFloat(), p(2).toFloat())
        glass.add(g)
        return g
    }

    private fun hscroll(h: Int, v: Int): HorizontalScrollView {
        val s = HorizontalScrollView(this)
        s.isHorizontalScrollBarEnabled = false
        s.clipToPadding = false
        s.clipChildren = false
        s.overScrollMode = View.OVER_SCROLL_NEVER
        s.setPadding(h, v, h, v)
        return s
    }

    private fun row(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.clipChildren = false
        r.clipToPadding = false
        return r
    }

    private fun buildUi() {
        val white = Color.WHITE
        val soft = Color.argb(215, 255, 255, 255)

        val root = FrameLayout(this)
        bg = NatureView(this)
        bg.onFrame = { for (g in glass) g.invalidate() }
        root.addView(bg, FrameLayout.LayoutParams(-1, -1))

        val scrim = View(this)
        scrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(90, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(200, 0, 0, 0))
        )
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.clipChildren = false
        col.setPadding(p(96), p(44), p(96), p(40))
        root.addView(col, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        top.clipChildren = false

        val clockPill = glassPill(44)
        clockPill.setPadding(p(34), p(16), p(34), p(16))
        clockTv = tv(30, white, "sans-serif-medium")
        clockPill.addView(clockTv)
        top.addView(clockPill, LinearLayout.LayoutParams(-2, -2))

        val dockPill = glassPill(44)
        dockPill.setPadding(p(14), 0, p(14), 0)
        val dockScroll = hscroll(p(8), p(8))
        dockRow = row()
        dockScroll.addView(dockRow)
        dockPill.addView(dockScroll, FrameLayout.LayoutParams(-1, -2))
        val dlp = LinearLayout.LayoutParams(0, -2, 1f)
        dlp.leftMargin = p(28)
        top.addView(dockPill, dlp)
        col.addView(top, LinearLayout.LayoutParams(-1, -2))

        val hero = LinearLayout(this)
        hero.orientation = LinearLayout.VERTICAL
        hero.gravity = Gravity.BOTTOM
        hero.setPadding(p(8), 0, 0, p(20))
        heroTitle = tv(76, white, "sans-serif-medium")
        heroTitle.maxLines = 1
        heroSub = tv(28, soft)
        hero.addView(heroTitle)
        hero.addView(heroSub)
        col.addView(hero, LinearLayout.LayoutParams(-1, 0, 1f))

        val favLabel = tv(28, soft, "sans-serif-medium")
        favLabel.text = "Tes favoris"
        col.addView(favLabel)
        val favScroll = hscroll(p(8), p(34))
        favRow = row()
        favScroll.addView(favRow)
        col.addView(favScroll, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
    }

    private fun updateClock() {
        val now = Date()
        val h = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        val d = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now)
            .replaceFirstChar { it.uppercase() }
        clockTv.text = "$h   ·   $d"
    }

    private fun loadApps(): List<App> {
        val pm = packageManager
        val out = LinkedHashMap<String, App>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(q, 0)) {
                val ai = ri.activityInfo.applicationInfo
                val pk = ai.packageName
                if (pk == packageName || out.containsKey(pk)) continue
                val system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                if (system) continue
                val i = Intent(Intent.ACTION_MAIN).addCategory(cat)
                    .setClassName(pk, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                out[pk] = App(pk, ri.loadLabel(pm).toString(), i, ri)
            }
        }
        return out.values.sortedBy { it.label.lowercase() }
    }

    private fun favList(): MutableList<String> =
        (prefs.getString("favs", favDefaults) ?: "").split(",").filter { it.isNotBlank() }.toMutableList()

    private fun refresh(focusTag: String?) {
        val apps = loadApps()
        val byPkg = apps.associateBy { it.pkg }
        val favs = favList()
        val favApps = favs.mapNotNull { byPkg[it] }
        val rest = apps.filter { a -> favApps.none { it.pkg == a.pkg } }

        favRow.removeAllViews()
        dockRow.removeAllViews()
        favApps.forEach { favRow.addView(card(it)) }
        (favApps + rest).forEach { dockRow.addView(dockIcon(it, favs.contains(it.pkg))) }
        dockRow.addView(settingsItem())

        if (focusTag == null || !focusByTag(focusTag)) {
            if (favRow.childCount > 0) favRow.getChildAt(0).requestFocus()
            else dockRow.getChildAt(0)?.requestFocus()
        }
    }

    private fun focusByTag(t: String): Boolean {
        for (r in listOf(favRow, dockRow)) {
            for (i in 0 until r.childCount) {
                val c = r.getChildAt(i)
                if (c.tag == t) { c.requestFocus(); return true }
            }
        }
        return false
    }

    private fun focusFx(v: View, scale: Float, title: String, sub: String, style: GradientDrawable, card: Boolean) {
        v.isFocusable = true
        v.isClickable = true
        v.setOnFocusChangeListener { view, has ->
            val s = if (has) scale else 1f
            view.animate().scaleX(s).scaleY(s).setDuration(160).start()
            view.elevation = if (has) p(16).toFloat() else 0f
            if (card) {
                if (has) style.setStroke(p(6), Color.WHITE)
                else style.setStroke(p(2), Color.argb(70, 255, 255, 255))
            } else {
                style.setColor(if (has) Color.argb(120, 255, 255, 255) else Color.TRANSPARENT)
                if (has) style.setStroke(p(2), Color.argb(200, 255, 255, 255))
                else style.setStroke(0, Color.TRANSPARENT)
            }
            if (has) {
                heroTitle.text = title
                heroSub.text = sub
            }
        }
    }

    private fun card(a: App): View {
        val pm = packageManager
        val f = FrameLayout(this)
        val base = GradientDrawable()
        base.cornerRadius = p(30).toFloat()
        base.setColor(Color.argb(200, 24, 24, 32))
        f.background = base
        f.clipToOutline = true

        val banner = try {
            a.ri.activityInfo.loadBanner(pm) ?: a.ri.activityInfo.applicationInfo.loadBanner(pm)
        } catch (e: Exception) { null }

        val img = ImageView(this)
        if (banner != null) {
            img.setImageDrawable(banner)
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            f.addView(img, FrameLayout.LayoutParams(-1, -1))
        } else {
            img.setImageDrawable(a.ri.loadIcon(pm))
            f.addView(img, FrameLayout.LayoutParams(p(96), p(96), Gravity.CENTER))
            val name = tv(24, Color.WHITE)
            name.text = a.label
            f.addView(name, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        }

        val gloss = View(this)
        gloss.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(55, 255, 255, 255), Color.argb(0, 255, 255, 255))
        )
        f.addView(gloss, FrameLayout.LayoutParams(-1, p(90), Gravity.TOP))

        val ring = View(this)
        val rd = GradientDrawable()
        rd.cornerRadius = p(30).toFloat()
        rd.setColor(Color.TRANSPARENT)
        rd.setStroke(p(2), Color.argb(70, 255, 255, 255))
        ring.background = rd
        f.addView(ring, FrameLayout.LayoutParams(-1, -1))

        val lp = LinearLayout.LayoutParams(p(384), p(216))
        lp.setMargins(p(14), 0, p(14), 0)
        f.layoutParams = lp
        f.tag = a.pkg
        focusFx(f, 1.12f, a.label, "Favori  ·  appui long sur OK pour retirer", rd, true)
        f.setOnClickListener { go(a.intent) }
        f.setOnLongClickListener { toggleFav(a.pkg); true }
        return f
    }

    private fun dockIcon(a: App, isFav: Boolean): View {
        val f = FrameLayout(this)
        val d = GradientDrawable()
        d.cornerRadius = p(24).toFloat()
        d.setColor(Color.TRANSPARENT)
        f.background = d

        val img = ImageView(this)
        img.setImageDrawable(a.ri.loadIcon(packageManager))
        f.addView(img, FrameLayout.LayoutParams(p(48), p(48), Gravity.CENTER))

        val lp = LinearLayout.LayoutParams(p(68), p(68))
        lp.setMargins(p(6), 0, p(6), 0)
        f.layoutParams = lp
        f.tag = a.pkg
        val sub = if (isFav) "Favori  ·  appui long sur OK pour retirer"
        else "Appui long sur OK pour ajouter aux favoris"
        focusFx(f, 1.18f, a.label, sub, d, false)
        f.setOnClickListener { go(a.intent) }
        f.setOnLongClickListener { toggleFav(a.pkg); true }
        return f
    }

    private fun settingsItem(): View {
        val t = tv(24, Color.WHITE, "sans-serif-medium")
        t.text = "Réglages"
        t.gravity = Gravity.CENTER
        t.setPadding(p(26), 0, p(26), 0)
        val d = GradientDrawable()
        d.cornerRadius = p(24).toFloat()
        d.setColor(Color.TRANSPARENT)
        t.background = d
        val lp = LinearLayout.LayoutParams(-2, p(68))
        lp.setMargins(p(6), 0, p(6), 0)
        t.layoutParams = lp
        t.tag = "settings"
        focusFx(t, 1.1f, "Réglages", "Paramètres de la Fire TV", d, false)
        t.setOnClickListener {
            go(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return t
    }

    private fun toggleFav(pkg: String) {
        val l = favList()
        if (l.contains(pkg)) {
            l.remove(pkg)
            Toast.makeText(this, "Retiré des favoris", Toast.LENGTH_SHORT).show()
        } else {
            l.add(pkg)
            Toast.makeText(this, "Ajouté aux favoris", Toast.LENGTH_SHORT).show()
        }
        prefs.edit().putString("favs", l.joinToString(",")).apply()
        refresh(pkg)
    }

    private fun go(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Impossible d'ouvrir cette appli", Toast.LENGTH_SHORT).show()
        }
    }
}
