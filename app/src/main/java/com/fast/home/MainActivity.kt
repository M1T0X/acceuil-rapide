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
    private lateinit var clock: TextView
    private lateinit var dateTv: TextView
    private lateinit var nameTv: TextView
    private lateinit var favRow: LinearLayout
    private lateinit var dockRow: LinearLayout
    private val ui = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("home", MODE_PRIVATE) }
    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            ui.postDelayed(this, 15000)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        // On reste sur l'accueil
    }

    private fun buildUi() {
        val root = FrameLayout(this)
        bg = NatureView(this)
        root.addView(bg, FrameLayout.LayoutParams(-1, -1))

        val scrim = View(this)
        scrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(70, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(170, 0, 0, 0))
        )
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(64), dp(36), dp(64), dp(28))
        root.addView(col, FrameLayout.LayoutParams(-1, -1))

        clock = TextView(this)
        clock.textSize = 44f
        clock.setTextColor(Color.WHITE)
        clock.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        col.addView(clock)

        dateTv = TextView(this)
        dateTv.textSize = 20f
        dateTv.setTextColor(Color.argb(204, 255, 255, 255))
        col.addView(dateTv)

        col.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        val favLabel = TextView(this)
        favLabel.text = "Favoris"
        favLabel.textSize = 18f
        favLabel.setTextColor(Color.argb(204, 255, 255, 255))
        col.addView(favLabel)

        val favScroll = hscroll()
        favRow = row()
        favScroll.addView(favRow)
        col.addView(favScroll, LinearLayout.LayoutParams(-1, -2))

        nameTv = TextView(this)
        nameTv.textSize = 22f
        nameTv.setTextColor(Color.WHITE)
        nameTv.gravity = Gravity.CENTER
        nameTv.setPadding(0, dp(8), 0, dp(8))
        col.addView(nameTv, LinearLayout.LayoutParams(-1, -2))

        val dockBox = LinearLayout(this)
        dockBox.clipChildren = false
        dockBox.clipToPadding = false
        dockBox.setPadding(dp(14), dp(6), dp(14), dp(6))
        val dockBg = GradientDrawable()
        dockBg.cornerRadius = dp(36).toFloat()
        dockBg.setColor(Color.argb(110, 20, 20, 30))
        dockBox.background = dockBg
        val dockScroll = hscroll()
        dockRow = row()
        dockScroll.addView(dockRow)
        dockBox.addView(dockScroll)
        val dockLp = LinearLayout.LayoutParams(-2, -2)
        dockLp.gravity = Gravity.CENTER_HORIZONTAL
        col.addView(dockBox, dockLp)

        val hint = TextView(this)
        hint.text = "Maintiens OK sur une appli pour l'ajouter ou la retirer des favoris"
        hint.textSize = 14f
        hint.gravity = Gravity.CENTER
        hint.setTextColor(Color.argb(150, 255, 255, 255))
        hint.setPadding(0, dp(10), 0, 0)
        col.addView(hint, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
    }

    private fun hscroll(): HorizontalScrollView {
        val s = HorizontalScrollView(this)
        s.isHorizontalScrollBarEnabled = false
        s.clipToPadding = false
        s.clipChildren = false
        s.overScrollMode = View.OVER_SCROLL_NEVER
        s.setPadding(dp(8), dp(20), dp(8), dp(20))
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

    private fun updateClock() {
        val now = Date()
        clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        dateTv.text = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now)
            .replaceFirstChar { it.uppercase() }
    }

    private fun loadApps(): List<App> {
        val pm = packageManager
        val out = LinkedHashMap<String, App>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(q, 0)) {
                val ai = ri.activityInfo.applicationInfo
                val p = ai.packageName
                if (p == packageName || out.containsKey(p)) continue
                val system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                if (system) continue
                val i = Intent(Intent.ACTION_MAIN).addCategory(cat)
                    .setClassName(p, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                out[p] = App(p, ri.loadLabel(pm).toString(), i, ri)
            }
        }
        return out.values.sortedBy { it.label.lowercase() }
    }

    private fun favList(): MutableList<String> =
        (prefs.getString("favs", favDefaults) ?: "").split(",").filter { it.isNotBlank() }.toMutableList()

    private fun refresh(focusTag: String?) {
        val apps = loadApps()
        val byPkg = apps.associateBy { it.pkg }
        val favApps = favList().mapNotNull { byPkg[it] }
        val rest = apps.filter { a -> favApps.none { it.pkg == a.pkg } }

        favRow.removeAllViews()
        dockRow.removeAllViews()
        favApps.forEach { favRow.addView(tile(it)) }
        (favApps + rest).forEach { dockRow.addView(dockIcon(it)) }
        dockRow.addView(settingsIcon())

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

    private fun focusFx(v: View, scale: Float, name: String) {
        v.isFocusable = true
        v.isClickable = true
        v.setOnFocusChangeListener { view, has ->
            val s = if (has) scale else 1f
            view.animate().scaleX(s).scaleY(s).setDuration(140).start()
            view.elevation = if (has) dp(10).toFloat() else 0f
            (view.background as? GradientDrawable)?.setStroke(if (has) dp(3) else 0, Color.WHITE)
            if (has) nameTv.text = name
        }
    }

    private fun tile(a: App): View {
        val pm = packageManager
        val f = FrameLayout(this)
        val d = GradientDrawable()
        d.cornerRadius = dp(16).toFloat()
        d.setColor(Color.argb(200, 24, 24, 32))
        f.background = d
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
            f.addView(img, FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER))
        }

        val lp = LinearLayout.LayoutParams(dp(288), dp(162))
        lp.setMargins(dp(10), 0, dp(10), 0)
        f.layoutParams = lp
        f.tag = a.pkg
        focusFx(f, 1.08f, a.label)
        f.setOnClickListener { go(a.intent) }
        f.setOnLongClickListener { toggleFav(a.pkg); true }
        return f
    }

    private fun dockIcon(a: App): View {
        val f = FrameLayout(this)
        val d = GradientDrawable()
        d.cornerRadius = dp(22).toFloat()
        d.setColor(Color.argb(60, 255, 255, 255))
        f.background = d

        val img = ImageView(this)
        img.setImageDrawable(a.ri.loadIcon(packageManager))
        f.addView(img, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))

        val lp = LinearLayout.LayoutParams(dp(76), dp(76))
        lp.setMargins(dp(8), 0, dp(8), 0)
        f.layoutParams = lp
        f.tag = a.pkg
        focusFx(f, 1.25f, a.label)
        f.setOnClickListener { go(a.intent) }
        f.setOnLongClickListener { toggleFav(a.pkg); true }
        return f
    }

    private fun settingsIcon(): View {
        val t = TextView(this)
        t.text = "⚙"
        t.textSize = 30f
        t.gravity = Gravity.CENTER
        t.setTextColor(Color.WHITE)
        val d = GradientDrawable()
        d.cornerRadius = dp(22).toFloat()
        d.setColor(Color.argb(60, 255, 255, 255))
        t.background = d
        val lp = LinearLayout.LayoutParams(dp(76), dp(76))
        lp.setMargins(dp(8), 0, dp(8), 0)
        t.layoutParams = lp
        t.tag = "settings"
        focusFx(t, 1.25f, "Paramètres")
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
