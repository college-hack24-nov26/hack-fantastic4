package com.vision.app.detector

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

/** Android thermal status is a device pressure signal, not a surface temperature. */
class PowerThermalMonitor(private val context: Context) {
    private val power = context.getSystemService(PowerManager::class.java)

    fun snapshot(): PowerSnapshot {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return PowerSnapshot(
            thermalStatus = if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else null,
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else null,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            batterySaver = power.isPowerSaveMode,
        )
    }
}

data class PowerSnapshot(val thermalStatus: Int?, val batteryPercent: Int?, val charging: Boolean, val batterySaver: Boolean)
