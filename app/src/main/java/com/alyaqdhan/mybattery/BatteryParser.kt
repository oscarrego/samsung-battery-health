package com.alyaqdhan.mybattery

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

object BatteryParser {

    // -- Folder / file helpers -------------------------------------------------

    fun isDumpStateLog(name: String) =
        name.startsWith("dumpState_", ignoreCase = true) &&
                name.endsWith(".log", ignoreCase = true)

    /**
     * Returns a compact display name for a dumpstate log file.
     * Example: dumpstate_SM-G9766HE_20231015_123045.log -> dumpstate_G9766
     */
    fun truncateLogDisplayName(name: String): String {
        val noExt = if (name.endsWith(".log", ignoreCase = true)) name.dropLast(4) else name

        if (!noExt.startsWith("dumpstate_", ignoreCase = true)) {
            return noExt.replace(Regex("""_\d{6,}$"""), "")
        }

        val afterPrefix = noExt.substring("dumpstate_".length)
        val modelMatch = Regex("""[A-Za-z]\d+[A-Za-z]?""").find(afterPrefix)
        val model = modelMatch?.value ?: afterPrefix.substringBefore("_")

        return "dumpstate_$model"
    }

    fun hasPersistedPermission(context: Context, uri: Uri): Boolean {
        val uriStr = uri.toString()
        if (context.contentResolver.persistedUriPermissions
                .any { it.uri.toString() == uriStr && it.isReadPermission }) return true
        return try {
            val treeDocId = DocumentsContract.getTreeDocumentId(uri)
            val docUri    = DocumentsContract.buildDocumentUriUsingTree(uri, treeDocId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null, null, null
            )?.use { it.count >= 0 } ?: false
        } catch (_: Exception) { false }
    }

    fun listChildren(folderUri: Uri, parentId: String, context: Context): List<DocEntry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val result = mutableListOf<DocEntry>()
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idIdx   = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modIdx  = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    val id      = cursor.getString(idIdx)   ?: continue
                    val name    = cursor.getString(nameIdx) ?: ""
                    val mime    = cursor.getString(mimeIdx) ?: ""
                    val lastMod = if (modIdx >= 0) cursor.getLong(modIdx) else 0L
                    result += DocEntry(id, name, mime, lastMod)
                }
            }
        } catch (_: Exception) {}
        return result
    }

    fun findLatestLogEntry(folderUri: Uri, context: Context): DocEntry? {
        val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val topLevel  = listChildren(folderUri, treeDocId, context)
        val dirMime   = DocumentsContract.Document.MIME_TYPE_DIR

        var allLogs = topLevel.filter { isDumpStateLog(it.name) }.toMutableList()
        if (allLogs.isEmpty()) {
            for (child in topLevel) {
                if (child.mime == dirMime)
                    allLogs.addAll(listChildren(folderUri, child.id, context).filter { isDumpStateLog(it.name) })
            }
        }
        return allLogs.maxByOrNull { extractTimestampFromFileName(it.name).takeIf { ts -> ts != 0L } ?: it.lastModified }
    }

    fun listAllLogs(folderUri: Uri, context: Context): List<DocEntry> {
        val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val topLevel  = listChildren(folderUri, treeDocId, context)
        val dirMime   = DocumentsContract.Document.MIME_TYPE_DIR

        var allLogs = topLevel.filter { isDumpStateLog(it.name) }.toMutableList()
        if (allLogs.isEmpty()) {
            for (child in topLevel) {
                if (child.mime == dirMime)
                    allLogs.addAll(listChildren(folderUri, child.id, context).filter { isDumpStateLog(it.name) })
            }
        }
        return allLogs.sortedByDescending { extractTimestampFromFileName(it.name).takeIf { ts -> ts != 0L } ?: it.lastModified }
    }

    // -- Log parsing -----------------------------------------------------------

    fun parseLogEntry(folderUri: Uri, entry: DocEntry, context: Context): BatteryInfo {
        val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, entry.id)
        return try {
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                parseInputStream(stream, entry.name)
            } ?: BatteryInfo(
                healthPercent = null, cycleCount = null,
                logFileName   = entry.name,
                errorMessage  = "Could not open ${entry.name}"
            )
        } catch (e: Exception) {
            BatteryInfo(
                healthPercent = null, cycleCount = null,
                logFileName   = entry.name,
                errorMessage  = "Read error: ${e.localizedMessage}"
            )
        }
    }

    fun parseLatestLog(
        folderUri: Uri,
        context: Context,
        prefs: SharedPreferences
    ): BatteryInfo {
        val logEntry = findLatestLogEntry(folderUri, context)
            ?: return BatteryInfo(healthPercent = null, cycleCount = null, errorMessage = "NO_LOG_FOUND")

        val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, logEntry.id)
        val info = try {
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                parseInputStream(stream, logEntry.name)
            } ?: BatteryInfo(
                healthPercent = null, cycleCount = null,
                logFileName   = logEntry.name,
                errorMessage  = "Could not open ${logEntry.name}"
            )
        } catch (e: Exception) {
            BatteryInfo(
                healthPercent = null, cycleCount = null,
                logFileName   = logEntry.name,
                errorMessage  = "Read error: ${e.localizedMessage}"
            )
        }

        BatteryCache.cacheBatteryInfo(prefs, info)
        return info
    }

    fun smartScan(
        folderUri: Uri,
        context: Context,
        prefs: SharedPreferences
    ): BatteryInfo {
        val latestEntry = findLatestLogEntry(folderUri, context)
        if (latestEntry == null) {
            val cached = BatteryCache.loadCachedBatteryInfo(prefs)
            if (cached != null) return cached
            return BatteryInfo(healthPercent = null, cycleCount = null, errorMessage = "NO_LOG_FOUND")
        }
        val cached = BatteryCache.loadCachedBatteryInfo(prefs)
        if (cached != null && cached.logFileName == latestEntry.name) return cached
        return parseLatestLog(folderUri, context, prefs)
    }

    fun extractBatterySection(folderUri: Uri, entry: DocEntry, context: Context): String {
        val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, entry.id)
        return try {
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                val sections = mutableListOf<Pair<String, String>>()
                var currentSection = ""
                var currentLabel   = ""
                var state          = 0

                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.forEachLine { line ->
                        when (state) {
                            0 -> when {
                                line.contains("BatteryInfoBackUp") -> {
                                    if (currentSection.isNotEmpty())
                                        sections.add(currentLabel to currentSection.trim())
                                    state = 1; currentLabel = "BatteryInfoBackUp"; currentSection = ""
                                }
                                line.contains("DUMP OF SERVICE battery:") -> {
                                    if (currentSection.isNotEmpty())
                                        sections.add(currentLabel to currentSection.trim())
                                    state = 2; currentLabel = "Battery Service Dump"; currentSection = ""
                                }
                            }
                            1 -> {
                                if (line.isNotBlank() && !line.startsWith(" ") && !line.startsWith("\t")
                                    && !line.trimStart().startsWith("m")) {
                                    state = 0
                                    if (line.contains("DUMP OF SERVICE battery:")) {
                                        if (currentSection.isNotEmpty())
                                            sections.add(currentLabel to currentSection.trim())
                                        state = 2; currentLabel = "Battery Service Dump"; currentSection = ""
                                    } else {
                                        sections.add(currentLabel to currentSection.trim())
                                        currentSection = ""; currentLabel = ""
                                    }
                                } else {
                                    currentSection += line + "\n"
                                }
                            }
                            2 -> {
                                if (line.contains("DUMP OF SERVICE") && !line.contains("DUMP OF SERVICE battery:")) {
                                    state = 0
                                    sections.add(currentLabel to currentSection.trim())
                                    currentSection = ""; currentLabel = ""
                                } else {
                                    currentSection += line + "\n"
                                }
                            }
                        }
                    }
                    if (currentSection.isNotEmpty())
                        sections.add(currentLabel to currentSection.trim())
                }

                if (sections.isEmpty()) return@use "(no battery sections found)"

                val logBufferRegex = Regex("""^\[[\w]+LogBuffer\]""")
                sections.joinToString("\n\n") { (label, content) ->
                    val filtered = buildString {
                        var insideBuffer = false
                        content.lines().forEach { line ->
                            val trimmed = line.trim()
                            when {
                                logBufferRegex.containsMatchIn(trimmed) -> insideBuffer = true
                                insideBuffer && trimmed.isEmpty() -> {}
                                insideBuffer && (trimmed.startsWith("[") || (!trimmed.startsWith(" ") &&
                                        trimmed.isNotEmpty() && !trimmed.first().isDigit())) -> {
                                    insideBuffer = false; appendLine(line)
                                }
                                insideBuffer -> {}
                                else -> appendLine(line)
                            }
                        }
                    }.trimEnd()
                    if (label.isNotEmpty()) "=== $label ===\n\n$filtered" else filtered
                }
            } ?: "(could not open file)"
        } catch (e: Exception) {
            "(read error: ${e.localizedMessage})"
        }
    }

    fun extractTimestampFromFileName(name: String): Long {
        val match = Regex("""(\d{12})""").find(name) ?: return 0L
        val f = match.groupValues[1]
        val str = "${f.substring(0,4)}-${f.substring(4,6)}-${f.substring(6,8)} " +
                "${f.substring(8,10)}:${f.substring(10,12)}:00"
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(str)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    fun parseInputStream(stream: InputStream, fileName: String): BatteryInfo {
        var asoc: Int?     = null
        var asocSeen       = false
        var asocRaw        = ""
        var bsoh: Int?     = null
        var bsohRaw        = ""
        var usage: Int?    = null
        var usageRaw       = ""
        var llbType        = ""
        var batteryDateMs  = 0L
        var logTimestampMs = 0L

        // Quick battery information & electrical readings
        var level: Int?                   = null
        var scale: Int?                   = null
        var temperatureC: Float?          = null
        var voltageV: Float?              = null
        var statusStr: String             = ""
        var healthCondStr: String         = ""
        var currentNowMa: Int?            = null

        // Power & Charging details
        var acPowered: Boolean?           = null
        var usbPowered: Boolean?          = null
        var wirelessPowered: Boolean?     = null
        var maxChargingCurrentMa: Int?    = null
        var maxChargingVoltageMv: Int?    = null
        var fastCharging: String          = ""

        // Historical extremes
        var maxRecordedTempC: Float?      = null
        var maxRecordedCurrentMa: Int?    = null

        // Capacity & Protection
        var chargeCounterUah: Long?       = null
        var technology: String            = ""
        var present: Boolean?             = null
        var protectionMode: String        = ""
        var protectionThreshold: Int?     = null
        var authResult: String            = ""

        val rawEntries   = mutableListOf<RawDumpEntry>()
        val rawDumpLines = StringBuilder()

        val fullDateTimeRegex    = Regex("""(?:==\s*dumpstate:|dumpstate:|Build time:)\s*(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})""")
        val bracketDateTimeRegex = Regex("""^\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})]""")
        val dateTimeFmt          = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val llbRegex             = Regex("""^LLB\s+(CAL|MAN):\s*(\d{8})\s*$""", RegexOption.IGNORE_CASE)
        val compactDateFmt       = SimpleDateFormat("yyyyMMdd", Locale.US)

        var insideBatteryService = false
        var insideBatteryBackup  = false

        BufferedReader(InputStreamReader(stream)).use { reader ->
            reader.forEachLine { line ->
                val t = line.trim()

                // State tracker for sections
                when {
                    line.contains("DUMP OF SERVICE battery:") -> {
                        insideBatteryService = true
                        insideBatteryBackup  = false
                    }
                    line.contains("BatteryInfoBackUp") -> {
                        insideBatteryBackup  = true
                        insideBatteryService = false
                    }
                    insideBatteryService && line.contains("DUMP OF SERVICE ") -> {
                        insideBatteryService = false
                    }
                    insideBatteryBackup && line.isNotBlank() && !line.startsWith(" ") && !line.startsWith("\t")
                            && !line.trimStart().startsWith("m") && !line.trimStart().startsWith("L") -> {
                        insideBatteryBackup = false
                    }
                }

                // Collect raw dump entries when inside either battery block
                if (insideBatteryService || insideBatteryBackup) {
                    if (t.contains(":") && !t.startsWith("[") && !t.startsWith("=") && !t.startsWith("-") && t.length < 120) {
                        val key = t.substringBefore(":").trim()
                        val value = t.substringAfter(":").trim()
                        if (key.isNotEmpty() && value.isNotEmpty()) {
                            rawEntries.add(RawDumpEntry(key, value))
                            rawDumpLines.appendLine("$key: $value")
                        }
                    }
                }

                // 1. Health percentages and cycles
                if (t.contains("mSavedBatteryAsoc:")) {
                    asocSeen = true
                    asocRaw  = t.substringAfter("mSavedBatteryAsoc:").trim()
                    asoc     = extractValueOrNull(t, "mSavedBatteryAsoc:")
                }
                if (t.contains("mSavedBatteryBsoh:")) {
                    bsohRaw = t.substringAfter("mSavedBatteryBsoh:").trim()
                    bsoh    = extractValueOrNull(t, "mSavedBatteryBsoh:")
                }
                if (t.contains("mSavedBatteryUsage:")) {
                    usageRaw = t.substringAfter("mSavedBatteryUsage:").trim()
                    val raw  = extractValueOrNull(t, "mSavedBatteryUsage:")
                    usage    = if (raw != null) raw / 100 else null
                }

                // 2. Manufacture / Calibration date
                if (batteryDateMs == 0L) {
                    llbRegex.find(t)?.let { m ->
                        llbType        = m.groupValues[1].uppercase()
                        batteryDateMs = try {
                            compactDateFmt.parse(m.groupValues[2])?.time ?: 0L
                        } catch (_: Exception) { 0L }
                    }
                }

                // 3. Log timestamps
                if (logTimestampMs == 0L) {
                    fullDateTimeRegex.find(t)?.let { m ->
                        logTimestampMs = dateTimeFmt.parse(m.groupValues[1])?.time ?: 0L
                    } ?: bracketDateTimeRegex.find(t)?.let { m ->
                        logTimestampMs = dateTimeFmt.parse(m.groupValues[1])?.time ?: 0L
                    }
                }

                // 4. Quick Battery Info fields
                if (level == null && (insideBatteryService || t.startsWith("level:", ignoreCase = true))) {
                    val rawL = extractValueOrNull(t, "level:")
                    if (rawL != null && rawL in 0..100) level = rawL
                }

                if (scale == null && (insideBatteryService || t.startsWith("scale:", ignoreCase = true))) {
                    extractValueOrNull(t, "scale:")?.let { scale = it }
                }

                // Temperature: standard Android dumpsys is tenths of degree C (e.g. 295 -> 29.5 C)
                if (temperatureC == null && (insideBatteryService || t.startsWith("temperature:", ignoreCase = true))) {
                    if (Regex("""^temperature\s*:""", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
                        val rawTemp = extractValueOrNull(t, "temperature:")
                        if (rawTemp != null && rawTemp > -400) {
                            temperatureC = if (rawTemp > 100) rawTemp / 10f else rawTemp.toFloat()
                        }
                    }
                }

                // Voltage: standard Android dumpsys is mV (e.g. 4125 -> 4.125 V) or uV
                if (voltageV == null && (insideBatteryService || t.startsWith("voltage:", ignoreCase = true))) {
                    if (Regex("""^voltage\s*:""", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
                        val rawV = extractValueOrNull(t, "voltage:")
                        if (rawV != null && rawV > 0) {
                            voltageV = when {
                                rawV in 1000..9999   -> rawV / 1000f
                                rawV >= 10000        -> rawV / 1000000f
                                else                 -> rawV.toFloat()
                            }
                        }
                    }
                }

                // Status
                if (statusStr.isBlank() && (insideBatteryService || t.startsWith("status:", ignoreCase = true))) {
                    if (Regex("""^status\s*:""", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
                        val rawStatus = t.substringAfter(":").trim()
                        statusStr = when (rawStatus) {
                            "1" -> "Unknown"
                            "2" -> "Charging"
                            "3" -> "Discharging"
                            "4" -> "Not charging"
                            "5" -> "Full"
                            else -> if (rawStatus.isNotBlank() && !rawStatus.all { it.isDigit() }) {
                                rawStatus.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
                            } else ""
                        }
                    }
                }

                // Health condition
                if (healthCondStr.isBlank() && (insideBatteryService || t.startsWith("health:", ignoreCase = true))) {
                    if (Regex("""^health\s*:""", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
                        val rawH = t.substringAfter(":").trim()
                        healthCondStr = when (rawH) {
                            "1" -> "Unknown"
                            "2" -> "Good"
                            "3" -> "Overheat"
                            "4" -> "Dead"
                            "5" -> "Over voltage"
                            "6" -> "Unspecified failure"
                            "7" -> "Cold"
                            else -> if (rawH.isNotBlank() && !rawH.all { it.isDigit() }) {
                                rawH.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
                            } else ""
                        }
                    }
                }

                // Power sources
                if (acPowered == null && t.startsWith("AC powered:", ignoreCase = true)) {
                    acPowered = t.substringAfter(":").trim().startsWith("true", ignoreCase = true)
                }
                if (usbPowered == null && t.startsWith("USB powered:", ignoreCase = true)) {
                    usbPowered = t.substringAfter(":").trim().startsWith("true", ignoreCase = true)
                }
                if (wirelessPowered == null && t.startsWith("Wireless powered:", ignoreCase = true)) {
                    wirelessPowered = t.substringAfter(":").trim().startsWith("true", ignoreCase = true)
                }

                // Max charging current & voltage
                if (maxChargingCurrentMa == null && t.contains("Max charging current:", ignoreCase = true)) {
                    val rawCur = extractValueOrNull(t, "Max charging current:")
                    if (rawCur != null && rawCur > 0) {
                        maxChargingCurrentMa = if (rawCur > 10000) rawCur / 1000 else rawCur
                    }
                }
                if (maxChargingVoltageMv == null && t.contains("Max charging voltage:", ignoreCase = true)) {
                    val rawVolt = extractValueOrNull(t, "Max charging voltage:")
                    if (rawVolt != null && rawVolt > 0) {
                        maxChargingVoltageMv = if (rawVolt > 20000) rawVolt / 1000 else rawVolt
                    }
                }

                // Fast charging settings
                if (fastCharging.isBlank()) {
                    when {
                        t.contains("mSuperFastCharging: true", ignoreCase = true) -> fastCharging = "Super Fast Charging"
                        t.contains("mFastCharging: true", ignoreCase = true)      -> fastCharging = "Fast Charging"
                        t.contains("Fast charging: true", ignoreCase = true)       -> fastCharging = "Fast Charging"
                    }
                }

                // Historical peaks
                if (maxRecordedTempC == null && t.contains("mSavedBatteryMaxTemp:")) {
                    val rawMaxT = extractValueOrNull(t, "mSavedBatteryMaxTemp:")
                    if (rawMaxT != null && rawMaxT > 0) {
                        maxRecordedTempC = if (rawMaxT > 100) rawMaxT / 10f else rawMaxT.toFloat()
                    }
                }
                if (maxRecordedCurrentMa == null && t.contains("mSavedBatteryMaxCurrent:")) {
                    val rawMaxI = extractValueOrNull(t, "mSavedBatteryMaxCurrent:")
                    if (rawMaxI != null && rawMaxI > 0) {
                        maxRecordedCurrentMa = rawMaxI
                    }
                }

                // Instantaneous current
                if (currentNowMa == null) {
                    val curMatch = Regex("""(?:current now|mCurrentNow|sec_bat_current)\s*:\s*(-?\d+)""", RegexOption.IGNORE_CASE).find(t)
                    curMatch?.let { m ->
                        val rawI = m.groupValues[1].toIntOrNull()
                        if (rawI != null) {
                            currentNowMa = if (abs(rawI) > 10000) rawI / 1000 else rawI
                        }
                    }
                }

                // Capacity & Battery details
                if (chargeCounterUah == null && t.contains("Charge counter:", ignoreCase = true)) {
                    val rawCc = extractLongValueOrNull(t, "Charge counter:")
                    if (rawCc != null && rawCc > 0L) chargeCounterUah = rawCc
                }

                if (technology.isBlank() && t.startsWith("technology:", ignoreCase = true)) {
                    technology = t.substringAfter(":").trim()
                }

                if (present == null && t.startsWith("present:", ignoreCase = true)) {
                    present = t.substringAfter(":").trim().startsWith("true", ignoreCase = true)
                }

                // Protection settings
                if (protectionThreshold == null && t.contains("mProtectionThreshold:", ignoreCase = true)) {
                    extractValueOrNull(t, "mProtectionThreshold:")?.let { protectionThreshold = it }
                }
                if (protectionMode.isBlank()) {
                    if (t.contains("mBatteryProtection:", ignoreCase = true)) {
                        val rawMode = t.substringAfter("mBatteryProtection:").trim()
                        protectionMode = when (rawMode) {
                            "1" -> "Enabled"
                            "0" -> "Disabled"
                            else -> rawMode
                        }
                    } else if (t.contains("Protect Battery:", ignoreCase = true)) {
                        protectionMode = t.substringAfter(":").trim()
                    }
                }

                // Authentication result
                if (authResult.isBlank()) {
                    when {
                        t.contains("mPassedAuth: 1", ignoreCase = true) || t.contains("mPassAuth: 1", ignoreCase = true) ->
                            authResult = "Passed"
                        t.contains("mPassedAuth: 0", ignoreCase = true) || t.contains("mPassAuth: 0", ignoreCase = true) ->
                            authResult = "Unverified (0)"
                        t.contains("battery auth: pass", ignoreCase = true) ->
                            authResult = "Passed"
                    }
                }
            }
        }

        if (logTimestampMs == 0L) logTimestampMs = extractTimestampFromFileName(fileName)

        val (resolvedHealth, healthSource, healthUnsupported) = when {
            asoc != null -> Triple(asoc, "asoc", false)
            bsoh != null -> Triple(bsoh, "bsoh", false)
            else         -> Triple(null, "", true)
        }

        val readSuccess = resolvedHealth != null || bsoh != null || usage != null ||
                batteryDateMs != 0L || asocSeen || level != null || temperatureC != null

        return BatteryInfo(
            healthPercent         = resolvedHealth,
            healthSource          = healthSource,
            healthUnsupported     = healthUnsupported,
            cycleCount            = usage,
            asocRaw               = asocRaw,
            bsohRaw               = bsohRaw,
            usageRaw              = usageRaw,
            llbType               = llbType,
            batteryDateMs        = batteryDateMs,
            logFileName           = fileName,
            logTimestampMs        = logTimestampMs,
            readSuccess           = readSuccess,
            level                 = level,
            scale                 = scale,
            temperatureC          = temperatureC,
            voltageV              = voltageV,
            status                = statusStr,
            healthCondition       = healthCondStr,
            currentNowMa          = currentNowMa,
            acPowered             = acPowered,
            usbPowered            = usbPowered,
            wirelessPowered       = wirelessPowered,
            maxChargingCurrentMa  = maxChargingCurrentMa,
            maxChargingVoltageMv  = maxChargingVoltageMv,
            fastCharging          = fastCharging,
            maxRecordedTempC      = maxRecordedTempC,
            maxRecordedCurrentMa  = maxRecordedCurrentMa,
            chargeCounterUah      = chargeCounterUah,
            technology            = technology,
            present               = present,
            protectionMode        = protectionMode,
            protectionThreshold   = protectionThreshold,
            authResult            = authResult,
            rawEntries            = rawEntries,
            rawBatteryDumpText    = rawDumpLines.toString().trim()
        )
    }

    private fun extractValueOrNull(line: String, key: String): Int? {
        val raw = line.substringAfter(key, "").trim()
        if (raw.isBlank()) return null
        val stripped = when {
            raw.startsWith("[") && raw.contains("]") ->
                raw.removePrefix("[").substringBefore("]").trim()
            else -> raw.takeWhile { it.isDigit() || it == '-' }
        }
        if (stripped.equals("unsupported", ignoreCase = true)) return null
        val v = stripped.toIntOrNull() ?: return null
        return if (v < 0) null else v
    }

    private fun extractLongValueOrNull(line: String, key: String): Long? {
        val raw = line.substringAfter(key, "").trim()
        if (raw.isBlank()) return null
        val stripped = when {
            raw.startsWith("[") && raw.contains("]") ->
                raw.removePrefix("[").substringBefore("]").trim()
            else -> raw.takeWhile { it.isDigit() || it == '-' }
        }
        if (stripped.equals("unsupported", ignoreCase = true)) return null
        val v = stripped.toLongOrNull() ?: return null
        return if (v < 0) null else v
    }
}
