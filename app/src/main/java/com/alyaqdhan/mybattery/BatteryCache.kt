package com.alyaqdhan.mybattery

import android.content.SharedPreferences
import androidx.core.content.edit

object BatteryCache {

    fun cacheBatteryInfo(prefs: SharedPreferences, info: BatteryInfo) {
        if (!info.readSuccess) return
        prefs.edit {
            putString ("cache_log_file_name",      info.logFileName)
            putLong   ("cache_log_timestamp_ms",   info.logTimestampMs)
            putInt    ("cache_health_percent",      info.healthPercent ?: -1)
            putString ("cache_health_source",      info.healthSource)
            putBoolean("cache_health_unsupported", info.healthUnsupported)
            putInt    ("cache_cycle_count",         info.cycleCount ?: -1)
            putLong   ("cache_battery_date_ms",    info.batteryDateMs)
            putString ("cache_asoc_raw",           info.asocRaw)
            putString ("cache_bsoh_raw",           info.bsohRaw)
            putString ("cache_usage_raw",          info.usageRaw)
            putString ("cache_llb_type",           info.llbType)

            // Quick Info & Electrical fields
            putInt    ("cache_level",              info.level ?: -1)
            putInt    ("cache_scale",              info.scale ?: -1)
            putFloat  ("cache_temp_c",             info.temperatureC ?: -999f)
            putFloat  ("cache_voltage_v",          info.voltageV ?: -999f)
            putString ("cache_status",             info.status)
            putString ("cache_health_condition",   info.healthCondition)
            putInt    ("cache_current_now_ma",     info.currentNowMa ?: -999999)

            // Power & Protection
            putBoolean("cache_ac_powered",         info.acPowered ?: false)
            putBoolean("cache_usb_powered",        info.usbPowered ?: false)
            putBoolean("cache_wireless_powered",   info.wirelessPowered ?: false)
            putString ("cache_technology",         info.technology)
            putString ("cache_fast_charging",      info.fastCharging)
            putFloat  ("cache_max_temp_c",         info.maxRecordedTempC ?: -999f)
            putInt    ("cache_max_current_ma",     info.maxRecordedCurrentMa ?: -1)
            putLong   ("cache_charge_counter",     info.chargeCounterUah ?: -1L)
            putString ("cache_protection_mode",    info.protectionMode)
            putInt    ("cache_protection_thresh",  info.protectionThreshold ?: -1)
            putString ("cache_auth_result",        info.authResult)
            putString ("cache_raw_battery_dump",   info.rawBatteryDumpText)
            putBoolean("cache_is_concise_only",    info.isConciseOnly)
        }
    }

    fun loadCachedBatteryInfo(prefs: SharedPreferences): BatteryInfo? {
        val fileName = prefs.getString("cache_log_file_name", null) ?: return null
        if (fileName.isBlank()) return null

        val level = prefs.getInt("cache_level", -1).takeIf { it >= 0 }
        val tempC = prefs.getFloat("cache_temp_c", -999f).takeIf { it > -900f }
        val voltV = prefs.getFloat("cache_voltage_v", -999f).takeIf { it > -900f }
        val status = prefs.getString("cache_status", "") ?: ""
        val currentNow = prefs.getInt("cache_current_now_ma", -999999).takeIf { it != -999999 }
        val maxTemp = prefs.getFloat("cache_max_temp_c", -999f).takeIf { it > -900f }
        val maxCurr = prefs.getInt("cache_max_current_ma", -1).takeIf { it >= 0 }
        val chargeCounter = prefs.getLong("cache_charge_counter", -1L).takeIf { it >= 0L }
        val protectionThresh = prefs.getInt("cache_protection_thresh", -1).takeIf { it >= 0 }
        val rawDump = prefs.getString("cache_raw_battery_dump", "") ?: ""

        val rawEntries = if (rawDump.isNotBlank()) {
            val list = rawDump.lines().mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.contains(":") && !trimmed.startsWith("[") && !trimmed.startsWith("━") && !trimmed.startsWith("=")) {
                    val k = trimmed.substringBefore(":").trim()
                    val v = trimmed.substringAfter(":").trim()
                    if (k.isNotEmpty() && v.isNotEmpty()) RawDumpEntry(k, v) else null
                } else null
            }
            val cutoff = list.indexOfFirst {
                it.key.contains("FullStatusDuration", ignoreCase = true)
            }
            if (cutoff != -1) list.subList(0, cutoff + 1) else list
        } else emptyList()

        return BatteryInfo(
            healthPercent         = prefs.getInt("cache_health_percent", -1).takeIf { it >= 0 },
            healthSource          = prefs.getString("cache_health_source", "") ?: "",
            healthUnsupported     = prefs.getBoolean("cache_health_unsupported", false),
            cycleCount            = prefs.getInt("cache_cycle_count", -1).takeIf { it >= 0 },
            asocRaw               = prefs.getString("cache_asoc_raw", "") ?: "",
            bsohRaw               = prefs.getString("cache_bsoh_raw", "") ?: "",
            usageRaw              = prefs.getString("cache_usage_raw", "") ?: "",
            llbType               = prefs.getString("cache_llb_type", "") ?: "",
            batteryDateMs        = prefs.getLong("cache_battery_date_ms", 0L),
            logFileName           = fileName,
            logTimestampMs        = prefs.getLong("cache_log_timestamp_ms", 0L),
            readSuccess           = true,
            level                 = level,
            scale                 = prefs.getInt("cache_scale", -1).takeIf { it >= 0 },
            temperatureC          = tempC,
            voltageV              = voltV,
            status                = status,
            healthCondition       = prefs.getString("cache_health_condition", "") ?: "",
            currentNowMa          = currentNow,
            acPowered             = if (prefs.contains("cache_ac_powered")) prefs.getBoolean("cache_ac_powered", false) else null,
            usbPowered            = if (prefs.contains("cache_usb_powered")) prefs.getBoolean("cache_usb_powered", false) else null,
            wirelessPowered       = if (prefs.contains("cache_wireless_powered")) prefs.getBoolean("cache_wireless_powered", false) else null,
            technology            = prefs.getString("cache_technology", "") ?: "",
            fastCharging          = prefs.getString("cache_fast_charging", "") ?: "",
            maxRecordedTempC      = maxTemp,
            maxRecordedCurrentMa  = maxCurr,
            chargeCounterUah      = chargeCounter,
            protectionMode        = prefs.getString("cache_protection_mode", "") ?: "",
            protectionThreshold   = protectionThresh,
            authResult            = prefs.getString("cache_auth_result", "") ?: "",
            rawEntries            = rawEntries,
            rawBatteryDumpText    = rawDump,
            isConciseOnly         = prefs.getBoolean("cache_is_concise_only", false)
        )
    }

    fun clearBatteryInfoCache(prefs: SharedPreferences) {
        prefs.edit {
            remove("cache_log_file_name")
            remove("cache_log_timestamp_ms")
            remove("cache_health_percent")
            remove("cache_health_source")
            remove("cache_health_unsupported")
            remove("cache_cycle_count")
            remove("cache_battery_date_ms")
            remove("cache_asoc_raw")
            remove("cache_bsoh_raw")
            remove("cache_usage_raw")
            remove("cache_llb_type")
            remove("cache_level")
            remove("cache_scale")
            remove("cache_temp_c")
            remove("cache_voltage_v")
            remove("cache_status")
            remove("cache_health_condition")
            remove("cache_current_now_ma")
            remove("cache_ac_powered")
            remove("cache_usb_powered")
            remove("cache_wireless_powered")
            remove("cache_technology")
            remove("cache_fast_charging")
            remove("cache_max_temp_c")
            remove("cache_max_current_ma")
            remove("cache_charge_counter")
            remove("cache_protection_mode")
            remove("cache_protection_thresh")
            remove("cache_auth_result")
            remove("cache_raw_battery_dump")
            remove("cache_is_concise_only")
        }
    }
}
