package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.local.AiPreferences
import com.timewallet.mvp.domain.usecase.ApiKeyHygiene
import com.timewallet.mvp.domain.usecase.OutcomeSource
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * v1.2.0 Live AI QA (P1): chỉ chạy khi đặt COACH_EVAL_KEY_FILE và WALLY_CHAT_EVAL_OUT; bỏ qua an toàn khi test offline.
 * Kiểm chứng:
 * 1) Tuyến mặc định `cinb/claude-sonnet-4-6` kết nối thành công (HTTP 200);
 * 2) Tuyến `cinb/gemini-3.8-flash` (nếu gặp lỗi 503/403 từ upstream) báo lỗi rõ ràng, không văng ngoại lệ;
 * 3) UC-15 câu tự nhiên xuyên ngày, từ chối mơ hồ AM/PM, và đa đề xuất trong 1 câu Chat.
 */
class V120LiveAiEvalTest {

    @Test
    fun liveChatAndRouteEval() {
        val keyFile = System.getenv("COACH_EVAL_KEY_FILE")
        val outFile = System.getenv("WALLY_CHAT_EVAL_OUT")
        assumeTrue(
            "COACH_EVAL_KEY_FILE/WALLY_CHAT_EVAL_OUT chưa đặt: bỏ qua (không gọi mạng)",
            !keyFile.isNullOrBlank() && !outFile.isNullOrBlank()
        )
        val key = ApiKeyHygiene.normalize(File(keyFile!!).readText(Charsets.UTF_8))
        check(ApiKeyHygiene.check(key) == null && key.isNotBlank()) { "credential unusable" }

        val out = File(outFile!!).also {
            it.parentFile?.mkdirs()
            it.writeText("", Charsets.UTF_8)
        }

        // 1. Default route check (cinb/claude-sonnet-4-6)
        val defaultConn = runBlocking {
            GeminiProposalClient.testConnection(
                baseUrl = AiPreferences.DEFAULT_AI_BASE_URL,
                apiKey = key,
                modelName = AiPreferences.DEFAULT_AI_MODEL
            )
        }
        out.appendText(
            JSONObject()
                .put("case", "testConnection_default")
                .put("model", AiPreferences.DEFAULT_AI_MODEL)
                .put("ok", defaultConn.ok)
                .put("httpStatus", defaultConn.httpStatus ?: JSONObject.NULL)
                .put("fault", defaultConn.fault?.name ?: JSONObject.NULL)
                .put("latencyMs", defaultConn.latencyMs)
                .put("detailVi", defaultConn.detailVi)
                .toString() + "\n",
            Charsets.UTF_8
        )
        assertTrue("Default model ${AiPreferences.DEFAULT_AI_MODEL} phải kết nối thành công: ${defaultConn.detailVi}", defaultConn.ok)
        assertEquals(200, defaultConn.httpStatus)

        // 2. Unstable legacy route negative/diagnostic probe (cinb/gemini-3.8-flash)
        val legacyConn = runBlocking {
            GeminiProposalClient.testConnection(
                baseUrl = AiPreferences.DEFAULT_AI_BASE_URL,
                apiKey = key,
                modelName = "cinb/gemini-3.8-flash"
            )
        }
        out.appendText(
            JSONObject()
                .put("case", "testConnection_legacy_route")
                .put("model", "cinb/gemini-3.8-flash")
                .put("ok", legacyConn.ok)
                .put("httpStatus", legacyConn.httpStatus ?: JSONObject.NULL)
                .put("fault", legacyConn.fault?.name ?: JSONObject.NULL)
                .put("latencyMs", legacyConn.latencyMs)
                .put("detailVi", legacyConn.detailVi)
                .toString() + "\n",
            Charsets.UTF_8
        )
        if (!legacyConn.ok) {
            assertTrue("Phải phân loại fault rõ ràng và không trống detailVi", legacyConn.detailVi.isNotBlank())
        }

        val today = LocalDate.of(2026, 11, 16)
        val nowTime = LocalTime.of(8, 30)

        // 3. UC-15 clear cross-midnight statement
        val crossMidnight = runBlocking {
            GeminiProposalClient.analyzeInput(
                userText = "Tôi ngủ từ 20h tối hôm qua đến 7h sáng hôm nay",
                today = today,
                nowTime = nowTime,
                timezoneId = "Asia/Ho_Chi_Minh",
                optInEnabled = true,
                apiKey = key,
                modelName = AiPreferences.DEFAULT_AI_MODEL
            )
        }
        out.appendText(
            JSONObject()
                .put("case", "uc15_cross_midnight")
                .put("source", crossMidnight.source.name)
                .put("fault", crossMidnight.fault?.name ?: JSONObject.NULL)
                .put("proposals", crossMidnight.proposals.size)
                .put("totalSeconds", crossMidnight.proposals.sumOf { it.durationSeconds })
                .put("dates", crossMidnight.proposals.joinToString(",") { "${it.dateLocal}:${it.startLocal}-${it.endLocal}" })
                .put("reply", crossMidnight.reply ?: JSONObject.NULL)
                .put("warnings", crossMidnight.warnings.joinToString(" | "))
                .toString() + "\n",
            Charsets.UTF_8
        )
        assertEquals(OutcomeSource.CLOUD, crossMidnight.source)
        assertNull("UC-15 cross-midnight không được lỗi: ${crossMidnight.fault}", crossMidnight.fault)
        assertTrue("UC-15 phải sinh đề xuất hợp lệ (>=1 phân đoạn): ${crossMidnight.warnings}", crossMidnight.proposals.isNotEmpty())
        assertEquals("Tổng thời lượng ngủ 20:00->07:00 phải là 11h (39600s)", 39_600L, crossMidnight.proposals.sumOf { it.durationSeconds })

        // 4. UC-15 ambiguous AM/PM statement ("8h ngày hôm qua" thiếu sáng/tối) -> refuse to guess
        val ambiguous = runBlocking {
            GeminiProposalClient.analyzeInput(
                userText = "Tôi ngủ từ 8h ngày hôm qua đến 7h sáng hôm nay",
                today = today,
                nowTime = nowTime,
                timezoneId = "Asia/Ho_Chi_Minh",
                optInEnabled = true,
                apiKey = key,
                modelName = AiPreferences.DEFAULT_AI_MODEL
            )
        }
        out.appendText(
            JSONObject()
                .put("case", "uc15_ambiguous_refusal")
                .put("source", ambiguous.source.name)
                .put("fault", ambiguous.fault?.name ?: JSONObject.NULL)
                .put("proposals", ambiguous.proposals.size)
                .put("reply", ambiguous.reply ?: JSONObject.NULL)
                .put("warnings", ambiguous.warnings.joinToString(" | "))
                .toString() + "\n",
            Charsets.UTF_8
        )
        assertEquals(OutcomeSource.CLOUD, ambiguous.source)
        assertNull(ambiguous.fault)
        assertTrue("Mơ hồ sáng/tối không được tự suy đoán đề xuất: ${ambiguous.proposals}", ambiguous.proposals.isEmpty())

        // 5. Multi-proposal Chat extraction
        val multi = runBlocking {
            GeminiProposalClient.analyzeInput(
                userText = "Sáng 8h đến 10h làm việc, xong 10h đến 11h30 đọc sách",
                today = today,
                nowTime = LocalTime.of(12, 0),
                timezoneId = "Asia/Ho_Chi_Minh",
                optInEnabled = true,
                apiKey = key,
                modelName = AiPreferences.DEFAULT_AI_MODEL
            )
        }
        out.appendText(
            JSONObject()
                .put("case", "multi_proposal_chat")
                .put("source", multi.source.name)
                .put("fault", multi.fault?.name ?: JSONObject.NULL)
                .put("proposals", multi.proposals.size)
                .put("dates", multi.proposals.joinToString(",") { "${it.dateLocal}:${it.startLocal}-${it.endLocal}(${it.title})" })
                .put("reply", multi.reply ?: JSONObject.NULL)
                .toString() + "\n",
            Charsets.UTF_8
        )
        assertEquals(OutcomeSource.CLOUD, multi.source)
        assertNull(multi.fault)
        assertEquals("Phải tách đúng 2 đề xuất", 2, multi.proposals.size)
    }
}
