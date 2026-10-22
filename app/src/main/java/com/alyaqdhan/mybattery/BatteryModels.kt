package com.alyaqdhan.mybattery

import java.text.SimpleDateFormat
import java.util.Locale

data class RawDumpEntry(
    val key: String,
    val value: String
)

data class BatteryInfo(
    val healthPercent: Int?,
    val healthSource: String = "",
    val healthUnsupported: Boolean = false,
    val cycleCount: Int?,
    val asocRaw: String = "",
    val bsohRaw: String = "",
    val usageRaw: String = "",
    val llbType: String = "",
    val batteryDateMs: Long = 0L,
    val logFileName: String = "",
    val logTimestampMs: Long = 0L,
    val readSuccess: Boolean = false,
    val errorMessage: String = "",

    // Quick Information & Live Electrical Readings
    val level: Int? = null,
    val scale: Int? = null,
    val temperatureC: Float? = null,
    val voltageV: Float? = null,
    val status: String = "",
    val healthCondition: String = "",
    val currentNowMa: Int? = null,

    // Charging & Power Source
    val acPowered: Boolean? = null,
    val usbPowered: Boolean? = null,
    val wirelessPowered: Boolean? = null,
    val maxChargingCurrentMa: Int? = null,
    val maxChargingVoltageMv: Int? = null,
    val fastCharging: String = "",

    // Historical Peak Extremes
    val maxRecordedTempC: Float? = null,
    val maxRecordedCurrentMa: Int? = null,

    // Capacity & Protection
    val chargeCounterUah: Long? = null,
    val technology: String = "",
    val present: Boolean? = null,
    val protectionMode: String = "",
    val protectionThreshold: Int? = null,
    val authResult: String = "",

    // Raw fields for Advanced section
    val rawEntries: List<RawDumpEntry> = emptyList(),
    val rawBatteryDumpText: String = ""
) {
    val relativeDate: String
        get() {
            if (logTimestampMs == 0L) return ""
            val diffMs = System.currentTimeMillis() - logTimestampMs
            val mins   = diffMs / 60_000
            val hours  = diffMs / 3_600_000
            val days   = diffMs / 86_400_000
            val months = days / 30
            val years  = days / 365
            return when {
                mins  < 1   -> "just now"
                mins  < 60  -> "$mins min${if (mins == 1L) "" else "s"} ago"
                hours < 24  -> "$hours hour${if (hours == 1L) "" else "s"} ago"
                days  < 30  -> "$days day${if (days == 1L) "" else "s"} ago"
                years < 1   -> "$months month${if (months == 1L) "" else "s"} ago"
                else        -> "$years year${if (years == 1L) "" else "s"} ago"
            }
        }

    val batteryDateFormatted: String
        get() {
            if (batteryDateMs == 0L) return ""
            return SimpleDateFormat("d/M/yyyy", Locale.US)
                .format(java.util.Date(batteryDateMs))
        }

    val logTimestampFormatted: String
        get() {
            if (logTimestampMs == 0L) return ""
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(java.util.Date(logTimestampMs))
        }

    val powerSourceSummary: String
        get() = when {
            acPowered == true -> "AC Adapter"
            usbPowered == true -> "USB"
            wirelessPowered == true -> "Wireless"
            acPowered == false && usbPowered == false && wirelessPowered == false -> "Battery (Discharging)"
            status.isNotBlank() -> status
            else -> "Unavailable"
        }

    val asocPercent: Int?
        get() = asocRaw.toIntOrNull() ?: if (healthSource == "asoc") healthPercent else null

    val bsohPercent: Int?
        get() = bsohRaw.toIntOrNull() ?: if (healthSource == "bsoh") healthPercent else null
}

data class DocEntry(
    val id: String,
    val name: String,
    val mime: String,
    val lastModified: Long = 0L
)

/** Overlay error dialogs: replaces the three full-screen error routes. */
sealed class ErrorDialog {
    data class WrongFolder(val errorMessage: String) : ErrorDialog()
    object FolderDeleted  : ErrorDialog()
    object PermissionLost : ErrorDialog()
}
