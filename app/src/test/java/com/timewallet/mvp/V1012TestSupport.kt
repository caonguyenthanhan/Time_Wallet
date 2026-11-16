package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.ai.HttpOpener
import com.timewallet.mvp.data.diagnostics.AiDiagnostics
import com.timewallet.mvp.domain.usecase.ParseChatOutcome
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime

/**
 * v1.0.12: hạ tầng giả cho các test AI. KHÔNG có kết nối mạng thật; khóa và văn bản chỉ là dữ liệu tổng hợp.
 *
 * [AndroidLikeConnection] mô phỏng lớp mạng của Android theo số ĐO THẬT (v1.0.13: Android 9 và 16, HttpURLConnection): `setRequestProperty`
 * chỉ ném IllegalArgumentException NGAY khi đặt (trước mọi kết nối, vài mili giây, không có mã HTTP) với KÝ TỰ ĐIỀU KHIỂN: U+0000–0008, U+000A–001F
 * (gồm LF/CR) và U+007F. Tab (U+0009) và mọi ký tự ngoài ASCII đã đo (gạch dài, khoảng trắng không ngắt, rộng-bằng-không, chữ có dấu, CJK...) được NHẬN
 * và sẽ được gửi đi. Việc sản phẩm chặn khóa ngoài ASCII là quy tắc sản phẩm (ApiKeyHygiene.check), không phải hạn chế của nền tảng.
 */
internal class AndroidLikeConnection(
    url: URL,
    private val status: Int = 200,
    private val body: String = "",
    private val rejectHeader: ((String, String) -> Boolean)? = null
) : HttpURLConnection(url) {
    val headers = linkedMapOf<String, String>()
    val sent = ByteArrayOutputStream()

    override fun connect() {}
    override fun usingProxy() = false
    override fun disconnect() {}

    override fun setRequestProperty(key: String?, value: String?) {
        if (key != null && value != null) {
            for ((i, c) in value.withIndex()) {
                if (isRejectedByPlatform(c)) {
                    throw IllegalArgumentException("Unexpected char 0x%04x at %d in %s value".format(c.code, i, key))
                }
            }
            if (rejectHeader?.invoke(key, value) == true) throw IllegalArgumentException("header rejected: $key")
            headers[key] = value
        }
        super.setRequestProperty(key, value)
    }

    override fun getOutputStream(): OutputStream = sent
    override fun getResponseCode(): Int = status
    override fun getHeaderField(name: String?): String? = null
    override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray())
    override fun getErrorStream(): InputStream? = null

    companion object {
        /** Đúng tập đã đo: điều khiển trừ Tab, cộng DEL. Mọi ký tự khác (kể cả ngoài ASCII) được lớp mạng nhận. */
        fun isRejectedByPlatform(c: Char): Boolean = c.code in 0x00..0x08 || c.code in 0x0A..0x1F || c.code == 0x7F
    }
}

internal object V1012Rig {
    val zone: String = "Asia/Ho_Chi_Minh"

    /** Phản hồi kiểu OpenAI-compatible với `content` là chuỗi. */
    fun chatBody(content: String): String = JSONObject().put(
        "choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", content)))
    ).toString()

    fun proposalJson(
        title: String = "Code", category: String = "work", durationSeconds: Long = 9000, date: String,
        start: String? = "14:00", end: String? = "16:30", evidence: String = "explicit_range"
    ): String {
        fun q(s: String?) = if (s == null) "null" else "\"$s\""
        val missing = if (start != null && end != null) "[]" else "[\"startLocal\",\"endLocal\"]"
        return """{"title":"$title","category":"$category","durationSeconds":$durationSeconds,"dateLocal":"$date","startLocal":${q(start)},"endLocal":${q(end)},"timeEvidence":"$evidence","missingFields":$missing,"needsConfirmation":true}"""
    }

    fun answer(vararg proposals: String, reply: String = "Mình đã tách giúp bạn, bạn xác nhận nhé."): String =
        """{"reply":"$reply","proposals":[${proposals.joinToString(",")}],"queries":[]}"""

    class Run(val outcome: ParseChatOutcome, val connections: List<AndroidLikeConnection>)

    /** Chạy ĐÚNG `GeminiProposalClient.analyzeInput` với kết nối giả; trả kết quả và mọi kết nối đã mở. */
    fun analyze(
        text: String,
        key: String,
        today: LocalDate,
        now: LocalTime,
        status: Int = 200,
        body: String = "",
        rejectHeader: ((String, String) -> Boolean)? = null
    ): Run {
        val conns = mutableListOf<AndroidLikeConnection>()
        val opener: HttpOpener = { url -> AndroidLikeConnection(url, status, body, rejectHeader).also { conns += it } }
        val out = runBlocking {
            GeminiProposalClient.analyzeInput(
                userText = text, today = today, nowTime = now, timezoneId = zone, optInEnabled = true,
                apiKey = key, connectionFactory = opener
            )
        }
        return Run(out, conns)
    }

    fun resetDiagnostics() = AiDiagnostics.clear()
}
