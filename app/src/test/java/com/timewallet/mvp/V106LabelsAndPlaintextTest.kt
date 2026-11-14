package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.usecase.AiQueryValidator
import com.timewallet.mvp.domain.usecase.DashboardSummary
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.LedgerQueryEngine
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.ValidationResult
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** WALLY v1.0.6 — R04 (nhãn thống nhất, cảnh báo > 24h, không chặn cứng) và R03 mục (5) (văn bản thường hợp lệ không bị coi là hỏng). */
class V106LabelsAndPlaintextTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 13)
    private val nowMs = today.atTime(23, 0).atZone(zone).toInstant().toEpochMilli()

    private fun envelope(content: String) =
        JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", content)))).toString()

    private fun parse(content: String) =
        GeminiProposalClient.parseAndValidateResponseJson(envelope(content), "Cho mình lời khuyên", today, LocalTime.of(18, 0))

    // ---------------------------------------------------------------- R03 (5)
    @Test
    fun `plaintext that merely starts with a bracket tag is a normal reply not a malformed response`() {
        val out = parse("[Gợi ý] Hãy học đều mỗi ngày nhé")
        assertEquals("[Gợi ý] Hãy học đều mỗi ngày nhé", out.reply)
        assertTrue(out.proposals.isEmpty())
        assertNull(out.fault)
    }

    @Test
    fun `a long plaintext reply is kept but cut to the display limit instead of failing`() {
        val out = parse("A".repeat(801))
        assertNull(out.fault)
        assertEquals(400, out.reply!!.length)
    }

    // ---------------------------------------------------------------- R04
    private fun timed(cat: String, from: String, to: String, existing: List<LedgerRecord>): LedgerRecord {
        val range = LedgerValidator.parseTimeRangeToEpochMillis(today.toString(), from, to, zone)!!
        return (LedgerValidator.validateAndBuildRecord(
            source = "manual", rawTitle = "Có giờ", rawCategory = cat, dateLocal = today.toString(), inputDurationMinutes = null,
            startedAt = range.first, endedAt = range.second, existingRecords = existing, nowEpochMs = nowMs, zoneId = zone
        ) as ValidationResult.Valid).record
    }

    private fun untimedResult(minutes: Int, existing: List<LedgerRecord>) =
        LedgerValidator.validateAndBuildRecord(
            source = "manual", rawTitle = "Nhập tay", rawCategory = "work", dateLocal = today.toString(),
            inputDurationMinutes = minutes, startedAt = null, endedAt = null, existingRecords = existing, nowEpochMs = nowMs, zoneId = zone
        )

    @Test
    fun `untimed time that pushes the day over 24h is NOT hard blocked but the Dashboard flags it`() {
        val t = timed("work", "00:00", "18:00", emptyList())
        val u = untimedResult(8 * 60, listOf(t))
        assertTrue("không chặn cứng: $u", u is ValidationResult.Valid)
        val recs = listOf(t, (u as ValidationResult.Valid).record)
        val summary = DashboardSummary.from(LedgerAnalyticsCalculator.computeDayStats(today.toString(), recs, zone, nowMs))
        assertEquals(26 * 3600L, summary.recordedSeconds)
        assertTrue(summary.recordedExceedsDay)
        assertEquals(0L, summary.unrecordedSeconds)
    }

    @Test
    fun `the over-24h warning stays and explains the possible timed untimed overlap`() {
        val src = File("src/main/java/com/timewallet/mvp/ui/screens/OverviewScreen.kt").readText()
        assertTrue(src.contains("recordedExceedsDay"))
        assertTrue(src.contains("vượt quá độ dài của ngày") && src.contains("chưa gắn giờ có thể trùng"))
    }

    @Test
    fun `query answers use one label set - recorded equals timed plus untimed`() {
        val t = timed("work", "09:00", "10:00", emptyList())
        val u = (untimedResult(30, listOf(t)) as ValidationResult.Valid).record
        val intent = (AiQueryValidator.validate("TOTAL", today.toString(), today.toString(), null, null, null, today) as AiQueryValidator.Result.Ok).intent
        val a = LedgerQueryEngine.execute(intent, listOf(t, u), zone, nowMs)
        assertTrue(a.headline, a.headline.contains("1h có mốc giờ"))
        assertTrue(a.notes.toString(), a.notes.any { it.contains("Đã ghi nhận 1h 30p gồm 1h có mốc giờ và 30p chưa gắn giờ") })
    }

    @Test
    fun `no user-facing text calls the unrecorded part free time`() {
        val offenders = File("src/main").walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml") }
            .filter { f ->
                f.readLines().any { line ->
                    val l = line.lowercase()
                    // Chú thích mã nguồn nói "KHÔNG phải giờ rảnh" là hợp lệ; chỉ cấm văn bản hiển thị gọi phần chưa ghi là rảnh.
                    (l.contains("thời gian rảnh") || l.contains("giờ rảnh")) && !l.trim().startsWith("//") && !l.trim().startsWith("*") && !l.trim().startsWith("/*") &&
                        !l.contains("không phải")
                }
            }.map { it.name }.toList()
        assertTrue("không được gọi phần chưa ghi nhận là thời gian rảnh: $offenders", offenders.isEmpty())
    }
}
