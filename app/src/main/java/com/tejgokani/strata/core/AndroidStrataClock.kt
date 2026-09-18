package com.tejgokani.strata.core

import android.os.SystemClock

/**
 * Production [StrataClock]. [monotonicMs] is `SystemClock.elapsedRealtime()` — immune to the
 * user changing the wall clock — and [bootId] is resolved once at startup from
 * [com.tejgokani.strata.data.prefs.SettingsStore]'s reboot-detection heuristic (plan §6.8).
 */
class AndroidStrataClock(private val bootIdValue: String) : StrataClock {
    override fun wallClockMs(): Long = System.currentTimeMillis()
    override fun monotonicMs(): Long = SystemClock.elapsedRealtime()
    override fun bootId(): String = bootIdValue
}
