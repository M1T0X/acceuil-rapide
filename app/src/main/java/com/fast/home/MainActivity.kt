package com.fast.home

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private class App(val pkg: String, val label: String, val intent: Intent, val ri: ResolveInfo)

    private val favDefaults =
        "com.netflix.ninja,com.amazon.firetv.youtube,com.canal.android.canal,tv.molotov.app,fr.tf1.mytf1"
    private val accent = Color.rgb(229, 9, 20)

    private lateinit var bg: NatureView
    private lateinit var dim: View
    private lateinit var clockTv: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private lateinit var scroll: ScrollView
    private lateinit var favRow: LinearLayout
    private lateinit var appsRow: LinearLayout

    private var apps: List<App> = emptyList()
    private var dirty = true
    private var unit = 1f
    private val bannerState = HashMap<String, Drawable.ConstantState>()
    private val iconState = HashMap<String, Drawable.ConstantState>()
    private val noBanner = HashSet<String>()
    private val ui = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("home", MODE_PRIVATE) }

    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            ui.postDelayed(this, 15000)
        }
    }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { dirty = true }
    }

    private fun p(v: Int) = (v * unit).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        unit = resources.displayMetrics.widthPixels / 1920f
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        buildUi()
        val f = IntentFilter()
        f.addAction(Intent.ACTION_PACKAGE_ADDED)
        f.addAction(Intent.ACTION_PACKAGE_REMOVED)
        f.addAction(Intent.ACTION_PACKAGE_REPLACED)
        f.addDataScheme("package")
        registerReceiver(pkgReceiver, f)
    }

    override fun onDestroy() {
        try { unregisterReceiver(pkgReceiver) } catch (e: Exception) { }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        ui.post(tick)
        applyBg()
        if (dirty || favRow.childCount == 0) refresh(currentFocus?.tag as? String)
    }

    override fun onPause() {
        super.onPause()
        bg.pause()
        ui.removeCallbacks(tick)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        scroll.scrollTo(0, 0)
        if (favRow.childCount > 0) favRow.getChildAt(0).requestFocus()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
    }

    private fun nature() = prefs.getString("bg", "noir") == "nature"

    private fun applyBg() {
        val n = nature()
        bg.visibility = if (n) View.VISIBLE else View.GONE
        dim.visibility = if (n) View.VISIBLE else View.GONE
        if (n) bg.resume() else bg.pause()
    }

    private fun tv(sizePx: Int, color: Int, font: String = "sans-serif"): TextView {
        val t = TextView(this)
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, p(sizePx).toFloat())
        t.setTextColor(color)
        t.typeface = Typeface.create(font, Typeface.NORMAL)
        return t
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

    private fun rowLabel(text: String): TextView {
        val t = tv(30, Color.argb(230, 255, 255, 255), "sans-serif-medium")
        t.text = text
        t.setPadding(p(26), p(18), 0, 0)
        return t
    }

    private fun tab(label: String, action: () -> Unit): TextView {
        val idle = Color.argb(190, 255, 255, 255)
        val t = tv(28, idle, "sans-serif-medium")
        t.text = label
        t.gravity = Gravity.CENTER
        t.setPadding(p(26), p(10), p(26), p(10))
        val d = GradientDrawable()
        d.cornerRadius = p(30).toFloat()
        d.setColor(Color.TRANSPARENT)
        t.background = d
        t.isFocusable = true
        t.isClickable = true
        t.setOnFocusChangeListener { _, has ->
            d.setColor(if (has) Color.WHITE else Color.TRANSPARENT)
            t.setTextColor(if (has) Color.BLACK else idle)
            if (has) {
                heroTitle.text = "Accueil Rapide"
                heroSub.text = label
            }
        }
        t.setOnClickListener { action() }
        return t
    }

    private fun buildUi() {
        val white = Color.WHITE
        val soft = Color.argb(215, 255, 255, 255)

        val root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.rgb(28, 28, 36), Color.BLACK, Color.BLACK)
        )
        bg = NatureView(this)
        root.addView(bg, FrameLayout.LayoutParams(-1, -1))
        dim = View(this)
        dim.setBackgroundColor(Color.argb(140, 0, 0, 0))
        root.addView(dim, FrameLayout.LayoutParams(-1, -1))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.clipChildren = false
        col.setPadding(p(80), p(36), p(80), 0)
        root.addView(col, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        top.clipChildren = false

        val brand = tv(40, accent, "sans-serif-black")
        brand.text = "ACCUEIL"
        brand.letterSpacing = 0.08f
        val blp = LinearLayout.LayoutParams(-2, -2)
        blp.rightMargin = p(48)
        top.addView(brand, blp)

        top.addView(tab("Accueil") {
            scroll.smoothScrollTo(0, 0)
            if (favRow.childCount > 0) favRow.getChildAt(0).requestFocus()
        })
        top.addView(tab("Applis") {
            if (appsRow.childCount > 0) appsRow.getChildAt(0).requestFocus()
        })
        top.addView(tab("Menu") { showMenu() })

        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        clockTv = tv(30, white, "sans-serif-medium")
        top.addView(clockTv)
        col.addView(top, LinearLayout.LayoutParams(-1, -2))

        val hero = LinearLayout(this)
        hero.orientation = LinearLayout.VERTICAL
        hero.gravity = Gravity.BOTTOM
        hero.setPadding(p(26), 0, 0, p(10))
        heroTitle = tv(72, white, "sans-serif-medium")
        heroTitle.maxLines = 1
        heroTitle.text = "Accueil Rapide"
        heroSub = tv(28, soft)
        heroSub.text = "Tes applis, sans pub"
        val chip = tv(26, Color.BLACK, "sans-serif-medium")
        chip.text = "OK : ouvrir"
        chip.setPadding(p(30), p(10), p(30), p(10))
        val cd = GradientDrawable()
        cd.cornerRadius = p(30).toFloat()
        cd.setColor(Color.WHITE)
        chip.background = cd
        hero.addView(heroTitle)
        hero.addView(heroSub)
        val clp = LinearLayout.LayoutParams(-2, -2)
        clp.topMargin = p(18)
        hero.addView(chip, clp)
        col.addView(hero, LinearLayout.LayoutParams(-1, p(280)))

        scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.clipToPadding = false
        scroll.clipChildren = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        val rows = LinearLayout(this)
        rows.orientation = LinearLayout.VERTICAL
        rows.clipChildren = false
        rows.setPadding(0, 0, 0, p(40))

        rows.addView(rowLabel("Favoris"))
        val favScroll = hscroll(p(16), p(28))
        favRow = row()
        favScroll.addView(favRow)
        rows.addView(favScroll, LinearLayout.LayoutParams(-1, -2))

        rows.addView(rowLabel("Toutes les applis"))
        val appsScroll = hscroll(p(16), p(28))
        appsRow = row()
        appsScroll.addView(appsRow)
        rows.addView(appsScroll, LinearLayout.LayoutParams(-1, -2))

        scroll.addView(rows)
        col.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
    }

    private fun updateClock() {
        val now = Date()
        val h = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        val d = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now)
            .replaceFirstChar { it.uppercase() }
        clockTv.text = "$h   ·   $d"
    }

    private fun showMenu() {
        val items = arrayOf(
            "Réglages Fire TV",
            "Wi-Fi",
            "Affichage",
            if (nature()) "Fond noir" else "Fond nature (paysages)"
        )
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Menu")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> go(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    1 -> go(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    2 -> go(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    3 -> {
                        prefs.edit().putString("bg", if (nature()) "noir" else "nature").apply()
                        applyBg()
                    }
                }
            }
            .show()
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

    private fun banner(a: App): Drawable? {
        val cs = bannerState[a.pkg]
        if (cs != null) return cs.newDrawable(resources)
        if (noBanner.contains(a.pkg)) return null
        val d: Drawable? = try {
            a.ri.activityInfo.loadBanner(packageManager)
                ?: a.ri.activityInfo.applicationInfo.loadBanner(packageManager)
        } catch (e: Exception) { null }
        if (d == null) { noBanner.add(a.pkg); return null }
        val st = d.constantState
        if (st != null) { bannerState[a.pkg] = st; return st.newDrawable(resources) }
        return d
    }

    private fun icon(a: App): Drawable {
        val cs = iconState[a.pkg]
        if (cs != null) return cs.newDrawable(resources)
        val d = a.ri.loadIcon(packageManager)
        val st = d.constantState
        if (st != null) { iconState[a.pkg] = st; return st.newDrawable(resources) }
        return d
    }

    private fun refresh(focusTag: String?) {
        if (dirty || apps.isEmpty()) apps = loadApps()
        dirty = false
        val byPkg = apps.associateBy { it.pkg }
        val favs = favList()
        val favApps = favs.mapNotNull { byPkg[it] }

        favRow.removeAllViews()
        appsRow.removeAllViews()
        favApps.forEach { favRow.addView(card(it, 360, 203, true, it.pkg)) }
        apps.forEach { appsRow.addView(card(it, 288, 162, favs.contains(it.pkg), "a:" + it.pkg)) }

        if (focusTag == null || !focusByTag(focusTag)) {
            if (favRow.childCount > 0) favRow.getChildAt(0).requestFocus()
            else appsRow.getChildAt(0)?.requestFocus()
        }
    }

    private fun focusByTag(t: String): Boolean {
        for (r in listOf(favRow, appsRow)) {
            for (i in 0 until r.childCount) {
                val c = r.getChildAt(i)
                if (c.tag == t) { c.requestFocus(); return true }
            }
        }
        return false
    }

    private fun focusFx(v: View, rd: GradientDrawable, title: String, sub: String) {
        v.isFocusable = true
        v.isClickable = true
        v.setOnFocusChangeListener { view, has ->
            val s = if (has) 1.1f else 1f
            view.animate().scaleX(s).scaleY(s).setDuration(120).start()
            view.elevation = if (has) p(16).toFloat() else 0f
            if (has) {
                rd.setStroke(p(5), Color.WHITE)
                heroTitle.text = title
                heroSub.text = sub
            } else {
                rd.setStroke(0, Color.TRANSPARENT)
            }
        }
    }

    private fun card(a: App, w: Int, h: Int, isFav: Boolean, tag: String): View {
        val f = FrameLayout(this)
        val base = GradientDrawable()
        base.cornerRadius = p(20).toFloat()
        base.setColor(Color.rgb(34, 34, 42))
        f.background = base
        f.clipToOutline = true

        val img = ImageView(this)
        val ban = banner(a)
        if (ban != null) {
            img.setImageDrawable(ban)
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            f.addView(img, FrameLayout.LayoutParams(-1, -1))
        } else {
            img.setImageDrawable(icon(a))
            val s = p(h / 2)
            f.addView(img, FrameLayout.LayoutParams(s, s, Gravity.CENTER))
            val name = tv(22, Color.WHITE)
            name.text = a.label
            name.setPadding(0, 0, 0, p(10))
            f.addView(name, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        }

        val ring = View(this)
        val rd = GradientDrawable()
        rd.cornerRadius = p(20).toFloat()
        rd.setColor(Color.TRANSPARENT)
        ring.background = rd
        f.addView(ring, FrameLayout.LayoutParams(-1, -1))

        val lp = LinearLayout.LayoutParams(p(w), p(h))
        lp.setMargins(p(10), 0, p(10), 0)
        f.layoutParams = lp
        f.tag = tag
        val sub = if (isFav) "Favori  ·  maintiens OK pour retirer"
        else "Maintiens OK pour ajouter aux favoris"
        focusFx(f, rd, a.label, sub)
        f.setOnClickListener { go(a.intent) }
        f.setOnLongClickListener { toggleFav(a.pkg, tag); true }
        return f
    }

    private fun toggleFav(pkg: String, keepTag: String) {
        val l = favList()
        if (l.contains(pkg)) {
            l.remove(pkg)
            Toast.makeText(this, "Retiré des favoris", Toast.LENGTH_SHORT).show()
        } else {
            l.add(pkg)
            Toast.makeText(this, "Ajouté aux favoris", Toast.LENGTH_SHORT).show()
        }
        prefs.edit().putString("favs", l.joinToString(",")).apply()
        refresh(keepTag)
    }

    private fun go(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Impossible d'ouvrir cette appli", Toast.LENGTH_SHORT).show()
        }
    }
}
