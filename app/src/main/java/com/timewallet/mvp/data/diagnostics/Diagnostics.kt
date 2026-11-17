package com.timewallet.mvp.data.diagnostics

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Thông tin trích từ một báo cáo lỗi đã lưu (không làm mất/sửa nội dung thô bên dưới).
 * Các bản ghi từ phiên bản cũ chưa ghi trường `version=` phải trả `version = null` -> hiển thị "Không xác định".
 */
data class CrashReportInfo(
    val timeIso: String?,
    val version: String?,
    val exception: String?
) {
    fun header(zone: ZoneId = ZoneId.systemDefault()): String {
        val timeDisplay = formatTime(timeIso, zone)
        val versionDisplay = version?.takeIf { it.isNotBlank() } ?: UNKNOWN_VERSION_LABEL
        return "Thời điểm: $timeDisplay · Phiên bản khi lỗi: $versionDisplay"
    }

    companion object {
        const val UNKNOWN_VERSION_LABEL = "Không xác định"

        private val DISPLAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

        fun parse(raw: String): CrashReportInfo {
            var timeIso: String? = null
            var version: String? = null
            var exception: String? = null
            raw.lineSequence().forEach { line ->
                val trimmed = line.trim()
                when {
                    timeIso == null && trimmed.startsWith("time=") ->
                        timeIso = trimmed.removePrefix("time=").trim().takeIf { it.isNotEmpty() }
                    version == null && trimmed.startsWith("version=") ->
                        version = trimmed.removePrefix("version=").trim().takeIf { it.isNotEmpty() }
                    exception == null && trimmed.startsWith("exception=") ->
                        exception = trimmed.removePrefix("exception=").trim().takeIf { it.isNotEmpty() }
                }
            }
            return CrashReportInfo(timeIso = timeIso, version = version, exception = exception)
        }

        private fun formatTime(rawIso: String?, zone: ZoneId): String {
            if (rawIso.isNullOrBlank()) return UNKNOWN_VERSION_LABEL
            return try {
                val instant = Instant.parse(rawIso)
                "${DISPLAY_FMT.format(instant.atZone(zone))} ($rawIso)"
            } catch (_: Throwable) {
                rawIso
            }
        }
    }
}

/**
 * WALLY F6: báo cáo lỗi đã che nội dung riêng tư. Chỉ ghi tên lớp ngoại lệ và các khung stack,
 * KHÔNG ghi `message` (có thể chứa văn bản nhật ký hoặc thông tin cấu hình nhạy cảm).
 */
object CrashReport {
    private const val MAX_FRAMES = 30
    private const val MAX_CAUSES = 5

    fun redact(t: Throwable, now: Instant = Instant.now(), appVersion: String? = null): String {
        val sb = StringBuilder("time=$now\n")
        val cleanVersion = appVersion?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanVersion != null) {
            sb.append("version=").append(cleanVersion).append('\n')
        }
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth < MAX_CAUSES) {
            sb.append(if (depth == 0) "exception=" else "caused_by=").append(cur.javaClass.name).append('\n')
            cur.stackTrace.take(MAX_FRAMES).forEach { sb.append("  at ").append(it).append('\n') }
            cur = cur.cause?.takeIf { it !== cur }
            depth++
        }
        return sb.toString()
    }
}

/** Ghi báo cáo lỗi gần nhất vào tệp cục bộ để Owner đọc được stack trace sau khi app dừng, đồng thời giữ lại 1 bản ghi trước đó. */
object CrashRecorder {
    @Volatile
    private var file: File? = null

    @Volatile
    private var appVersion: String? = null

    fun install(target: File, appVersion: String? = null) {
        file = target
        this.appVersion = appVersion?.trim()?.takeIf { it.isNotEmpty() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(error: Throwable) {
        try {
            val target = file ?: return
            val existing = target.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
            if (existing != null) {
                previousFileFor(target).writeText(existing)
            }
            target.writeText(CrashReport.redact(error, appVersion = appVersion))
        } catch (_: Throwable) {
            // Không để việc ghi log làm hỏng luồng xử lý lỗi gốc.
        }
    }

    fun readLast(): String? = try {
        file?.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        null
    }

    fun readPrevious(): String? = try {
        file?.let(::previousFileFor)?.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        null
    }

    fun clear() {
        try {
            val target = file
            target?.delete()
            target?.let(::previousFileFor)?.delete()
        } catch (_: Throwable) {
        }
    }

    private fun previousFileFor(target: File): File =
        File(target.parentFile, "${target.name}.prev")
}
