package com.timewallet.mvp

import com.timewallet.mvp.domain.usecase.AiFault
import com.timewallet.mvp.domain.usecase.ApiKeyHygiene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime

/**
 * v1.0.13 U3 — sự thật về ApiKeyHygiene. ĐO THẬT (Android 9 và 16, HttpURLConnection): `setRequestProperty` chỉ ném IllegalArgumentException với KÝ TỰ ĐIỀU KHIỂN
 * (U+0000..0008, U+000A..001F gồm LF/CR, U+007F); Tab và mọi ký tự ngoài ASCII đã đo được lớp mạng NHẬN. Việc sản phẩm báo khóa ngoài ASCII là KEY_INVALID TRƯỚC khi
 * mở kết nối là QUY TẮC SẢN PHẨM (khóa nhà cung cấp là ASCII), không phải hạn chế của nền tảng. Các test này khóa cả hành vi sản phẩm lẫn lời văn trong mã.
 * Ký tự đặc biệt trong test được viết bằng \uXXXX để không phụ thuộc vào ký tự vô hình trong tệp nguồn.
 */
class V1013KeyHygieneTruthTest {

    private val today = LocalDate.of(2026, 11, 16)
    private val now = LocalTime.of(16, 30)
    private val url = URL("https://api.example.invalid/v1/chat/completions")

    private fun accepts(c: Char): Boolean = try {
        AndroidLikeConnection(url).setRequestProperty("Authorization", "Bearer sk-a${c}b")
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    // ------------------------------------------------------------------ bản giả đúng với số đo

    @Test
    fun `the Android-like double rejects only the measured control characters`() {
        val rejected = (0x00..0x08) + (0x0A..0x1F) + 0x7F
        for (code in rejected) assertFalse("U+%04X phải bị từ chối".format(code), accepts(code.toChar()))
        assertTrue("Tab được nhận", accepts('\t'))
    }

    @Test
    fun `the Android-like double accepts every measured non-ASCII character and the printable range`() {
        val measured = listOf(
            '–', '—', '‐', '−',   // gạch Unicode
            ' ', '​', '﻿', '­',   // NBSP, rộng-bằng-không, BOM, gạch mềm
            'é', 'ạ', 'ế', 'đ',   // chữ có dấu / tiếng Việt
            '中', 'あ', '“', '”'    // CJK, ngoặc kép cong
        )
        for (c in measured) assertTrue("U+%04X được lớp mạng nhận".format(c.code), accepts(c))
        for (code in 0x20..0x7E) assertTrue("ASCII in được U+%04X được nhận".format(code), accepts(code.toChar()))
    }

    @Test
    fun `an accepted non-ASCII header value is really recorded by the double and would be sent`() {
        val c = AndroidLikeConnection(url)
        c.setRequestProperty("Authorization", "Bearer sk–khóa")
        assertEquals("Bearer sk–khóa", c.headers["Authorization"])
    }

    // ------------------------------------------------------------------ hành vi sản phẩm được giữ nguyên (quy tắc sản phẩm, không phải nền tảng)

    @Test
    fun `a non-ASCII key is still rejected before any connection even though the platform would have carried it`() {
        for (key in listOf("sk-synthetic-khóa-123", "sk-中文-123", "sk-synthetic-é")) {
            assertTrue("nền tảng sẽ nhận khóa này: $key", accepts(key.first { it.code > 0x7E }))
            val r = V1012Rig.analyze("Học 30p", key, today, now, body = V1012Rig.chatBody("{}"))
            assertEquals(AiFault.KEY_INVALID, r.outcome.fault)
            assertEquals("không mở kết nối nào: đây là quy tắc sản phẩm", 0, r.connections.size)
            assertNotNull(ApiKeyHygiene.check(ApiKeyHygiene.normalize(key)))
        }
    }

    @Test
    fun `a control character that survives normalisation is rejected before any connection`() {
        val key = "sk-synthetic\u0001-key"
        assertEquals(13, ApiKeyHygiene.check(ApiKeyHygiene.normalize(key))?.position)
        val r = V1012Rig.analyze("Học 30p", key, today, now, body = V1012Rig.chatBody("{}"))
        assertEquals(AiFault.KEY_INVALID, r.outcome.fault)
        assertEquals(0, r.connections.size)
    }

    @Test
    fun `normalize still strips paste debris and maps Unicode dashes so a pasted key reaches the provider clean`() {
        assertEquals("sk-synthetic-key", ApiKeyHygiene.normalize("  “sk–synthetic​-key”\r\n"))
        assertEquals("sk-synthetic-key", ApiKeyHygiene.normalize("Bearer sk—synthetic‑key"))
        assertNull(ApiKeyHygiene.check("sk-synthetic-key"))
    }

    // ------------------------------------------------------------------ lời văn trong mã không còn khẳng định điều sai

    private fun main(path: String) = File("src/main/java/com/timewallet/mvp/$path").readText(Charsets.UTF_8)

    @Test
    fun `the KDoc of ApiKeyHygiene states the measured platform behaviour and the product rule`() {
        val doc = main("domain/usecase/ApiKeyHygiene.kt")
        assertTrue(doc.contains("SỰ THẬT ĐÃ ĐO"))
        assertTrue(doc.contains("chỉ ném IllegalArgumentException với KÝ TỰ ĐIỀU KHIỂN"))
        assertTrue(doc.contains("U+0000–0008, U+000A–001F") && doc.contains("U+007F"))
        assertTrue(doc.contains("Tab (U+0009)") && doc.contains("được lớp mạng NHẬN"))
        assertTrue(doc.contains("QUY TẮC SẢN PHẨM (không phải hạn chế của nền tảng)"))
        assertFalse("khẳng định sai cũ", doc.contains("ném IllegalArgumentException ngay khi đặt"))
        assertFalse("khẳng định sai cũ", doc.contains("mà header không chấp nhận"))
    }

    @Test
    fun `no source comment or user-visible string claims that the platform rejects non-ASCII header values`() {
        val wrong = listOf(
            Regex("""(nền tảng|lớp mạng)[^\n]{0,60}(từ chối|không nhận|ném)[^\n]{0,60}(header|ký tự ngoài ASCII)""", RegexOption.IGNORE_CASE),
            Regex("""header[^\n]{0,40}(không nhận|không chấp nhận)""", RegexOption.IGNORE_CASE),
            Regex("""ký tự (mà )?header không""", RegexOption.IGNORE_CASE)
        )
        val offenders = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
            f.readLines(Charsets.UTF_8).withIndex().filter { (_, line) ->
                // ngoại lệ duy nhất: câu nói ĐÚNG sự thật đã đo (chỉ ký tự điều khiển; "KHÔNG từ chối ký tự ngoài ASCII") trong chú thích của chính ApiKeyHygiene / nơi ném
                wrong.any { it.containsMatchIn(line) } && !line.contains("điều khiển") && !line.contains("KHÔNG từ chối")
            }.map { "${f.name}:${it.index + 1}: ${it.value.trim()}" }
        }.toList()
        assertTrue("còn khẳng định sai về nền tảng: $offenders", offenders.isEmpty())
    }

    @Test
    fun `the test double and the old wrong claim in the tests are corrected`() {
        val support = File("src/test/java/com/timewallet/mvp/V1012TestSupport.kt").readText(Charsets.UTF_8)
        assertTrue(support.contains("KÝ TỰ ĐIỀU KHIỂN") && support.contains("Tab (U+0009)"))
        assertFalse("không còn quy tắc c >= 0x7F", support.contains("c >= '\\u007f'"))
        assertFalse(support.contains("hoặc ngoài ASCII in được bị từ chối"))
    }

    @Test
    fun `the user-visible key messages do not blame the platform or network stack`() {
        val shown = listOf(
            AiFault.KEY_INVALID.messageVi,
            ApiKeyHygiene.check("é")!!.kindVi, ApiKeyHygiene.check("\u0001")!!.kindVi, ApiKeyHygiene.check(" ")!!.kindVi
        )
        val blame = Regex("""nền tảng|lớp mạng|hệ điều hành|Android|HttpURLConnection""", RegexOption.IGNORE_CASE)
        for (s in shown) assertFalse("không đổ lỗi cho nền tảng: $s", blame.containsMatchIn(s))
        assertTrue(AiFault.KEY_INVALID.messageVi.contains("ký tự không hợp lệ"))
    }
}
