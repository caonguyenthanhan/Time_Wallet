package com.timewallet.mvp

import com.timewallet.mvp.data.diagnostics.AiDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * v1.0.12 — bộ hồi quy HAI CÂY: chỉ dùng API đã có ở v1.0.11 (không `ApiKeyHygiene`, không `KEY_INVALID`, không `errorClass`) để cùng một file
 * chạy được trên cây v1.0.11 (kỳ vọng ĐỎ = tái hiện lỗi Owner) và cây v1.0.12 (kỳ vọng XANH = đã sửa). Xem out/wally_evidence_v1012/red_green/.
 */
class V1012OwnerRegressionTest {

    private val today = LocalDate.of(2026, 11, 15)
    private val now = LocalTime.of(16, 30)
    private val owner = "Code từ lúc 14h đến giờ"
    private val answer = V1012Rig.answer(V1012Rig.proposalJson(date = today.toString()))

    @Test
    fun `R1 the owner sentence works when the saved key carries pasted debris - it used to be MALFORMED with no HTTP status`() {
        val debris = listOf("sk-synthetic​-key-123", "sk-synthetic-\nkey-123", "sk-synthetic -key-123", "sk–synthetic-key-123", "“sk-synthetic-key-123”")
        for (key in debris) {
            val r = V1012Rig.analyze(owner, key, today, now, body = V1012Rig.chatBody(answer))
            assertNull("khóa ${key.length} ký tự phải dùng được", r.outcome.fault?.name)
            assertEquals(1, r.outcome.proposals.size)
            assertEquals("14:00", r.outcome.proposals.single().startLocal)
        }
    }

    @Test
    fun `R2 five sends with an unusable key are never reported as MALFORMED and are not counted as cloud requests`() {
        AiDiagnostics.clear()
        repeat(5) {
            val r = V1012Rig.analyze(owner, "sk-synthetic-khóa-123", today, now, body = V1012Rig.chatBody(answer))
            assertNotEquals("MALFORMED", r.outcome.fault?.name)
            assertNotNull(r.outcome.fault)
        }
        assertEquals("không có yêu cầu cloud nào được mở", 0, AiDiagnostics.summary().cloudRequests)
    }

    @Test
    fun `R3 until-now takes the current time even when the AI end time is off`() {
        val wrong = V1012Rig.answer(V1012Rig.proposalJson(date = today.toString(), end = "17:00", durationSeconds = 10800))
        val p = V1012Rig.analyze(owner, "sk-synthetic-key-123", today, now, body = V1012Rig.chatBody(wrong)).outcome.proposals.single()
        assertEquals("16:30", p.endLocal)
        assertEquals(9000L, p.durationSeconds)
    }
}
