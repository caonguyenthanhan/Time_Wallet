package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.diagnostics.AiDiagnostics
import com.timewallet.mvp.domain.usecase.AiFault
import com.timewallet.mvp.domain.usecase.ApiKeyHygiene
import com.timewallet.mvp.domain.usecase.OutcomeSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * v1.0.12 P1-AI — "Cloud MALFORMED 5/5, không có mã HTTP, 4–37 ms" (câu thật của Owner: "Code từ lúc 14h đến giờ").
 *
 * Gốc lỗi (bằng chứng): chữ ký 4–37 ms + không có mã HTTP chỉ khớp với một lỗi CỤC BỘ xảy ra trước khi có kết nối; trong client, con đường duy nhất như vậy
 * là lớp mạng của nền tảng từ chối header xác thực ⇒ IllegalArgumentException ⇒ `classify` cũ gán nhãn MALFORMED ("AI trả dữ liệu hỏng") và đếm là "yêu cầu cloud".
 * Số đo thật (v1.0.13): lớp mạng chỉ từ chối KÝ TỰ ĐIỀU KHIỂN (xuống dòng/CR dính theo khi dán khóa…); gạch dài, khoảng trắng không ngắt, rộng-bằng-không,
 * chữ có dấu… đều được nhận và sẽ được gửi đi. Việc sản phẩm báo KEY_INVALID cho khóa ngoài ASCII trước khi mở kết nối là QUY TẮC SẢN PHẨM (khóa nhà cung cấp là ASCII).
 * Mọi con đường MALFORMED khác (đọc phản hồi) chỉ xảy ra SAU một phản hồi HTTP thật nên không thể là vài mili giây.
 */
class V1012AiReliabilityTest {

    private val today = LocalDate.of(2026, 11, 15)
    private val now = LocalTime.of(16, 30)
    private val owner = "Code từ lúc 14h đến giờ"
    private val good = V1012Rig.answer(V1012Rig.proposalJson(date = today.toString()))
    private val okBody get() = V1012Rig.chatBody(good)

    private fun run(key: String, status: Int = 200, body: String = okBody) =
        V1012Rig.analyze(owner, key, today, now, status, body)

    // ------------------------------------------------------------------ gốc lỗi

    @Test
    fun `pasted key debris is repaired so the Owner sentence reaches the AI and becomes a proposal`() {
        val clean = "sk-synthetic-key-123"
        val debris = mapOf(
            "ký tự rộng-bằng-không" to "sk-synthetic​-key-123",
            "xuống dòng khi dán" to "sk-synthetic-\nkey-123",
            "khoảng trắng không ngắt" to "sk-synthetic -key-123",
            "dấu gạch dài do bàn phím" to "sk–synthetic-key-123",
            "ngoặc kép cong bao quanh" to "“sk-synthetic-key-123”",
            "tiền tố Bearer" to "Bearer sk-synthetic-key-123",
            "BOM + khoảng trắng hai đầu" to "﻿  sk-synthetic-key-123 \r\n"
        )
        for ((label, key) in debris) {
            V1012Rig.resetDiagnostics()
            val r = run(key)
            assertNull("$label: không được có lỗi", r.outcome.fault)
            assertEquals("$label: đúng một kết nối", 1, r.connections.size)
            assertEquals("$label: header gửi đi là khóa sạch", "Bearer $clean", r.connections.single().headers["Authorization"])
            assertEquals("$label: đề xuất", 1, r.outcome.proposals.size)
            val ev = AiDiagnostics.snapshot().single()
            assertEquals("$label: mã HTTP được ghi", 200, ev.httpStatus)
        }
    }

    @Test
    fun `a key with a non-ASCII character is rejected by the product rule as KEY_INVALID before any connection - never as MALFORMED`() {
        V1012Rig.resetDiagnostics()
        val bad = "sk-synthetic-khóa-123"
        repeat(5) { // đúng như Owner: 5 lần gửi liên tiếp
            val r = run(bad)
            assertEquals(AiFault.KEY_INVALID, r.outcome.fault)
            assertNotEquals(AiFault.MALFORMED, r.outcome.fault)
            assertEquals("không mở kết nối nào", 0, r.connections.size)
            assertTrue("nói rõ vị trí và bản chất", r.outcome.warnings.joinToString().contains("vị trí 16"))
            assertFalse("không lộ khóa", r.outcome.warnings.joinToString().contains("synthetic"))
            assertTrue(r.outcome.proposals.isEmpty())
        }
        val m = AiDiagnostics.summary()
        assertEquals("lỗi cục bộ không còn bị đếm là yêu cầu cloud", 0, m.cloudRequests)
        assertEquals(0, m.failuresByFault[AiFault.MALFORMED] ?: 0)
        assertEquals(OutcomeSource.NOT_CONFIGURED, AiDiagnostics.snapshot().first().source)
        assertEquals("KeyHeaderChars", AiDiagnostics.snapshot().first().errorClass)
    }

    @Test
    fun `if the platform still rejects the credential header the result is KEY_INVALID with the exception class`() {
        V1012Rig.resetDiagnostics()
        // khóa ASCII hợp lệ nhưng lớp mạng của một máy cụ thể vẫn từ chối header (quy tắc riêng của nền tảng)
        val r = V1012Rig.analyze(owner, "sk-synthetic-key-123", today, now, body = okBody, rejectHeader = { k, _ -> k == "Authorization" })
        assertEquals(AiFault.KEY_INVALID, r.outcome.fault)
        assertEquals("IllegalArgumentException", AiDiagnostics.snapshot().single().errorClass)
        assertNull("không có mã HTTP vì chưa có kết nối", AiDiagnostics.snapshot().single().httpStatus)
    }

    // ------------------------------------------------------------------ phân loại lỗi trung thực

    @Test
    fun `a body that is not JSON after an HTTP 200 is MALFORMED and the status and class are recorded`() {
        V1012Rig.resetDiagnostics()
        val r = run("sk-synthetic-key-123", body = "<html><body>captive portal</body></html>")
        assertEquals(AiFault.MALFORMED, r.outcome.fault)
        val ev = AiDiagnostics.snapshot().single()
        assertEquals(200, ev.httpStatus)
        assertNotNull(ev.errorClass)
        val text = AiDiagnostics.asText()
        assertTrue(text, text.contains("http=200") && text.contains("err="))
    }

    @Test
    fun `local exceptions are no longer classified as AI returning bad data`() {
        assertEquals(AiFault.UNKNOWN, GeminiProposalClient.classify(IllegalStateException("x")).fault)
        assertEquals(AiFault.UNKNOWN, GeminiProposalClient.classify(IllegalArgumentException("x")).fault)
        assertEquals(AiFault.UNKNOWN, GeminiProposalClient.classify(org.json.JSONException("x")).fault)
        assertEquals("IllegalStateException", GeminiProposalClient.classify(IllegalStateException("x")).errorClass)
        assertEquals(AiFault.OFFLINE, GeminiProposalClient.classify(java.net.UnknownHostException("x")).fault)
        assertEquals(AiFault.TIMEOUT, GeminiProposalClient.classify(java.net.SocketTimeoutException("x")).fault)
    }

    @Test
    fun `an error object inside an HTTP 200 body is classified by its nature`() {
        fun fault(err: String) = run("sk-synthetic-key-123", body = """{"error":$err}""").outcome.fault
        assertEquals(AiFault.AUTH, fault("""{"code":"invalid_api_key","message":"Incorrect API key provided"}"""))
        assertEquals(AiFault.RATE_LIMIT, fault("""{"type":"rate_limit_error","message":"slow down"}"""))
        assertEquals(AiFault.NOT_FOUND, fault("""{"message":"The model does not exist"}"""))
        assertEquals(AiFault.UNKNOWN, fault("""{"message":"something else"}"""))
    }

    @Test
    fun `provider replies that are valid but unusual still parse - and hostile shapes are still rejected`() {
        V1012Rig.resetDiagnostics()
        // phản hồi dạng "phẳng" và dạng choices đều hợp lệ; content là mảng vẫn bị từ chối (hợp đồng chặt v1.0.6 R03)
        assertNull(run("sk-synthetic-key-123").outcome.fault)
        assertEquals(AiFault.MALFORMED, run("sk-synthetic-key-123", body = """{"choices":[{"message":{"content":["a"]}}]}""").outcome.fault)
    }

    @Test
    fun `testConnection reports a bad key without opening a connection and passes a repaired key`() {
        var opened = 0
        val opener: com.timewallet.mvp.data.ai.HttpOpener = { url ->
            opened++
            AndroidLikeConnection(url, 200, V1012Rig.chatBody("pong")) // v1.0.13: `{"choices":[]}` không phải phản hồi chat hợp lệ nên không còn được tính là thành công
        }
        val bad = runBlocking { GeminiProposalClient.testConnection("https://api.foza.ai/v1", "sk-khóa", "m", connectionFactory = opener) }
        assertFalse(bad.ok); assertEquals(AiFault.KEY_INVALID, bad.fault); assertEquals(0, opened)
        val fixed = runBlocking { GeminiProposalClient.testConnection("https://api.foza.ai/v1", "sk–synthetic​-key", "m", connectionFactory = opener) }
        assertTrue(fixed.ok); assertEquals(1, opened)
    }

    // ------------------------------------------------------------------ ApiKeyHygiene (đơn vị)

    @Test
    fun `ApiKeyHygiene normalises idempotently and locates the first bad character`() {
        val keys = listOf("sk-abc_123.XYZ", "sk–abc", "  sk-abc \n", "“sk-abc”", "Bearer  sk-abc", "sk-ab​c")
        for (k in keys) {
            val n = ApiKeyHygiene.normalize(k)
            assertEquals(n, ApiKeyHygiene.normalize(n))
            assertNull("sau chuẩn hóa không còn vấn đề: $k", ApiKeyHygiene.check(n))
        }
        assertEquals("", ApiKeyHygiene.normalize("  ​\n "))
        assertEquals(4, ApiKeyHygiene.check("sk-é")?.position)
        assertEquals(1, ApiKeyHygiene.check("\u0001abc")?.position)
        assertEquals("bên trong khóa thật không bị đổi", "sk-ABC_def.123~", ApiKeyHygiene.normalize("sk-ABC_def.123~"))
    }

    @Test
    fun `the saved key and the VM save path normalise too - static wiring`() {
        val store = java.io.File("src/main/java/com/timewallet/mvp/data/local/TimerAndSettingsStore.kt").readText()
        val vm = java.io.File("src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt").readText()
        assertTrue(store.contains("ApiKeyHygiene.normalize(decrypted"))
        assertTrue(store.contains("ApiKeyHygiene.normalize(aiPreferences.apiKey)"))
        assertTrue(vm.contains("ApiKeyHygiene.check(cleanKey)") && vm.contains("apiKey = cleanKey"))
        val screen = java.io.File("src/main/java/com/timewallet/mvp/ui/screens/ChatScreen.kt").readText()
        assertTrue("KEY_INVALID cũng hiện nút Mở cấu hình", screen.contains("AiFault.KEY_INVALID"))
        // hộp thoại cấu hình báo lỗi khóa NGAY TRONG hộp thoại (toast nằm sau hộp thoại) và chặn lưu/kiểm tra khóa hỏng
        val dialogs = java.io.File("src/main/java/com/timewallet/mvp/ui/screens/ComponentsAndDialogs.kt").readText()
        val dlg = dialogs.substring(dialogs.indexOf("fun AiSettingsDialog("), dialogs.indexOf("fun ProductInfoDialog("))
        assertTrue(dlg.contains("ApiKeyHygiene.check(ApiKeyHygiene.normalize(apiKey))") && dlg.contains("Khóa API có ký tự không hợp lệ ở vị trí"))
        assertTrue(dlg.contains("keyProblem == null,") && dlg.contains("&& keyProblem == null"))
    }
}
