package com.tejgokani.strata.core

import kotlin.math.abs
import kotlin.math.pow

object Formatting {
    private val units = listOf("B", "KB", "MB", "GB", "TB", "PB")

    fun bytes(value: Long): String {
        if (value == 0L) return "0 B"
        val absValue = abs(value).toDouble()
        var unitIndex = 0
        var scaled = absValue
        while (scaled >= 1024.0 && unitIndex < units.size - 1) {
            scaled /= 1024.0
            unitIndex++
        }
        val sign = if (value < 0) "-" else ""
        val formatted = if (unitIndex == 0) scaled.toInt().toString() else "%.1f".format(scaled)
        return "$sign$formatted ${units[unitIndex]}"
    }

    fun percent(fraction: Float): String = "${(fraction * 100).toInt()}%"

    fun timestamp(epochMs: Long): String {
        if (epochMs <= 0L) return "—"
        val instant = java.time.Instant.ofEpochMilli(epochMs)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(java.time.ZoneId.systemDefault())
        return formatter.format(instant)
    }
}
