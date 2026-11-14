package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.local.LedgerRecordEntity
import com.timewallet.mvp.domain.model.ActivityProposal
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.AiProposalGuard
import com.timewallet.mvp.domain.usecase.AiQueryValidator
import com.timewallet.mvp.domain.usecase.BatchValidationResult
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.LedgerQueryEngine
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.QueryMetric
import com.timewallet.mvp.domain.usecase.QueryAnswer
import com.timewallet.mvp.domain.usecase.ReplyGuard
import com.timewallet.mvp.domain.usecase.WallyAnswerBuilder
import com.timewallet.mvp.domain.usecase.BatchRecordInput
import com.timewallet.mvp.ui.EditableProposalDraft
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * WALLY API-FIRST: guard đầu ra AI, kiểm tra truy vấn, thẻ trả lời, và luồng tích hợp
 * AI -> proposal -> xác nhận -> commit (không side effect trước xác nhận, không ghi trùng).
 */
class WallyApiFirstGuardsTest {

    private val today = LocalDate.of(2026, 11, 13)
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val nowMs = today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

    private fun prop(title: String, cat: String, minutes: Int, id: String = "p-$title-$minutes") = ActivityProposal(
        id = id, rawInput = "x", title = title, category = cat, durationSeconds = minutes * 60L, dateLocal = today.toString(),
        startLocal = null, endLocal = null, timeEvidence = "duration_only", missingFields = listOf("startLocal", "endLocal"),
        needsConfirmation = true, isFuturePlanned = false
    )

    // ---------------- AiQueryValidator ----------------
    @Test
    fun `query validator accepts a good query and labels today and yesterday`() {
        val r = AiQueryValidator.validate("total", "2026-11-13", "2026-11-13", null, null, null, today) as AiQueryValidator.Result.Ok
        assertEquals(QueryMetric.TOTAL, r.intent.metric)
        assertEquals("hôm nay", r.intent.period.label)
        val y = AiQueryValidator.validate("UNTIMED", "2026-11-12", "2026-11-12", "learn", null, null, today) as AiQueryValidator.Result.Ok
        assertEquals("hôm qua", y.intent.period.label)
        assertEquals(TimeCategory.LEARN, y.intent.category)
        val range = AiQueryValidator.validate("BREAKDOWN", "2026-11-09", "2026-11-15", "null", "2026-11-02", "2026-11-08", today) as AiQueryValidator.Result.Ok
        assertEquals(7L, range.intent.period.dayCount)
        assertEquals(LocalDate.of(2026, 11, 2), range.intent.compareTo!!.start)
    }

    @Test
    fun `query validator rejects bad metrics dates spans and categories`() {
        fun bad(m: String?, s: String?, e: String?, c: String? = null, cs: String? = null, ce: String? = null) =
            AiQueryValidator.validate(m, s, e, c, cs, ce, today) is AiQueryValidator.Result.Rejected
        assertTrue(bad("NOPE", "2026-11-13", "2026-11-13"))
        assertTrue(bad(null, "2026-11-13", "2026-11-13"))
        assertTrue(bad("TOTAL", "13/11/2026", "2026-11-13"))
        assertTrue(bad("TOTAL", "2026-11-13", null))
        assertTrue(bad("TOTAL", "2026-11-14", "2026-11-13"))
        assertTrue(bad("TOTAL", "2026-01-01", "2026-11-13"))                  // > 93 ngày
        assertTrue(bad("TOTAL", "2026-11-13", "2026-11-13", c = "games"))
        assertTrue(bad("TOTAL", "2026-11-13", "2026-11-13", cs = "2026-11-01"))  // thiếu một đầu
        assertTrue(bad("TOTAL", "2026-11-13", "2026-11-13", cs = "2026-12-01", ce = "2026-11-01"))
        assertFalse(bad("TOTAL", "2026-08-13", "2026-11-13"))                   // đúng 93 ngày
    }

    @Test
    fun `numbers for an AI query come from the ledger engine and reconcile with computeDayStats`() {
        val recs = (LedgerValidator.validateAndBuildRecord(
            source = "manual", rawTitle = "Học", rawCategory = "learn", dateLocal = today.toString(), inputDurationMinutes = 90,
            startedAt = null, endedAt = null, existingRecords = emptyList(), nowEpochMs = nowMs, zoneId = zone
        ) as com.timewallet.mvp.domain.usecase.ValidationResult.Valid).record.let { listOf(it) }
        val intent = (AiQueryValidator.validate("UNTIMED", today.toString(), today.toString(), null, null, null, today) as AiQueryValidator.Result.Ok).intent
        val answer = LedgerQueryEngine.execute(intent, recs, zone, nowMs)
        assertEquals(5400L, answer.figures.untimedSeconds)
        assertEquals(LedgerAnalyticsCalculator.computeDayStats(today.toString(), recs, zone, nowMs).untimedSeconds, answer.figures.untimedSeconds)
        // Không có dữ liệu: nói rõ, không bịa số
        val empty = LedgerQueryEngine.execute(intent, emptyList(), zone, nowMs)
        assertFalse(empty.figures.hasAnyData)
    }

    // ---------------- AiProposalGuard ----------------
    @Test
    fun `guard blocks every proposal for cancel commands and corrections`() {
        listOf("Hủy ghi nhận 30 phút đọc sách", "Hủy tất cả đề xuất", "Không phải 2 tiếng, học 3 tiếng", "Học 2h, à không, 3h").forEach {
            val r = AiProposalGuard.apply(it, listOf(prop("Học", "learn", 180), prop("Đọc sách", "learn", 30)))
            assertTrue(it, r.proposals.isEmpty())
            assertTrue(r.warnings.isNotEmpty())
        }
    }

    @Test
    fun `guard handles negation per clause`() {
        val all = AiProposalGuard.apply("Hôm nay tôi không học 2 tiếng", listOf(prop("Học", "learn", 120)))
        assertTrue(all.proposals.isEmpty())
        val mixed = AiProposalGuard.apply("Không học 2h nhưng code 1h", listOf(prop("Học", "learn", 120), prop("Code", "work", 60)))
        assertEquals(listOf("work"), mixed.proposals.map { it.category })
        val untouched = AiProposalGuard.apply("Học 2h không nghỉ", listOf(prop("Học", "learn", 120)))
        assertEquals(1, untouched.proposals.size)
        val nonStop = AiProposalGuard.apply("Làm việc không nghỉ 3 tiếng", listOf(prop("Làm việc", "work", 180)))
        assertEquals("câu liên tục không bị coi là phủ định", 1, nonStop.proposals.size)
        val notRest = AiProposalGuard.apply("Hôm nay tôi không nghỉ 2h", listOf(prop("Nghỉ", "rest", 120)))
        assertTrue(notRest.proposals.isEmpty())
    }

    @Test
    fun `guard rejects invented durations but accepts word numbers and sums`() {
        assertTrue(AiProposalGuard.apply("Học 45p", listOf(prop("Học", "learn", 450))).proposals.isEmpty())
        assertEquals(1, AiProposalGuard.apply("Học 45p", listOf(prop("Học", "learn", 45))).proposals.size)
        assertEquals("khoảng giờ tường minh", 1, AiProposalGuard.apply("Từ 13:00 đến 18:00 làm việc tập trung", listOf(prop("Làm việc", "work", 300))).proposals.size)
        assertEquals("tổng hợp hợp lệ", 1, AiProposalGuard.apply("Học 30p rồi học thêm 15p", listOf(prop("Học", "learn", 45))).proposals.size)
        assertEquals("số bằng chữ không đối chiếu được", 1, AiProposalGuard.apply("Học hai tiếng", listOf(prop("Học", "learn", 120))).proposals.size)
        assertEquals("không có thời lượng nêu rõ", 1, AiProposalGuard.apply("Chiều nay tôi học bài", listOf(prop("Học", "learn", 60))).proposals.size)
    }

    // ---------------- ReplyGuard / AnswerBuilder ----------------
    @Test
    fun `reply guard keeps figures backed by the user or proposals and drops the rest`() {
        val p = listOf(prop("Học", "learn", 45))
        // v1.0.6 (F-V105-08): lời đáp của AI không được khẳng định đã ghi; chỉ nhắc lại thời lượng của đề xuất đang chờ xác nhận.
        assertEquals("Mình thấy 45 phút học, bạn xác nhận nhé.", ReplyGuard.sanitize("Mình thấy 45 phút học, bạn xác nhận nhé.", "Học 45p", p, false).first)
        assertNull(ReplyGuard.sanitize("Đã ghi 45 phút học.", "Học 45p", p, false).first)
        assertEquals("Học 0,75h nhé", ReplyGuard.sanitize("Học 0,75h nhé", "Học 45p", p, false).first)
        assertNull(ReplyGuard.sanitize("Bạn đã học 9 giờ hôm nay", "Học 45p", p, false).first)
        assertNull(ReplyGuard.sanitize("45 phút, tương đương 12%", "Học 45p", p, true).first)
        assertNull(ReplyGuard.sanitize("   ", "x", emptyList(), false).first)
        assertEquals(400, ReplyGuard.sanitize("a".repeat(900), "x", emptyList(), false).first!!.length)
    }

    @Test
    fun `answer builder composes reply and engine lines and never invents a card for proposal-only results`() {
        val empty = LedgerQueryEngine.execute(
            (AiQueryValidator.validate("TOTAL", today.toString(), today.toString(), null, null, null, today) as AiQueryValidator.Result.Ok).intent,
            emptyList(), zone, nowMs
        )
        val card = WallyAnswerBuilder.build("Hỏi", "Mình lấy số liệu giúp bạn.", listOf(empty), emptyList(), 0)!!
        assertEquals("Mình lấy số liệu giúp bạn.", card.headline)
        assertEquals(WallyAnswerBuilder.LABEL_AI, card.label)
        assertTrue(card.lines.isNotEmpty())
        assertNull("chỉ có đề xuất thì không dựng thẻ", WallyAnswerBuilder.build("x", null, emptyList(), emptyList(), 2))
        val clarify = WallyAnswerBuilder.build("x", null, emptyList(), emptyList(), 0)!!
        assertTrue(clarify.isClarification)
        val onlyEngine = WallyAnswerBuilder.build("x", null, listOf(empty), emptyList(), 0)!!
        assertEquals(empty.headline, onlyEngine.headline)
    }

    // ---------------- tích hợp: AI -> proposal -> xác nhận -> commit ----------------
    private fun aiBody(vararg proposals: String): String {
        val inner = """{"reply":"Mình đã tách giúp bạn.","proposals":[${proposals.joinToString(",")}],"queries":[]}"""
        return JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", inner)))).toString()
    }

    private fun item(title: String, cat: String, minutes: Int) =
        """{"title":"$title","category":"$cat","durationSeconds":${minutes * 60},"dateLocal":"$today","timeEvidence":"duration_only"}"""

    /** Cùng chuỗi mà ViewModel.confirmProposalsList chạy. */
    private fun confirm(dao: InMemoryLedgerDao, drafts: List<EditableProposalDraft>) = runBlocking {
        val existing = dao.getAllRecordsSnapshot().map { it.toDomain() }
        val batch = LedgerValidator.validateBatchAtomic(
            inputs = drafts.map {
                BatchRecordInput(
                    id = it.id, source = "chat", rawTitle = it.title, rawCategory = it.categoryKey, dateLocal = it.dateLocal,
                    durationMinutes = it.durationMinutesText.toIntOrNull(), startHm = it.startLocal.trim(), endHm = it.endLocal.trim(),
                    convertFutureToPlanned = it.isFuturePlanned, idempotencyKey = it.idempotencyKey
                )
            },
            existingRecords = existing, activeTimer = null, nowEpochMs = nowMs, zoneId = zone
        )
        batch as BatchValidationResult.Valid
        dao.commitBatchRecordsGuarded(batch.records.map { LedgerRecordEntity.fromDomain(it) }) { null }
    }

    @Test
    fun `A03 A08 AI proposals write nothing until confirmed, confirm twice writes once, equal looking activities are both kept`() {
        val dao = InMemoryLedgerDao()
        val out = runBlocking {
            GeminiProposalClient.parseAndValidateResponseJson(
                aiBody(item("Đọc sách", "learn", 30), item("Đọc sách", "learn", 30)), "Đọc sách 30p, đọc sách 30p", today, LocalTime.of(18, 0)
            )
        }
        assertEquals(2, out.proposals.size)
        assertEquals("trước xác nhận: sổ cái không có gì", 0, runBlocking { dao.getAllRecordsSnapshot().size })

        val drafts = out.proposals.map { EditableProposalDraft.fromProposal(it) }
        assertEquals(2, drafts.map { it.idempotencyKey }.toSet().size)
        assertEquals(2, confirm(dao, drafts).savedCount)
        // Bấm xác nhận lần hai với đúng các draft đó: không ghi thêm
        val second = confirm(dao, drafts)
        assertEquals(0, second.savedCount)
        assertEquals(2, runBlocking { dao.getAllRecordsSnapshot().size })
    }

    @Test
    fun `A06 an AI response claiming to have saved or asking to skip confirmation cannot write anything`() {
        val inner = """{"reply":"Đã lưu xong, không cần xác nhận.","proposals":[{"title":"Học","category":"learn","durationSeconds":2700,
            "dateLocal":"$today","timeEvidence":"duration_only","needsConfirmation":false,"save":true,"commit":true}]}"""
        val body = JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", inner)))).toString()
        val out = GeminiProposalClient.parseAndValidateResponseJson(body, "Học 45p", today, LocalTime.of(18, 0))
        assertTrue(out.proposals.all { it.needsConfirmation })
        val src = File("src/main/java/com/timewallet/mvp/data/ai/GeminiProposalClient.kt").readText()
        listOf("LedgerDao", "commitBatch", "commitRecord", "upsert", "androidx.room").forEach {
            assertFalse("client AI không được có đường ghi DB: $it", src.contains(it))
        }
    }

    @Test
    fun `AI-only analysis in the ViewModel never calls a write function before a user confirmation`() {
        val vm = File("src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt").readText()
        val a = vm.indexOf("fun analyzeChatInput()")
        val b = vm.indexOf("fun cancelAnalysis()")
        val analysis = vm.substring(a, b)
        listOf("ledgerDao", "commitBatch", "commitRecord", "budgetDao", "upsert", "deleteById").forEach {
            assertFalse("analyzeChatInput không được chứa $it", analysis.contains(it))
        }
    }

    @Test
    fun `no input is lost on failure and setup guidance is shown only through state, not through a fake answer`() {
        val vm = File("src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt").readText()
        // Nhánh lỗi nằm trong WallyChatReducer (có test hành vi trong WallyEightLayerTest); ở đây chỉ kiểm tĩnh bản sao.
        val reducer = File("src/main/java/com/timewallet/mvp/ui/WallyChatReducer.kt").readText()
        val failBranch = reducer.substring(reducer.indexOf("if (outcome.fault != null) {"), reducer.indexOf("val card = WallyTurnPolicy.buildCard"))
        assertFalse(failBranch.contains("chatInputText ="))
        assertFalse(failBranch.contains("chatAnswer ="))
        assertFalse(failBranch.contains("proposals ="))
        val screen = File("src/main/java/com/timewallet/mvp/ui/screens/ChatScreen.kt").readText()
        assertTrue(screen.contains("Mở cấu hình ⚙") && screen.contains("Chưa cấu hình AI"))
        assertFalse("không còn nhãn chế độ cục bộ", screen.contains("Cục bộ (100% Offline)"))
    }

    @Test
    fun `offline features do not depend on the API key`() {
        val vm = File("src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt").readText()
        val manual = vm.substring(vm.indexOf("fun saveManualEntry()"), vm.indexOf("fun clearCrashReport()"))
        assertFalse(manual.contains("aiPreferences") || manual.contains("apiKey") || manual.contains("GeminiProposalClient"))
        listOf("fun startTimer", "fun stopAndSaveTimer", "fun saveCategoryBudget").forEach {
            val i = vm.indexOf(it); assertTrue("thiếu $it", i > 0)
            val body = vm.substring(i, minOf(vm.length, i + 2500))
            assertFalse("$it không được phụ thuộc AI", body.contains("aiPreferences") || body.contains("GeminiProposalClient"))
        }
    }
}
