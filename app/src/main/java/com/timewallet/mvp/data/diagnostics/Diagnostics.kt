package com.timewallet.mvp.data.diagnostics

import java.io.File
import java.time.Instant

/**
 * WALLY F6: báo cáo lỗi đã che nội dung riêng tư. Chỉ ghi tên lớp ngoại lệ và các khung stack,
 * KHÔNG ghi `message` (có thể chứa văn bản nhật ký hoặc khóa API).
 */
object CrashReport {
    private const val MAX_FRAMES = 30
    private const val MAX_CAUSES = 5

    fun redact(t: Throwable, now: Instant = Instant.now()): String {
        val sb = StringBuilder("time=$now\n")
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

/** Ghi báo cáo lỗi gần nhất vào một tệp cục bộ để Owner đọc được stack trace sau khi app dừng. */
object CrashRecorder {
    @Volatile
    private var file: File? = null

    fun install(target: File) {
        file = target
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(error: Throwable) {
        try {
            file?.writeText(CrashReport.redact(error))
        } catch (_: Throwable) {
            // Không để việc ghi log làm hỏng luồng xử lý lỗi gốc.
        }
    }

    fun readLast(): String? = try {
        file?.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        null
    }

    fun clear() {
        try {
            file?.delete()
        } catch (_: Throwable) {
        }
    }
}
