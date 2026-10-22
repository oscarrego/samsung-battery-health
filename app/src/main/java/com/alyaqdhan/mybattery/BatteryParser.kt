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

    fun parseLogEntry(
        folderUri: Uri,
        entry: DocEntry,
        context: Context,
        isCancelled: () -> Boolean = { false },
        conciseOnly: Boolean = false
    ): BatteryInfo {
        val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, entry.id)
        return try {
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                parseInputStream(stream, entry.name, isCancelled, conciseOnly)
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
        prefs: SharedPreferences,
        isCancelled: () -> Boolean = { false },
        conciseOnly: Boolean = false
    ): BatteryInfo {
        val logEntry = findLatestLogEntry(folderUri, context)
            ?: return BatteryInfo(healthPercent = null, cycleCount = null, errorMessage = "NO_LOG_FOUND")

        val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, logEntry.id)
        val info = try {
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                parseInputStream(stream, logEntry.name, isCancelled, conciseOnly)
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
        prefs: SharedPreferences,
        isCancelled: () -> Boolean = { false },
        conciseOnly: Boolean = false
    ): BatteryInfo {
        val latestEntry = findLatestLogEntry(folderUri, context)
        if (latestEntry == null) {
            val cached = BatteryCache.loadCachedBatteryInfo(prefs)
            if (cached != null) return cached
            return BatteryInfo(healthPercent = null, cycleCount = null, errorMessage = "NO_LOG_FOUND")
        }
        val cached = BatteryCache.loadCachedBatteryInfo(prefs)
        if (cached != null && cached.logFileName == latestEntry.name && (!cached.isConciseOnly || conciseOnly)) {
            return cached
        }
        return parseLatestLog(folderUri, context, prefs, isCancelled, conciseOnly)
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

    // Precompiled regex patterns to avoid repeated compilation in loops
    private val fullDateTimeRegex    = Regex("""(?:==\s*dumpstate:|dumpstate:|Build time:)\s*(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})""")
    private val bracketDateTimeRegex = Regex("""^\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})]""")
    private val dateTimeFmt          = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val compactDateFmt       = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val llbRegex             = Regex("""^LLB\s+(CAL|MAN):\s*(\d{8})""", RegexOption.IGNORE_CASE)
    private val tempRegex            = Regex("""^temperature\s*:""", RegexOption.IGNORE_CASE)
    private val voltRegex            = Regex("""^voltage\s*:""", RegexOption.IGNORE_CASE)
    private val statusRegex          = Regex("""^status\s*:""", RegexOption.IGNORE_CASE)
    private val healthRegex          = Regex("""^health\s*:""", RegexOption.IGNORE_CASE)
    private val currentRegex         = Regex("""(?:current now|mCurrentNow|sec_bat_current)\s*:\s*(-?\d+)""", RegexOption.IGNORE_CASE)

    fun parseInputStream(
        stream: InputStream,
        fileName: String,
        isCancelled: () -> Boolean = { false },
        conciseOnly: Boolean = false
    ): BatteryInfo {
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

        var insideBatteryService = false
        var batteryServiceSeen   = false
        var insideBatteryBackup  = false
        var batteryBackupSeen    = false
        var lineIndex            = 0
        var isConciseResult      = conciseOnly

        BufferedReader(InputStreamReader(stream, Charsets.UTF_8), 65536).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                lineIndex++

                // Cooperative cancellation check every 128 lines for instant UI responsiveness
                if (lineIndex % 128 == 0 && isCancelled()) {
                    isConciseResult = true
                    break
                }

                if (line.isEmpty()) continue

                // Fast early exit: if concise requested and core metrics found
                if (isConciseResult && (asoc != null || bsoh != null || asocSeen) && usage != null) {
                    break
                }

                // If both sections are done or we already got all battery data, stop scanning logcat/dmesg
                if ((batteryServiceSeen && batteryBackupSeen) ||
                    (batteryServiceSeen && (asoc != null || bsoh != null || asocSeen) && usage != null && level != null)
                ) {
                    break
                }

                // State tracker for sections
                if (line.contains("DUMP OF SERVICE battery:")) {
                    insideBatteryService = true
                    batteryServiceSeen   = true
                    insideBatteryBackup  = false
                    continue
                }

                if (insideBatteryService) {
                    if (line.startsWith("DUMP OF SERVICE ") || line.startsWith("--------------------------------------------------")) {
                        insideBatteryService = false
                        batteryServiceSeen   = true

                        // Exit immediately if core metrics already parsed
                        if ((asoc != null || bsoh != null || asocSeen) && usage != null) {
                            break
                        }
                    }
                }

                if (line.contains("BatteryInfoBackUp")) {
                    insideBatteryBackup  = true
                    batteryBackupSeen    = true
                    insideBatteryService = false
                    continue
                }

                if (insideBatteryBackup && !line.startsWith(" ") && !line.startsWith("\t")
                    && !line.trimStart().startsWith("m") && !line.trimStart().startsWith("L") && line.isNotBlank()) {
                    insideBatteryBackup = false
                    batteryBackupSeen   = true

                    if (batteryServiceSeen || ((asoc != null || bsoh != null || asocSeen) && usage != null)) {
                        break
                    }
                }

                // Collect raw dump entries and parse values inside either battery block
                if (insideBatteryService || insideBatteryBackup) {
                    val t = line.trim()
                    if (t.contains(":") && !t.startsWith("[") && !t.startsWith("=") && !t.startsWith("-") && t.length < 120) {
                        val key = t.substringBefore(":").trim()
                        val value = t.substringAfter(":").trim()
                        if (key.isNotEmpty() && value.isNotEmpty() && !isConciseResult) {
                            rawEntries.add(RawDumpEntry(key, value))
                            rawDumpLines.appendLine("$key: $value")
                        }
                    }

                    // 1. Health percentages and cycles
                    if (line.contains("mSavedBatteryAsoc:")) {
                        asocSeen = true
                        asocRaw  = line.substringAfter("mSavedBatteryAsoc:").trim()
                        asoc     = extractValueOrNull(line, "mSavedBatteryAsoc:")
                    }
                    if (line.contains("mSavedBatteryBsoh:")) {
                        bsohRaw = line.substringAfter("mSavedBatteryBsoh:").trim()
                        bsoh    = extractValueOrNull(line, "mSavedBatteryBsoh:")
                    }
                    if (line.contains("mSavedBatteryUsage:")) {
                        usageRaw = line.substringAfter("mSavedBatteryUsage:").trim()
                        val raw  = extractValueOrNull(line, "mSavedBatteryUsage:")
                        usage    = if (raw != null) raw / 100 else null
                    }

                    // 4. Quick Battery Info fields
                    if (level == null && (insideBatteryService || line.contains("level:"))) {
                        val rawL = extractValueOrNull(line, "level:")
                        if (rawL != null && rawL in 0..100) level = rawL
                    }

                    if (scale == null && (insideBatteryService || line.contains("scale:"))) {
                        extractValueOrNull(line, "scale:")?.let { scale = it }
                    }

                    if (temperatureC == null && (insideBatteryService || line.contains("temperature:"))) {
                        if (tempRegex.containsMatchIn(t)) {
                            val rawTemp = extractValueOrNull(line, "temperature:")
                            if (rawTemp != null && rawTemp > -400) {
                                temperatureC = if (rawTemp > 100) rawTemp / 10f else rawTemp.toFloat()
                            }
                        }
                    }

                    if (voltageV == null && (insideBatteryService || line.contains("voltage:"))) {
                        if (voltRegex.containsMatchIn(t)) {
                            val rawV = extractValueOrNull(line, "voltage:")
                            if (rawV != null && rawV > 0) {
                                voltageV = when {
                                    rawV in 1000..9999   -> rawV / 1000f
                                    rawV >= 10000        -> rawV / 1000000f
                                    else                 -> rawV.toFloat()
                                }
                            }
                        }
                    }

                    if (statusStr.isBlank() && (insideBatteryService || line.contains("status:"))) {
                        if (statusRegex.containsMatchIn(t)) {
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

                    if (healthCondStr.isBlank() && (insideBatteryService || line.contains("health:"))) {
                        if (healthRegex.containsMatchIn(t)) {
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

                    if (acPowered == null && line.contains("AC powered:", ignoreCase = true)) {
                        acPowered = line.substringAfter(":").trim().startsWith("true", ignoreCase = true)
                    }
                    if (usbPowered == null && line.contains("USB powered:", ignoreCase = true)) {
                        usbPowered = line.substringAfter(":").trim().startsWith("true", ignoreCase = true)
                    }
                    if (wirelessPowered == null && line.contains("Wireless powered:", ignoreCase = true)) {
                        wirelessPowered = line.substringAfter(":").trim().startsWith("true", ignoreCase = true)
                    }

                    if (maxChargingCurrentMa == null && line.contains("Max charging current:", ignoreCase = true)) {
                        val rawCur = extractValueOrNull(line, "Max charging current:")
                        if (rawCur != null && rawCur > 0) {
                            maxChargingCurrentMa = if (rawCur > 10000) rawCur / 1000 else rawCur
                        }
                    }
                    if (maxChargingVoltageMv == null && line.contains("Max charging voltage:", ignoreCase = true)) {
                        val rawVolt = extractValueOrNull(line, "Max charging voltage:")
                        if (rawVolt != null && rawVolt > 0) {
                            maxChargingVoltageMv = if (rawVolt > 20000) rawVolt / 1000 else rawVolt
                        }
                    }

                    if (fastCharging.isBlank()) {
                        when {
                            line.contains("mSuperFastCharging: true", ignoreCase = true) -> fastCharging = "Super Fast Charging"
                            line.contains("mFastCharging: true", ignoreCase = true)      -> fastCharging = "Fast Charging"
                            line.contains("Fast charging: true", ignoreCase = true)       -> fastCharging = "Fast Charging"
                        }
                    }

                    if (maxRecordedTempC == null && line.contains("mSavedBatteryMaxTemp:")) {
                        val rawMaxT = extractValueOrNull(line, "mSavedBatteryMaxTemp:")
                        if (rawMaxT != null && rawMaxT > 0) {
                            maxRecordedTempC = if (rawMaxT > 100) rawMaxT / 10f else rawMaxT.toFloat()
                        }
                    }
                    if (maxRecordedCurrentMa == null && line.contains("mSavedBatteryMaxCurrent:")) {
                        val rawMaxI = extractValueOrNull(line, "mSavedBatteryMaxCurrent:")
                        if (rawMaxI != null && rawMaxI > 0) {
                            maxRecordedCurrentMa = rawMaxI
                        }
                    }

                    if (currentNowMa == null && (line.contains("current now", ignoreCase = true) || line.contains("sec_bat_current", ignoreCase = true) || line.contains("mCurrentNow", ignoreCase = true))) {
                        currentRegex.find(line)?.let { m ->
                            val rawI = m.groupValues[1].toIntOrNull()
                            if (rawI != null) {
                                currentNowMa = if (abs(rawI) > 10000) rawI / 1000 else rawI
                            }
                        }
                    }

                    if (chargeCounterUah == null && line.contains("Charge counter:", ignoreCase = true)) {
                        val rawCc = extractLongValueOrNull(line, "Charge counter:")
                        if (rawCc != null && rawCc > 0L) chargeCounterUah = rawCc
                    }

                    if (technology.isBlank() && line.contains("technology:", ignoreCase = true)) {
                        technology = line.substringAfter(":").trim()
                    }

                    if (present == null && line.contains("present:", ignoreCase = true)) {
                        present = line.substringAfter(":").trim().startsWith("true", ignoreCase = true)
                    }

                    if (protectionThreshold == null && line.contains("mProtectionThreshold:", ignoreCase = true)) {
                        extractValueOrNull(line, "mProtectionThreshold:")?.let { protectionThreshold = it }
                    }
                    if (protectionMode.isBlank()) {
                        if (line.contains("mBatteryProtection:", ignoreCase = true)) {
                            val rawMode = line.substringAfter("mBatteryProtection:").trim()
                            protectionMode = when (rawMode) {
                                "1" -> "Enabled"
                                "0" -> "Disabled"
                                else -> rawMode
                            }
                        } else if (line.contains("Protect Battery:", ignoreCase = true)) {
                            protectionMode = line.substringAfter(":").trim()
                        }
                    }

                    if (authResult.isBlank()) {
                        when {
                            line.contains("mPassedAuth: 1", ignoreCase = true) || line.contains("mPassAuth: 1", ignoreCase = true) ->
                                authResult = "Passed"
                            line.contains("mPassedAuth: 0", ignoreCase = true) || line.contains("mPassAuth: 0", ignoreCase = true) ->
                                authResult = "Unverified (0)"
                            line.contains("battery auth: pass", ignoreCase = true) ->
                                authResult = "Passed"
                        }
                    }
                } else {
                    // Lines outside battery service: only inspect for dates/timestamps and fallback registers
                    if (batteryDateMs == 0L && (line.startsWith("LLB") || line.contains("LLB "))) {
                        llbRegex.find(line.trim())?.let { m ->
                            llbType        = m.groupValues[1].uppercase()
                            batteryDateMs = try {
                                compactDateFmt.parse(m.groupValues[2])?.time ?: 0L
                            } catch (_: Exception) { 0L }
                        }
                    }

                    if (logTimestampMs == 0L && (line.startsWith("==") || line.startsWith("dumpstate:") || line.startsWith("["))) {
                        val t = line.trim()
                        fullDateTimeRegex.find(t)?.let { m ->
                            logTimestampMs = dateTimeFmt.parse(m.groupValues[1])?.time ?: 0L
                        } ?: bracketDateTimeRegex.find(t)?.let { m ->
                            logTimestampMs = dateTimeFmt.parse(m.groupValues[1])?.time ?: 0L
                        }
                    }

                    if (line.contains("mSavedBattery")) {
                        if (line.contains("mSavedBatteryAsoc:")) {
                            asocSeen = true
                            asocRaw  = line.substringAfter("mSavedBatteryAsoc:").trim()
                            asoc     = extractValueOrNull(line, "mSavedBatteryAsoc:")
                        }
                        if (line.contains("mSavedBatteryBsoh:")) {
                            bsohRaw = line.substringAfter("mSavedBatteryBsoh:").trim()
                            bsoh    = extractValueOrNull(line, "mSavedBatteryBsoh:")
                        }
                        if (line.contains("mSavedBatteryUsage:")) {
                            usageRaw = line.substringAfter("mSavedBatteryUsage:").trim()
                            val raw  = extractValueOrNull(line, "mSavedBatteryUsage:")
                            usage    = if (raw != null) raw / 100 else null
                        }
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
            isConciseOnly         = isConciseResult,
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
