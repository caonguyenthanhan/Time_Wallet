package com.timewallet.mvp

import com.timewallet.mvp.data.ai.AiHttpException
import com.timewallet.mvp.data.ai.AiUsageParser
import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.local.TimerCommit
import com.timewallet.mvp.data.local.TimerCommitResult
import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.ActivityProposal
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.AiFault
import com.timewallet.mvp.domain.usecase.AiProposalGuard
import com.timewallet.mvp.domain.usecase.CacheStatus
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.OutcomeSource
import com.timewallet.mvp.domain.usecase.ReplyGuard
import com.timewallet.mvp.domain.usecase.VietnameseChatParser
import com.timewallet.mvp.domain.usecase.WallyAnswerBuilder
import com.timewallet.mvp.ui.EditableProposalDraft
import com.timewallet.mvp.ui.TimeWalletUiState
import com.timewallet.mvp.ui.WallyChatReducer
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class V107RemediationTest {

    private val vn = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 13)
    private val nowTime = LocalTime.of(18, 0)

    private fun prop(title: String, cat: String, minutes: Int): ActivityProposal = ActivityProposal(
        id = UUID.randomUUID().toString(),
        rawInput = "$title ${minutes}p",
        title = title,
        category = cat,
        durationSeconds = minutes * 60L,
        dateLocal = today.toString(),
        startLocal = null,
        endLocal = null,
        timeEvidence = "duration_only",
        missingFields = listOf("startLocal", "endLocal"),
        needsConfirmation = true
    )

    private fun proposalJson(title: String, cat: String, minutes: Int): JSONObject = JSONObject()
        .put("title", title)
        .put("category", cat)
        .put("durationSeconds", minutes * 60L)
        .put("dateLocal", today.toString())
        .put("timeEvidence", "duration_only")

    private fun envelope(innerContent: String): String = JSONObject()
        .put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", innerContent))))
        .toString()

    // =========================================================================
    // 1. F-V105-03: Guard phủ định với số bằng chữ tiếng Việt
    // =========================================================================

    @Test
    fun `F-V105-03 pure negation with Vietnamese word numbers must be blocked`() {
        val cases = listOf(
            "Không học hai tiếng hôm nay" to prop("Học", "learn", 120),
            "Đừng ghi một tiếng rưỡi xem phim" to prop("Xem phim", "fun", 90),
            "Chưa chạy nửa tiếng sáng nay" to prop("Chạy bộ", "health", 30),
            "Không họp bốn mươi lăm phút" to prop("Họp", "work", 45),
            "Không làm 1 tiếng rưỡi chiều nay" to prop("Làm việc", "work", 90),
            "Chẳng đọc sách hai mươi lăm phút" to prop("Đọc sách", "learn", 25)
        )
        for ((sentence, aiProp) in cases) {
            val guarded = AiProposalGuard.apply(sentence, listOf(aiProp))
            assertTrue("Phủ định số bằng chữ phải bị chặn cho '$sentence', nhưng lại giữ ${guarded.proposals}", guarded.proposals.isEmpty())
            assertTrue("Phải có cảnh báo/làm rõ cho '$sentence'", guarded.warnings.isNotEmpty())
        }
    }

    @Test
    fun `F-V105-03 mixed negation and affirmative with word numbers keeps only affirmative`() {
        val learn120 = prop("Học", "learn", 120)
        val work180 = prop("Làm việc", "work", 180)
        val both = AiProposalGuard.apply("Không học hai tiếng hôm nay, làm việc ba giờ", listOf(learn120, work180))
        assertEquals("Phải chỉ giữ Làm việc 180p", listOf("Làm việc"), both.proposals.map { it.title })
        assertEquals(180, both.proposals.single().durationMinutes)

        // Khi AI chỉ trả phần khẳng định của câu hỗn hợp, không được chặn nhầm cả phần khẳng định
        val affirmativeOnlyFromAi = AiProposalGuard.apply("Không học hai tiếng hôm nay, làm việc ba giờ", listOf(work180))
        assertEquals(listOf("Làm việc"), affirmativeOnlyFromAi.proposals.map { it.title })

        val read30 = prop("Đọc sách", "learn", 30)
        val run90 = prop("Chạy bộ", "health", 90)
        val frac = AiProposalGuard.apply("Không đọc nửa tiếng, chỉ chạy bộ một tiếng rưỡi", listOf(read30, run90))
        assertEquals(listOf("Chạy bộ"), frac.proposals.map { it.title })
        assertEquals(90, frac.proposals.single().durationMinutes)
    }

    @Test
    fun `F-V105-03 same duration mixed negation and affirmative works with word numbers`() {
        val learn120 = prop("Học", "learn", 120)
        val read120 = prop("Đọc sách", "learn", 120)
        val work120 = prop("Làm việc", "work", 120)

        val sameWordDiffCat = AiProposalGuard.apply("Không học hai tiếng nhưng làm việc hai tiếng", listOf(learn120, work120))
        assertEquals(listOf("Làm việc"), sameWordDiffCat.proposals.map { it.title })

        val sameDigit = AiProposalGuard.apply("Không học 2h nhưng làm việc 2h", listOf(learn120, work120))
        assertEquals(listOf("Làm việc"), sameDigit.proposals.map { it.title })

        val sameWordDiffTitle = AiProposalGuard.apply("Không học hai tiếng nhưng đọc sách hai tiếng", listOf(learn120, read120))
        assertEquals(listOf("Đọc sách"), sameWordDiffTitle.proposals.map { it.title })
    }

    @Test
    fun `F-V105-03 ambiguous negation that cannot cleanly distinguish affirmative vs negated requires clarification`() {
        val cases = listOf(
            "Không học vài tiếng hôm nay" to prop("Học", "learn", 120),
            "Đừng ghi mấy giờ vừa rồi" to prop("Làm việc", "work", 120),
            "Không học và đọc sách hai tiếng" to prop("Đọc sách", "learn", 120),
            "Hôm nay không học hay làm việc hai tiếng đâu" to prop("Làm việc", "work", 120)
        )
        for ((sentence, aiProp) in cases) {
            val guarded = AiProposalGuard.apply(sentence, listOf(aiProp))
            assertTrue("Câu mơ hồ phủ định/khẳng định '$sentence' phải yêu cầu làm rõ (không tạo đề xuất), nhận ${guarded.proposals}", guarded.proposals.isEmpty())
            assertTrue("Phải có thông báo yêu cầu làm rõ cho '$sentence'", guarded.warnings.isNotEmpty())
        }
    }

    @Test
    fun `F-V105-03 non-stop phrase khong nghi and affirmative word numbers have no regression`() {
        val study120 = prop("Học", "learn", 120)
        val work180 = prop("Làm việc", "work", 180)
        val study90 = prop("Học", "learn", 90)
        val read30 = prop("Đọc sách", "learn", 30)

        assertEquals(1, AiProposalGuard.apply("Học 2h không nghỉ", listOf(study120)).proposals.size)
        assertEquals(1, AiProposalGuard.apply("Học không nghỉ hai tiếng", listOf(study120)).proposals.size)
        assertEquals(1, AiProposalGuard.apply("Làm việc không nghỉ ba giờ", listOf(work180)).proposals.size)
        assertEquals(1, AiProposalGuard.apply("Học hai tiếng", listOf(study120)).proposals.size)
        assertEquals(1, AiProposalGuard.apply("Học một tiếng rưỡi", listOf(study90)).proposals.size)
        assertEquals(1, AiProposalGuard.apply("Thứ tư đọc sách nửa tiếng", listOf(read30)).proposals.size)
        assertEquals(1, AiProposalGuard.apply("Năm nay học ba tiếng mỗi tối", listOf(prop("Học", "learn", 180))).proposals.size)

        // Tiêu đề cục bộ sau khi tách thời lượng chữ phải sạch, không sót từ số ("hai", "nửa", ...)
        val local = VietnameseChatParser.parse("Học hai tiếng").proposals.single()
        assertEquals(120, local.durationMinutes)
        assertFalse("Tiêu đề không được sót 'hai': ${local.title}", local.title.lowercase().contains("hai"))
    }

    // =========================================================================
    // 2. V106-N1: 14 mẫu đối kháng Reviewer + ưu tiên thông báo app ở luồng ghi
    // =========================================================================

    private val fourteenAdversarialReplies = listOf(
        // 6 mẫu được nêu tên trong REVIEW_WALLY_V106.md; 8 mẫu bên dưới do Master TÁI DỰNG (không phải 14 mẫu nguyên bản của Reviewer;
        // bộ nguyên bản được khôi phục trong REVIEW_WALLY_V107.md mục 3.2 và kiểm riêng ở V108TransactionStatusTest)
        "Yên tâm, hoạt động học đã tự động hoàn tất rồi nhé!",
        "Không cần làm gì thêm đâu, mình giữ giúp bạn rồi.",
        "Xong xuôi rồi nhé, mục học 45p của bạn đã vào danh sách.",
        "Mình vừa xử lý xong việc ghi lại 45p học tập cho bạn rồi.",
        "Tất cả xong rồi bạn nhé, 45p đã có mặt trong Nhật ký.",
        "Sổ cái đã nhận 45p học tập của bạn.",
        // 8 mẫu đối kháng bổ sung (trực tiếp / gián tiếp / bỏ qua xác nhận / tiếng Anh)
        "Đã ghi 45 phút học cho bạn.",
        "Mình đã lưu xong hoạt động học 45p rồi nhé.",
        "Hoạt động học đã nằm trong sổ rồi bạn.",
        "Đã cập nhật xong vào lịch sử thời gian của bạn.",
        "Hệ thống đã tự động ghi và không cần xác nhận đâu.",
        "Mọi thứ xong hết rồi, khỏi bấm xác nhận nữa nhé!",
        "Tất cả dữ liệu đã được đưa vào nhật ký ngày hôm nay.",
        "Saved and recorded 45 minutes in your ledger."
    )

    @Test
    fun `V106-N1 14 adversarial replies (6 named in REVIEW_WALLY_V106 plus 8 reconstructed by Master, NOT the Reviewer's original 14) are blocked across prop0, prop1, mixed and ReplyGuard`() {
        assertEquals(14, fourteenAdversarialReplies.size)
        val p1 = listOf(prop("Học", "learn", 45))

        for ((idx, adv) in fourteenAdversarialReplies.withIndex()) {
            // 1) Direct ReplyGuard check
            val (sanitized, warn) = ReplyGuard.sanitize(adv, "Học 45p", p1, hasQueries = false)
            assertNull("Mẫu #${idx + 1} phải bị ReplyGuard chặn: '$adv'", sanitized)
            assertNotNull("Mẫu #${idx + 1} phải có cảnh báo: '$adv'", warn)

            // 2) prop0 (không đề xuất mới — kể cả khi người dùng đang có pending proposal chờ xác nhận)
            val prop0Inner = JSONObject()
                .put("reply", adv)
                .put("proposals", JSONArray())
                .put("queries", JSONArray())
                .toString()
            val outProp0 = GeminiProposalClient.parseAndValidateResponseJson(envelope(prop0Inner), "Ghi chưa?", today, nowTime)
            assertNull("prop0 mẫu #${idx + 1} không được để AI tuyên bố đã lưu: '$adv'", outProp0.reply)
            assertTrue("prop0 mẫu #${idx + 1} phải ghi nhận cảnh báo ứng dụng", outProp0.warnings.isNotEmpty())

            // Khi đã có pending proposal trong UI state, prop0 không được dựng card chứa lời đáp AI giả thành công
            val pending = EditableProposalDraft.fromProposal(p1.single())
            val stateWithPending = TimeWalletUiState(proposals = listOf(pending))
            val reducedProp0 = WallyChatReducer.applyResult(stateWithPending, "Xong chưa?", outProp0, stateWithPending.proposals, null) { _, _ -> emptyList() }
            assertEquals("Pending proposal vẫn phải giữ nguyên chờ xác nhận", 1, reducedProp0.proposals.size)
            val headline0 = reducedProp0.chatAnswer?.headline.orEmpty()
            assertFalse("Card ứng dụng không được chứa lời AI bịa: $headline0", headline0.contains(adv.take(15)))

            // 3) prop1 (luồng ghi nhận có 1 đề xuất mới)
            val prop1Inner = JSONObject()
                .put("reply", adv)
                .put("proposals", JSONArray().put(proposalJson("Học", "learn", 45)))
                .put("queries", JSONArray())
                .toString()
            val outProp1 = GeminiProposalClient.parseAndValidateResponseJson(envelope(prop1Inner), "Học 45p", today, nowTime)
            assertEquals(1, outProp1.proposals.size)
            assertNull("prop1 mẫu #${idx + 1} không được trả reply trước commit", outProp1.reply)
            val merged = stateWithPending.proposals + EditableProposalDraft.fromProposal(outProp1.proposals.single())
            val reducedProp1 = WallyChatReducer.applyResult(TimeWalletUiState(), "Học 45p", outProp1, merged, null) { _, _ -> emptyList() }
            assertNull("Luồng ghi nhận không dựng ChatAnswer khi chưa commit", reducedProp1.chatAnswer)

            // 4) MIXED (prop1 + query1)
            val mixedInner = JSONObject()
                .put("reply", adv)
                .put("proposals", JSONArray().put(proposalJson("Học", "learn", 45)))
                .put("queries", JSONArray().put(JSONObject()
                    .put("metric", "TOTAL")
                    .put("startDate", today.toString())
                    .put("endDate", today.toString())))
                .toString()
            val outMixed = GeminiProposalClient.parseAndValidateResponseJson(envelope(mixedInner), "Học 45p và tổng hôm nay", today, nowTime)
            assertNull("MIXED mẫu #${idx + 1} phải bỏ lời đáp bịa đã lưu", outMixed.reply)
            assertEquals(1, outMixed.proposals.size)
            assertEquals(1, outMixed.queries.size)
        }
    }

    @Test
    fun `V106-N1 recording flow prioritizes application state over any AI reply pre-commit`() {
        // Kể cả khi AI trả một câu lách khéo mới chưa từng gặp trước đây trong luồng ghi nhận (prop1, no queries),
        // ứng dụng vẫn ưu tiên thẻ/trạng thái đề xuất do application tạo và không cho reply AI xuất hiện trước commit.
        val novelTrickyReply = "Mọi hồ sơ học tập hôm nay đã ổn định rồi bạn nhé."
        val prop1Inner = JSONObject()
            .put("reply", novelTrickyReply)
            .put("proposals", JSONArray().put(proposalJson("Học", "learn", 45)))
            .put("queries", JSONArray())
            .toString()
        val out = GeminiProposalClient.parseAndValidateResponseJson(envelope(prop1Inner), "Học 45p", today, nowTime)
        assertEquals(1, out.proposals.size)
        assertNull("Luồng ghi nhận không hiển thị lời đáp tự do của AI khi chỉ có pending proposal", out.reply)
        assertNull(
            "WallyAnswerBuilder không dựng ChatAnswer khi có proposal chưa commit và không có query",
            WallyAnswerBuilder.build("Học 45p", novelTrickyReply, emptyList(), emptyList(), proposalCount = 1)
        )
    }

    @Test
    fun `V106-N1 safe pending confirmation phrasing and mixed analytical query are preserved`() {
        val p1 = listOf(prop("Học", "learn", 45))
        assertNotNull(ReplyGuard.sanitize("Bạn xác nhận để mình ghi nhé.", "Học 45p", p1, false).first)
        assertNotNull(ReplyGuard.sanitize("Mình chưa ghi gì cả, bạn kiểm tra thẻ đề xuất nhé.", "Học 45p", p1, false).first)
        assertNotNull(ReplyGuard.sanitize("Mình sẽ không tự động xác nhận thay bạn.", "Học 45p", p1, false).first)

        val mixedSafe = JSONObject()
            .put("reply", "Mình tách 1 hoạt động và lấy số liệu hôm nay.")
            .put("proposals", JSONArray().put(proposalJson("Học", "learn", 45)))
            .put("queries", JSONArray().put(JSONObject()
                .put("metric", "TOTAL")
                .put("startDate", today.toString())
                .put("endDate", today.toString())))
            .toString()
        val out = GeminiProposalClient.parseAndValidateResponseJson(envelope(mixedSafe), "Học 45p và tổng hôm nay", today, nowTime)
        // v1.0.8 (V107-F2): có đề xuất chờ xác nhận (RECORD và MIXED) thì ứng dụng tạo headline/trạng thái; lời AI không hiển thị.
        // Đề xuất đúng và truy vấn hợp lệ vẫn được giữ nguyên.
        assertNull(out.reply)
        assertEquals(1, out.proposals.size)
        assertEquals(1, out.queries.size)
    }

    // =========================================================================
    // 3. V106-N2: BOM trước kiểm tra JSON & bảo toàn tổng duration
    // =========================================================================

    private fun assertMalformed(label: String, block: () -> Unit) {
        try {
            block()
            fail("Phải báo AiFault.MALFORMED cho '$label'")
        } catch (e: AiHttpException) {
            assertEquals("Sai fault cho '$label'", AiFault.MALFORMED, e.fault)
        }
    }

    @Test
    fun `V106-N2 BOM prefix before JSON primitives and arrays fails as MALFORMED`() {
        val innerMalformed = listOf(
            "\uFEFF[1, 2]",
            "\uFEFFnull",
            "\uFEFF  [1, 2]  ",
            "\n\uFEFFtrue",
            "\uFEFF123"
        )
        for (raw in innerMalformed) {
            assertMalformed("inner=$raw") {
                GeminiProposalClient.parseAndValidateResponseJson(envelope(raw), "x", today, nowTime)
            }
            assertMalformed("outer=$raw") {
                GeminiProposalClient.parseAndValidateResponseJson(raw, "x", today, nowTime)
            }
        }
    }

    @Test
    fun `V106-N2 valid JSON with leading UTF-8 BOM in outer body, inner content, fenced JSON and usage parses cleanly`() {
        val innerJson = JSONObject()
            .put("reply", "Để mình tính giúp bạn nhé.")
            .put("proposals", JSONArray())
            .put("queries", JSONArray().put(JSONObject()
                .put("metric", "TOTAL")
                .put("startDate", today.toString())
                .put("endDate", today.toString())))
            .toString()
        val outerWithBom = "\uFEFF" + envelope("\uFEFF$innerJson")
        val out = GeminiProposalClient.parseAndValidateResponseJson(outerWithBom, "Hôm nay dùng bao nhiêu giờ?", today, nowTime)
        assertEquals("Để mình tính giúp bạn nhé.", out.reply)
        assertEquals(1, out.queries.size)

        val fencedWithBom = "\uFEFF```json\n\uFEFF$innerJson\n```"
        val outFenced = GeminiProposalClient.parseAndValidateResponseJson(envelope(fencedWithBom), "Hôm nay dùng bao nhiêu giờ?", today, nowTime)
        assertEquals(1, outFenced.queries.size)

        val usage = AiUsageParser.parseBody("""\uFEFF{"usage":{"prompt_tokens":10,"completion_tokens":5,"cached_tokens":4}}""".replace("\\uFEFF", "\uFEFF"))
        assertNotNull(usage)
        assertEquals(CacheStatus.HIT, usage!!.cacheStatus)
        assertEquals(10, usage.promptTokens)
    }

    @Test
    fun `V106-N2 TimerCommit conserves floor seconds across sub-second remainders over 24h and 48h`() = runBlocking {
        val start = today.atTime(9, 0).atZone(vn).toInstant().toEpochMilli()

        // 24h + 500ms => floor = 86_400s (1 đoạn, không phình thành 86_401s)
        val dao1 = InMemoryLedgerDao()
        val res1 = TimerCommit.run(dao1, "Học", "learn", start, start + 86_400_500L, vn)
        assertTrue("Phải lưu thành công: $res1", res1 is TimerCommitResult.Saved)
        res1 as TimerCommitResult.Saved
        assertEquals(1, res1.segments)
        assertEquals(86_400L, res1.totalSeconds)
        assertEquals(86_400L, dao1.getAllRecordsSnapshot().sumOf { it.durationSeconds })

        // 48h + 999ms => floor = 172_800s (2 đoạn)
        val dao2 = InMemoryLedgerDao()
        val res2 = TimerCommit.run(dao2, "Làm", "work", start, start + 2 * 86_400_000L + 999L, vn)
        assertTrue(res2 is TimerCommitResult.Saved)
        res2 as TimerCommitResult.Saved
        assertEquals(2, res2.segments)
        assertEquals(172_800L, res2.totalSeconds)
        assertEquals(172_800L, dao2.getAllRecordsSnapshot().sumOf { it.durationSeconds })

        // 48h + 1_500ms => floor = 172_801s (3 đoạn: 86400 + 86400 + 1)
        val dao3 = InMemoryLedgerDao()
        val res3 = TimerCommit.run(dao3, "Làm", "work", start, start + 2 * 86_400_000L + 1_500L, vn)
        assertTrue(res3 is TimerCommitResult.Saved)
        res3 as TimerCommitResult.Saved
        assertEquals(3, res3.segments)
        assertEquals(172_801L, res3.totalSeconds)
        assertEquals(listOf(86_400L, 86_400L, 1L), dao3.getAllRecordsSnapshot().sortedBy { it.startedAt }.map { it.durationSeconds })

        // < 1s (500ms) => Invalid ("Đồng hồ cần chạy ít nhất 1 giây.")
        val dao0 = InMemoryLedgerDao()
        val res0 = TimerCommit.run(dao0, "Ngắn", "work", start, start + 500L, vn)
        assertTrue("Chạy dưới 1 giây không được phình lên 1 giây: $res0", res0 is TimerCommitResult.Invalid)
        assertTrue(dao0.getAllRecordsSnapshot().isEmpty())
    }

    @Test
    fun `V106-N2 LedgerAnalyticsCalculator avoids cumulative sub-second loss in day sweep-line and midnight cross`() = runBlocking {
        // 1) Sweep-line tích lũy nhiều mảnh < 1s trong cùng 1 ngày (600ms + 600ms + 800ms = 2000ms = 2s)
        val dayStart = today.atStartOfDay(vn).toInstant().toEpochMilli()
        val recs = listOf(
            LedgerRecord(
                id = UUID.randomUUID().toString(),
                source = "timer", layer = TimeLayer.MEASURED,
                createdAt = dayStart, userConfirmedAt = dayStart + 1200L,
                category = TimeCategory.WORK, title = "Mảnh 1", date = today.toString(),
                durationSeconds = 1L, startedAt = dayStart, endedAt = dayStart + 600L,
                timezone = vn.id, status = "CONFIRMED", idempotencyKey = "k1"
            ),
            LedgerRecord(
                id = UUID.randomUUID().toString(),
                source = "timer", layer = TimeLayer.MEASURED,
                createdAt = dayStart, userConfirmedAt = dayStart + 1200L,
                category = TimeCategory.WORK, title = "Mảnh 2", date = today.toString(),
                durationSeconds = 1L, startedAt = dayStart + 600L, endedAt = dayStart + 1200L,
                timezone = vn.id, status = "CONFIRMED", idempotencyKey = "k2"
            ),
            LedgerRecord(
                id = UUID.randomUUID().toString(),
                source = "timer", layer = TimeLayer.MEASURED,
                createdAt = dayStart, userConfirmedAt = dayStart + 2000L,
                category = TimeCategory.LEARN, title = "Mảnh 3", date = today.toString(),
                durationSeconds = 1L, startedAt = dayStart + 1200L, endedAt = dayStart + 2000L,
                timezone = vn.id, status = "CONFIRMED", idempotencyKey = "k3"
            )
        )
        val stats = LedgerAnalyticsCalculator.computeDayStats(today.toString(), recs, vn, dayStart + 10_000L, null)
        assertEquals("Tổng hợp sub-second liên tiếp phải bảo toàn 2s", 2L, stats.timedSeconds)
        assertEquals("Tổng các danh mục phải bằng timedSeconds", stats.timedSeconds, stats.categoryBreakdown.sumOf { it.durationSeconds })

        // 2) Đồng hồ bấm giờ có startedAt lệch .400ms chạy vắt qua nửa đêm 20 phút (1200s) -> tổng 2 ngày phải đúng 1200s
        val crossStart = today.atTime(23, 50, 0).atZone(vn).toInstant().toEpochMilli() + 400L
        val crossEnd = crossStart + 1_200_900L // floor = 1200s
        val dao = InMemoryLedgerDao()
        val commitRes = TimerCommit.run(dao, "Trực đêm", "work", crossStart, crossEnd, vn)
        assertTrue(commitRes is TimerCommitResult.Saved)
        commitRes as TimerCommitResult.Saved
        assertEquals(1200L, commitRes.totalSeconds)

        val savedRecords = dao.getAllRecordsSnapshot().map { it.toDomain() }
        val nextDay = today.plusDays(1)
        val day1Stats = LedgerAnalyticsCalculator.computeDayStats(today.toString(), savedRecords, vn, crossEnd + 1000L, null)
        val day2Stats = LedgerAnalyticsCalculator.computeDayStats(nextDay.toString(), savedRecords, vn, crossEnd + 1000L, null)
        assertEquals("Không được mất 1 giây khi cắt qua nửa đêm", 1200L, day1Stats.timedSeconds + day2Stats.timedSeconds)
    }
}