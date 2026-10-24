package com.fast.home

import android.app.Activity
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
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
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

    private lateinit var root: FrameLayout
    private lateinit var bg: NatureView
    private lateinit var dim: View
    private lateinit var topBar: LinearLayout
    private lateinit var heroBox: LinearLayout
    private lateinit var clockTv: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private lateinit var scroll: ScrollView
    private lateinit var favRow: LinearLayout
    private lateinit var appsRow: LinearLayout
    private lateinit var menuTab: TextView
    private lateinit var menuPanel: GlassFrame
    private lateinit var menuList: LinearLayout

    private val glass = ArrayList<GlassFrame>()
    private var menuOpen = false
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
        bg.resume()
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
        if (menuOpen) closeMenu(false)
        scroll.scrollTo(0, 0)
        if (favRow.childCount > 0) favRow.getChildAt(0).requestFocus()
        animateIn(true)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (menuOpen) closeMenu(true)
    }

    private fun bgMode() = prefs.getString("bg", "aurore") ?: "aurore"

    private fun applyBg() {
        val m = when (bgMode()) {
            "noir" -> 1
            "nature" -> 2
            else -> 0
        }
        bg.setMode(m)
        dim.visibility = if (m == 2) View.VISIBLE else View.GONE
    }

    private fun cycleBg() {
        val next = when (bgMode()) {
            "aurore" -> "noir"
            "noir" -> "nature"
            else -> "aurore"
        }
        prefs.edit().putString("bg", next).apply()
        applyBg()
        Toast.makeText(this, "Fond : $next", Toast.LENGTH_SHORT).show()
    }

    private fun tv(sizePx: Int, color: Int, font: String = "sans-serif"): TextView {
        val t = TextView(this)
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, p(sizePx).toFloat())
        t.setTextColor(color)
        t.typeface = Typeface.create(font, Typeface.NORMAL)
        t.setShadowLayer(p(8).toFloat(), 0f, p(2).toFloat(), Color.argb(120, 0, 0, 0))
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

    private fun rowLabel(text: String): TextView {
        val t = tv(30, Color.argb(230, 255, 255, 255), "sans-serif-medium")
        t.text = text
        t.setPadding(p(26), p(18), 0, 0)
        return t
    }

    private fun tab(label: String, action: () -> Unit): TextView {
        val idle = Color.argb(200, 255, 255, 255)
        val t = tv(28, idle, "sans-serif-medium")
        t.text = label
        t.gravity = Gravity.CENTER
        t.setPadding(p(28), p(12), p(28), p(12))
        val d = GradientDrawable()
        d.cornerRadius = p(30).toFloat()
        d.setColor(Color.TRANSPARENT)
        t.background = d
        t.isFocusable = true
        t.isClickable = true
        t.setOnFocusChangeListener { _, has ->
            d.setColor(if (has) Color.WHITE else Color.TRANSPARENT)
            t.setTextColor(if (has) Color.BLACK else idle)
            t.setShadowLayer(if (has) 0f else p(8).toFloat(), 0f, p(2).toFloat(), Color.argb(120, 0, 0, 0))
            if (has) setHero("Accueil Rapide", label)
        }
        t.setOnClickListener { action() }
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.setMargins(p(4), 0, p(4), 0)
        t.layoutParams = lp
        return t
    }

    private fun setHero(title: String, sub: String) {
        if (heroTitle.text.toString() == title) {
            heroSub.text = sub
            return
        }
        heroTitle.text = title
        heroSub.text = sub
        heroTitle.alpha = 0f
        heroTitle.translationX = -p(40).toFloat()
        heroTitle.animate().alpha(1f).translationX(0f).setDuration(260)
            .setInterpolator(DecelerateInterpolator()).start()
        heroSub.alpha = 0f
        heroSub.animate().alpha(1f).setStartDelay(80).setDuration(260).start()
    }

    private fun animateIn(fast: Boolean) {
        val list = listOf<View>(topBar, heroBox, scroll)
        for ((i, v) in list.withIndex()) {
            v.alpha = 0f
            v.translationY = p(if (fast) 20 else 44).toFloat()
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(if (fast) 0L else i * 90L)
                .setDuration(if (fast) 220L else 460L)
                .setInterpolator(DecelerateInterpolator(1.6f))
                .start()
        }
    }

    private fun buildUi() {
        val white = Color.WHITE
        val soft = Color.argb(215, 255, 255, 255)

        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        bg = NatureView(this)
        bg.onFrame = { for (g in glass) g.invalidate() }
        root.addView(bg, FrameLayout.LayoutParams(-1, -1))
        dim = View(this)
        dim.setBackgroundColor(Color.argb(120, 0, 0, 0))
        root.addView(dim, FrameLayout.LayoutParams(-1, -1))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.clipChildren = false
        col.setPadding(p(80), p(36), p(80), 0)
        root.addView(col, FrameLayout.LayoutParams(-1, -1))

        topBar = LinearLayout(this)
        topBar.gravity = Gravity.CENTER_VERTICAL
        topBar.clipChildren = false

        val navPill = glassPill(40)
        navPill.setPadding(p(10), p(8), p(10), p(8))
        val navRow = row()
        navRow.addView(tab("Accueil") {
            scroll.smoothScrollTo(0, 0)
            if (favRow.childCount > 0) favRow.getChildAt(0).requestFocus()
        })
        navRow.addView(tab("Applis") {
            if (appsRow.childCount > 0) appsRow.getChildAt(0).requestFocus()
        })
        navRow.addView(tab("Boutique") { openStore() })
        menuTab = tab("Menu") { toggleMenu() }
        navRow.addView(menuTab)
        navPill.addView(navRow)
        topBar.addView(navPill, LinearLayout.LayoutParams(-2, -2))

        topBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        val clockPill = glassPill(40)
        clockPill.setPadding(p(32), p(16), p(32), p(16))
        clockTv = tv(30, white, "sans-serif-medium")
        clockPill.addView(clockTv)
        topBar.addView(clockPill, LinearLayout.LayoutParams(-2, -2))
        col.addView(topBar, LinearLayout.LayoutParams(-1, -2))

        heroBox = LinearLayout(this)
        heroBox.orientation = LinearLayout.VERTICAL
        heroBox.gravity = Gravity.BOTTOM
        heroBox.setPadding(p(26), 0, 0, p(10))
        heroTitle = tv(72, white, "sans-serif-medium")
        heroTitle.maxLines = 1
        heroTitle.text = "Accueil Rapide"
        heroSub = tv(28, soft)
        heroSub.text = "Tes applis, sans pub"
        heroBox.addView(heroTitle)
        heroBox.addView(heroSub)
        val chip = glassPill(30)
        chip.setPadding(p(30), p(10), p(30), p(10))
        val chipText = tv(26, white, "sans-serif-medium")
        chipText.text = "OK  ·  ouvrir"
        chip.addView(chipText)
        val clp = LinearLayout.LayoutParams(-2, -2)
        clp.topMargin = p(18)
        heroBox.addView(chip, clp)
        col.addView(heroBox, LinearLayout.LayoutParams(-1, p(280)))

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

        menuPanel = glassPill(36)
        menuPanel.setPadding(p(14), p(14), p(14), p(14))
        menuList = LinearLayout(this)
        menuList.orientation = LinearLayout.VERTICAL
        menuPanel.addView(menuList)
        menuPanel.visibility = View.GONE
        root.addView(menuPanel, FrameLayout.LayoutParams(p(560), -2))

        setContentView(root)
        root.viewTreeObserver.addOnGlobalFocusChangeListener { _, n ->
            if (menuOpen && n != null && !inMenu(n)) closeMenu(false)
        }
        animateIn(false)
    }

    private fun inMenu(v: View): Boolean {
        var c: View? = v
        while (c != null) {
            if (c === menuPanel) return true
            c = c.parent as? View
        }
        return false
    }

    private fun menuItem(label: String, action: () -> Unit): TextView {
        val t = tv(28, Color.WHITE, "sans-serif-medium")
        t.text = label
        t.setPadding(p(28), p(16), p(28), p(16))
        val d = GradientDrawable()
        d.cornerRadius = p(24).toFloat()
        d.setColor(Color.TRANSPARENT)
        t.background = d
        t.isFocusable = true
        t.isClickable = true
        t.setOnFocusChangeListener { _, has ->
            d.setColor(if (has) Color.WHITE else Color.TRANSPARENT)
            t.setTextColor(if (has) Color.BLACK else Color.WHITE)
        }
        t.setOnClickListener {
            closeMenu(true)
            action()
        }
        return t
    }

    private fun toggleMenu() {
        if (menuOpen) closeMenu(true) else showMenu()
    }

    private fun showMenu() {
        val loc = IntArray(2)
        menuTab.getLocationInWindow(loc)
        val lp = menuPanel.layoutParams as FrameLayout.LayoutParams
        lp.leftMargin = loc[0]
        lp.topMargin = loc[1] + menuTab.height + p(16)
        menuPanel.layoutParams = lp

        val bgName = when (bgMode()) {
            "noir" -> "Noir"
            "nature" -> "Paysages"
            else -> "Aurore animée"
        }
        menuList.removeAllViews()
        menuList.addView(menuItem("Réglages Fire TV") {
            go(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        menuList.addView(menuItem("Wi-Fi") {
            go(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        menuList.addView(menuItem("Affichage") {
            go(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        menuList.addView(menuItem("Boutique Amazon") { openStore() })
        menuList.addView(menuItem("Fond : $bgName") { cycleBg() })

        menuPanel.animate().cancel()
        menuPanel.visibility = View.VISIBLE
        menuPanel.alpha = 0f
        menuPanel.pivotY = 0f
        menuPanel.scaleY = 0.85f
        menuPanel.translationY = -p(16).toFloat()
        menuPanel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
            .setInterpolator(DecelerateInterpolator()).start()
        menuOpen = true
        menuList.getChildAt(0).requestFocus()
    }

    private fun closeMenu(refocus: Boolean) {
        if (!menuOpen) return
        menuOpen = false
        menuPanel.animate().cancel()
        menuPanel.animate().alpha(0f).translationY(-p(10).toFloat()).setDuration(150)
            .withEndAction { menuPanel.visibility = View.GONE }.start()
        if (refocus) menuTab.requestFocus()
    }

    private fun openStore() {
        val pm = packageManager
        val i = pm.getLeanbackLaunchIntentForPackage("com.amazon.venezia")
            ?: pm.getLaunchIntentForPackage("com.amazon.venezia")
        if (i != null) go(i)
        else Toast.makeText(this, "Boutique Amazon introuvable", Toast.LENGTH_SHORT).show()
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

    private fun focusFx(v: View, rd: GradientDrawable, title: String, sub: String, bar: View?) {
        v.isFocusable = true
        v.isClickable = true
        v.setOnFocusChangeListener { view, has ->
            val s = if (has) 1.1f else 1f
            view.animate().scaleX(s).scaleY(s)
                .setDuration(if (has) 240L else 160L)
                .setInterpolator(if (has) OvershootInterpolator(1.5f) else DecelerateInterpolator())
                .start()
            view.elevation = if (has) p(16).toFloat() else 0f
            if (has) {
                rd.setStroke(p(5), Color.WHITE)
                setHero(title, sub)
            } else {
                rd.setStroke(0, Color.TRANSPARENT)
            }
            bar?.animate()?.alpha(if (has) 1f else 0f)
                ?.translationY(if (has) 0f else p(24).toFloat())
                ?.setDuration(200)?.start()
        }
    }

    private fun card(a: App, w: Int, h: Int, isFav: Boolean, tag: String): View {
        val f = FrameLayout(this)
        val base = GradientDrawable()
        base.cornerRadius = p(22).toFloat()
        base.setColor(Color.rgb(30, 30, 40))
        f.background = base
        f.clipToOutline = true

        val img = ImageView(this)
        val ban = banner(a)
        var bar: View? = null
        if (ban != null) {
            img.setImageDrawable(ban)
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            f.addView(img, FrameLayout.LayoutParams(-1, -1))

            val gb = GlassFrame(this, bg, 0f, 0f, false)
            gb.setBackgroundColor(Color.argb(90, 0, 0, 0))
            val name = tv(24, Color.WHITE, "sans-serif-medium")
            name.text = a.label
            name.maxLines = 1
            name.setPadding(p(18), p(10), p(18), p(10))
            gb.addView(name)
            gb.alpha = 0f
            gb.translationY = p(24).toFloat()
            f.addView(gb, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            bar = gb
        } else {
            img.setImageDrawable(icon(a))
            val s = p(h / 2)
            f.addView(img, FrameLayout.LayoutParams(s, s, Gravity.CENTER))
            val name = tv(22, Color.WHITE)
            name.text = a.label
            name.setPadding(0, 0, 0, p(10))
            f.addView(name, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        }

        val gloss = View(this)
        gloss.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(48, 255, 255, 255), Color.argb(0, 255, 255, 255))
        )
        f.addView(gloss, FrameLayout.LayoutParams(-1, p(80), Gravity.TOP))

        val ring = View(this)
        val rd = GradientDrawable()
        rd.cornerRadius = p(22).toFloat()
        rd.setColor(Color.TRANSPARENT)
        ring.background = rd
        f.addView(ring, FrameLayout.LayoutParams(-1, -1))

        val lp = LinearLayout.LayoutParams(p(w), p(h))
        lp.setMargins(p(10), 0, p(10), 0)
        f.layoutParams = lp
        f.tag = tag
        val sub = if (isFav) "Favori  ·  maintiens OK pour retirer"
        else "Maintiens OK pour ajouter aux favoris"
        focusFx(f, rd, a.label, sub, bar)
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
