package com.fast.home

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class HomeService : AccessibilityService() {
    private var last = 0L

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == "com.amazon.tv.launcher" || pkg == "com.amazon.firehomestarter") {
            val now = SystemClock.elapsedRealtime()
            if (now - last < 1500) return
            last = now
            val i = Intent(this, MainActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            try { startActivity(i) } catch (ex: Exception) { }
        }
    }

    override fun onInterrupt() {}
}
