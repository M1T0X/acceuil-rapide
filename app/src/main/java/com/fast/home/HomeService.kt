package com.fast.home

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class HomeService : AccessibilityService() {
    private var last = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.getStringExtra("reason") == "homekey") openHome()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            registerReceiver(receiver, IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        } catch (e: Exception) { }
    }

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (e: Exception) { }
        super.onDestroy()
    }

    private fun openHome() {
        val now = SystemClock.elapsedRealtime()
        if (now - last < 300) return
        last = now
        val i = Intent(this, MainActivity::class.java)
        i.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        try { startActivity(i) } catch (e: Exception) { }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == "com.amazon.tv.launcher" || pkg == "com.amazon.firehomestarter") openHome()
    }

    override fun onInterrupt() {}
}
