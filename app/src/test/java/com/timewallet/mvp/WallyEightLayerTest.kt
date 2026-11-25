package com.timewallet.mvp

import com.timewallet.mvp.data.ai.AiUsageParser
import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.diagnostics.AiDiagnostics
import com.timewallet.mvp.data.local.BatchConfirm
import com.timewallet.mvp.data.local.ConfirmResult
import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.usecase.AiFault
import com.timewallet.mvp.domain.usecase.AiQueryValidator
import com.timewallet.mvp.domain.usecase.CacheStatus
import com.timewallet.mvp.domain.usecase.LedgerQueryEngine
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.OutcomeSource
import com.timewallet.mvp.domain.usecase.ParseChatOutcome
import com.timewallet.mvp.domain.usecase.QueryContext
import com.timewallet.mvp.domain.usecase.QueryMetric
import com.timewallet.mvp.domain.usecase.QueryPeriod
import com.timewallet.mvp.domain.usecase.QueryPeriodKind
import com.timewallet.mvp.domain.usecase.ValidationResult
import com.timewallet.mvp.domain.usecase.WallyIntentKind
import com.timewallet.mvp.ui.EditableProposalDraft
import com.timewallet.mvp.ui.TimeWalletUiState
import com.timewallet.mvp.ui.WallyChatReducer
import com.timewallet.mvp.ui.toBatchInput
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/**
 * WALLY 8-LAYER: bằng chứng cho prompt cache (cấu trúc tiền tố), usage/metrics, phân loại ý định, cấu hình endpoint/model,
 * tích hợp ViewModel (qua reducer + BatchConfirm dùng chung với ViewModel), độ mới của số liệu sau thêm/sửa/xóa.
 * Mock HTTP, dữ liệu tổng hợp; KHÔNG gọi API thật nên KHÔNG có bằng chứng cache hit thực tế.
 */
class WallyEightLayerTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 13)
    private val nowMs = today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
    private val key = "sk-synthetic-8layer-key"

    // ---------- hạ tầng giả ----------
    private class Conn(url: URL, private val reply: () -> Pair<Int, String>) : HttpURLConnection(url) {
        val sent = ByteArrayOutputStream()
        val props = LinkedHashMap<String, String>()
        override fun connect() {}
        override fun usingProxy() = false
        override fun disconnect() {}
        override fun setRequestProperty(k: String?, v: String?) { if (k != null && v != null) props[k] = v }
        override fun getOutputStream(): OutputStream = sent
        private val r by lazy { reply() }
        override fun getResponseCode(): Int = r.first
        override fun getInputStream(): InputStream = ByteArrayInputStream(r.second.toByteArray())
        override fun getErrorStream(): InputStream? = null
    }

    private class Net(private vararg val replies: Pair<Int, String>) {
        val conns = mutableListOf<Conn>()
        private var i = 0
        val opener: (URL) -> HttpURLConnection = { url ->
            val r = replies[minOf(i++, replies.size - 1)]
            Conn(url) { r }.also { conns += it }
        }
        fun payload(n: Int = 0) = JSONObject(conns[n].sent.toString("UTF-8"))
    }

    private fun body(content: String, usage: JSONObject? = null): String {
        val root = JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", content))))
        if (usage != null) root.put("usage", usage)
        return root.toString()
    }

    private fun item(title: String, cat: String, minutes: Int) =
        """{"title":"$title","category":"$cat","durationSeconds":${minutes * 60},"dateLocal":"$today","timeEvidence":"duration_only"}"""

    private fun ask(
        net: Net, text: String, date: LocalDate = today, time: LocalTime = LocalTime.of(18, 0), last: QueryContext? = null,
        base: String = "https://api.example.invalid/v1", model: String = "model-x", trusted: String? = null,
        optIn: Boolean = true, apiKey: String = key
    ): ParseChatOutcome = runBlocking {
        GeminiProposalClient.analyzeInput(
            userText = text, today = date, nowTime = time, optInEnabled = optIn, baseUrl = base, apiKey = apiKey,
            modelName = model, trustedHost = trusted, connectionFactory = net.opener, sleeper = { }, lastQuery = last
        )
    }

    // ================= L07: prompt cache — cấu trúc tiền tố =================
    @Test
    fun `prompt cache layout - the system message is byte identical for every request and holds no dynamic data`() {
        val systems = mutableListOf<String>()
        val users = mutableListOf<String>()
        val ctx = QueryContext(QueryMetric.TOTAL, QueryPeriod(today, today, "hôm nay", QueryPeriodKind.DATE), null)
        listOf(
            Triple("Học 45p", today, LocalTime.of(8, 5)),
            Triple("Hôm nay tôi dùng bao nhiêu giờ?", today.plusDays(40), LocalTime.of(23, 59)),
            Triple("Xin chào", today.minusDays(300), LocalTime.of(0, 0))
        ).forEachIndexed { i, (text, date, time) ->
            val net = Net(200 to body("""{"reply":"ok"}"""))
            ask(net, text, date, time, last = if (i == 1) ctx else null)
            val msgs = net.payload().getJSONArray("messages")
            assertEquals("system", msgs.getJSONObject(0).getString("role"))
            assertEquals("user", msgs.getJSONObject(1).getString("role"))
            systems += msgs.getJSONObject(0).getString("content")
            users += msgs.getJSONObject(1).getString("content")
        }
        assertEquals("tiền tố tĩnh phải giống hệt giữa mọi yêu cầu", 1, systems.toSet().size)
        assertEquals(GeminiProposalClient.staticSystemPrompt, systems[0])
        // Dữ liệu động chỉ nằm ở tin nhắn user, sau tiền tố
        assertTrue(users[0].contains("2026-11-13") && users[0].contains("08:05") && users[0].contains("Học 45p"))
        assertTrue(users[1].contains("metric=TOTAL"))
        assertFalse(users[0].contains("metric="))
        listOf("2026-11-13", "08:05", "23:59", "Asia/Ho_Chi_Minh", "Học 45p", "2026-").forEach {
            assertFalse("system prompt không được chứa dữ liệu động: $it", systems[0].contains(it))
        }
        assertTrue(users.all { it.indexOf("[NGỮ CẢNH") < it.indexOf("[CÂU CỦA NGƯỜI DÙNG") })
    }

    @Test
    fun `prompt cache layout - version and prefix hash are stable and the combined helper keeps the old rules`() {
        assertEquals("wally-prompt-10", GeminiProposalClient.PROMPT_VERSION) // v1.0.6: metric BUDGET (prompt-4); v1.0.12: quy tắc 7b (prompt-5); v1.0.13: quy tắc 7c/7d + câu hỏi lại (prompt-6/7); v1.2.0: quy tắc 5b UC-15 xuyên đêm & chống đoán mơ hồ (prompt-8); v2.2.3: tag cá nhân, xếp lịch nhiều lượt & gắn/hoàn tác Tag phiên cũ (prompt-9/10)
        assertEquals(12, GeminiProposalClient.staticPromptHash.length)
        // Băm cho wally-prompt-10: nếu đổi nội dung tĩnh phải đổi PROMPT_VERSION và cập nhật băm này có chủ đích.
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(GeminiProposalClient.staticSystemPrompt.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        assertTrue(digest.startsWith(GeminiProposalClient.staticPromptHash))
        val combined = GeminiProposalClient.buildSystemPrompt(today, "14:00", zone.id)
        assertTrue(combined.startsWith(GeminiProposalClient.staticSystemPrompt))
        assertTrue(combined.contains("hôm qua") && combined.contains("MỆNH ĐỀ TỔNG HỢP") && combined.contains("2h chiều"))
        assertTrue("đủ dài để có cơ hội vượt ngưỡng tối thiểu của bộ nhớ đệm tiền tố", GeminiProposalClient.staticSystemPrompt.length > 4000)
    }

    @Test
    fun `no provider specific cache fields are sent because the provider is unverified`() {
        val net = Net(200 to body("""{"reply":"ok"}"""))
        ask(net, "Học 45p")
        val keys = net.payload().keys().asSequence().toSet()
        assertEquals(setOf("model", "temperature", "messages"), keys)
    }

    // ================= usage / cache status: không giả cache hit =================
    @Test
    fun `usage parser reports HIT or MISS only when the provider reports cached tokens`() {
        fun u(json: String) = AiUsageParser.parse(JSONObject(json))
        val hit = u("""{"usage":{"prompt_tokens":2000,"completion_tokens":50,"prompt_tokens_details":{"cached_tokens":1536}}}""")!!
        assertEquals(CacheStatus.HIT, hit.cacheStatus); assertEquals(1536, hit.cachedTokens); assertEquals(2000, hit.promptTokens)
        assertEquals(CacheStatus.MISS, u("""{"usage":{"prompt_tokens":10,"prompt_tokens_details":{"cached_tokens":0}}}""")!!.cacheStatus)
        assertEquals(CacheStatus.HIT, u("""{"usage":{"input_tokens":9,"cache_read_input_tokens":5}}""")!!.cacheStatus)
        assertEquals(CacheStatus.HIT, u("""{"usage":{"promptTokenCount":9,"cachedContentTokenCount":4}}""")!!.cacheStatus)
        val none = u("""{"usage":{"prompt_tokens":120,"completion_tokens":30,"total_tokens":150}}""")!!
        assertEquals("không báo thì KHÔNG được coi là MISS hay HIT", CacheStatus.NOT_REPORTED, none.cacheStatus)
        assertNull(none.cachedTokens)
        assertNull("không có usage thì không có số", u("""{"choices":[]}"""))
        assertNull(AiUsageParser.parseBody("không phải json"))
        assertNull(u("""{"usage":{"prompt_tokens":-5,"completion_tokens":"x"}}""")!!.promptTokens)
    }

    // ================= L08: metrics =================
    @Test
    fun `metrics count requests latency retries tokens cache status intents and errors without any private content`() {
        AiDiagnostics.clear()
        val usage = JSONObject().put("prompt_tokens", 2100).put("completion_tokens", 40)
            .put("prompt_tokens_details", JSONObject().put("cached_tokens", 1800))
        // 1) ghi nhận có usage + cache HIT
        ask(Net(200 to body("""{"reply":"Đã tách.","proposals":[${item("Học", "learn", 45)}]}""", usage)), "Học 45p")
        // 2) 500 rồi 200: một lần thử lại, usage không báo trường cache
        val plain = JSONObject().put("prompt_tokens", 2000).put("completion_tokens", 20)
        ask(Net(500 to "", 200 to body("""{"reply":"Chào bạn!"}""", plain)), "Xin chào")
        // 3) lỗi 401
        ask(Net(401 to ""), "Học 45p")
        // 4) chưa cấu hình + lệnh điều khiển cục bộ
        ask(Net(), "Học 45p", apiKey = "")
        ask(Net(), "Hủy tất cả đề xuất")

        val m = AiDiagnostics.summary()
        assertEquals("yêu cầu cloud logic (mỗi lượt Wally = 1, kể cả khi thử lại; không tính cục bộ/chưa cấu hình)", 3, m.cloudRequests)
        assertEquals(1, m.cloudFailures)
        assertEquals(1, m.failuresByFault[AiFault.AUTH])
        assertEquals(1, m.retries)
        assertEquals(1, m.localControl)
        assertEquals(1, m.notConfigured)
        assertEquals(4100L, m.promptTokens)
        assertEquals(60L, m.completionTokens)
        assertEquals(1800L, m.cachedTokens)
        assertEquals(1, m.cacheHit)
        assertEquals(1, m.cacheNotReported)
        assertEquals(0, m.cacheMiss)
        assertEquals(1, m.byIntent[WallyIntentKind.RECORD])
        assertEquals(1, m.byIntent[WallyIntentKind.CHAT])
        assertEquals(1, m.byIntent[WallyIntentKind.ERROR])
        assertEquals(1, m.byIntent[WallyIntentKind.CONTROL])
        assertEquals(1, m.byIntent[WallyIntentKind.NOT_CONFIGURED])
        assertTrue(m.maxLatencyMs >= m.averageLatencyMs)

        val dump = AiDiagnostics.summaryText(GeminiProposalClient.PROMPT_VERSION, GeminiProposalClient.staticPromptHash) + "\n" + AiDiagnostics.asText()
        listOf(key, "Học 45p", "Xin chào", "Đã tách", "Chào bạn", "Hủy tất cả", "Học", "api.example.invalid").forEach {
            assertFalse("chẩn đoán không được chứa: $it", dump.contains(it))
        }
        assertTrue(dump.contains("HIT=1") && dump.contains("thử lại=1") && dump.contains("prefix=" + GeminiProposalClient.staticPromptHash))
    }

    @Test
    fun `a provider that never reports usage is shown as such and never as a cache hit`() {
        AiDiagnostics.clear()
        repeat(3) { ask(Net(200 to body("""{"reply":"ok"}""")), "Xin chào") }
        val m = AiDiagnostics.summary()
        assertEquals(3, m.cloudRequests)
        assertEquals(0, m.cacheHit)
        assertEquals(3, m.cacheNoUsage)
        assertTrue(AiDiagnostics.summaryText().contains("nhà cung cấp chưa báo usage"))
    }

    // ================= L07 Router: phân loại ý định (mọi câu vẫn gọi API) =================
    @Test
    fun `intent classification covers record query mixed chat clarify blocked error control and not-configured and every NL message calls the API`() {
        fun kind(text: String, reply: String, userText: String = text): WallyIntentKind {
            val net = Net(200 to body(reply))
            val out = ask(net, userText)
            assertEquals("câu \"$userText\" phải gọi API đúng 1 lần", 1, net.conns.size)
            return out.intent!!
        }
        val q = """{"metric":"TOTAL","startDate":"2026-11-13","endDate":"2026-11-13"}"""
        assertEquals(WallyIntentKind.RECORD, kind("Học 45p", """{"proposals":[${item("Học", "learn", 45)}]}"""))
        assertEquals(WallyIntentKind.QUERY, kind("Hôm nay tôi dùng bao nhiêu?", """{"queries":[$q]}"""))
        assertEquals(WallyIntentKind.MIXED, kind("Học 45p và hôm nay tôi dùng bao nhiêu?", """{"proposals":[${item("Học", "learn", 45)}],"queries":[$q]}"""))
        assertEquals(WallyIntentKind.CHAT, kind("Xin chào", """{"reply":"Chào bạn!"}"""))
        assertEquals(WallyIntentKind.CLARIFY, kind("???", """{"proposals":[]}"""))
        assertEquals("phủ định: AI có đề xuất nhưng chính sách bỏ hết", WallyIntentKind.BLOCKED,
            kind("Hôm nay tôi không học 2 tiếng", """{"proposals":[${item("Học", "learn", 120)}]}"""))
        // lỗi và các nhánh không gọi mạng
        val err = ask(Net(404 to ""), "Học 45p")
        assertEquals(WallyIntentKind.ERROR, err.intent)
        val ctl = Net(); assertEquals(WallyIntentKind.CONTROL, ask(ctl, "Hủy đề xuất").intent); assertEquals(0, ctl.conns.size)
        val nc = Net(); assertEquals(WallyIntentKind.NOT_CONFIGURED, ask(nc, "Học 45p", optIn = false).intent); assertEquals(0, nc.conns.size)
    }

    // ================= Manager #5: endpoint / model / host / key =================
    @Test
    fun `endpoint model and key go exactly where configured and nowhere else`() {
        val net = Net(200 to body("""{"reply":"ok"}"""))
        ask(net, "Học 45p", base = "https://gateway.example.invalid/api/v1/", model = "provider/model-9", trusted = "gateway.example.invalid")
        val c = net.conns.single()
        assertEquals("https://gateway.example.invalid/api/v1/chat/completions", c.url.toString())
        assertEquals("provider/model-9", net.payload().getString("model"))
        assertEquals("Bearer $key", c.props["Authorization"])
        assertFalse("khóa chỉ ở header", c.sent.toString("UTF-8").contains(key))
        assertFalse("không redirect", c.instanceFollowRedirects)

        // endpoint đã kết thúc bằng /chat/completions thì không nối thêm
        val net2 = Net(200 to body("""{"reply":"ok"}"""))
        ask(net2, "Học 45p", base = "https://gateway.example.invalid/v1/chat/completions")
        assertEquals("https://gateway.example.invalid/v1/chat/completions", net2.conns.single().url.toString())
    }

    @Test
    fun `invalid or untrusted endpoints never open a connection and never receive the key`() {
        listOf(
            "http://gateway.example.invalid/v1",              // không https
            "https://user:pw@gateway.example.invalid/v1",      // userinfo
            "https://gateway.example.invalid/v1?x=1",          // query
            "https://gateway.example.invalid/v1#frag",         // fragment
            "ftp://gateway.example.invalid/v1",
            "not a url"
        ).forEach {
            val net = Net(200 to body("""{"reply":"ok"}"""))
            val out = ask(net, "Học 45p", base = it, trusted = "gateway.example.invalid")
            assertEquals("endpoint không hợp lệ: $it", AiFault.CONFIG, out.fault)
            assertEquals(0, net.conns.size)
        }
        val net = Net(200 to body("""{"reply":"ok"}"""))
        assertEquals(AiFault.CONFIG, ask(net, "Học 45p", base = "https://other.example.invalid/v1", trusted = "gateway.example.invalid").fault)
        assertEquals("host khác với host gắn với khóa: không gửi khóa", 0, net.conns.size)
        assertEquals("host so khớp không phân biệt hoa thường", 1, Net(200 to body("""{"reply":"ok"}""")).also {
            ask(it, "Học 45p", base = "https://Gateway.Example.INVALID/v1", trusted = "gateway.example.invalid")
        }.conns.size)
    }

    // ================= Manager #4: tích hợp ViewModel qua reducer + BatchConfirm =================
    private fun rec(cat: String, minutes: Int, existing: List<LedgerRecord>, date: LocalDate = today): LedgerRecord =
        (LedgerValidator.validateAndBuildRecord(
            source = "manual", rawTitle = "t", rawCategory = cat, dateLocal = date.toString(), inputDurationMinutes = minutes,
            startedAt = null, endedAt = null, existingRecords = existing, nowEpochMs = nowMs, zoneId = zone
        ) as ValidationResult.Valid).record

    private val compute: (List<LedgerRecord>, ActiveTimerSession?) -> List<com.timewallet.mvp.domain.usecase.QueryAnswer> = { recs, timer ->
        listOf(
            LedgerQueryEngine.execute(
                (AiQueryValidator.validate("TOTAL", today.toString(), today.toString(), null, null, null, today) as AiQueryValidator.Result.Ok).intent,
                recs, zone, nowMs, timer
            )
        )
    }

    private fun mixedOutcome(): ParseChatOutcome {
        val q = """{"metric":"UNTIMED","startDate":"$today","endDate":"$today"}"""
        return ask(
            Net(200 to body("""{"reply":"Mình tách 1 hoạt động và lấy số liệu.","proposals":[${item("Học", "learn", 45)}],"queries":[$q]}""")),
            "Học 45p và hôm nay tôi có bao nhiêu giờ chưa gắn giờ?"
        )
    }

    @Test
    fun `reducer - a mixed result yields drafts and an answer card together, clears the input and keeps nothing stale`() {
        val outcome = mixedOutcome()
        assertEquals(WallyIntentKind.MIXED, outcome.intent)
        val drafts = outcome.proposals.map { EditableProposalDraft.fromProposal(it) }
        val before = TimeWalletUiState(chatInputText = "Học 45p và hôm nay...", records = emptyList())
        val after = WallyChatReducer.applyResult(before, "Học 45p…", outcome, drafts, null, compute)
        assertEquals(1, after.proposals.size)
        assertNull(after.chatFault)
        assertEquals("", after.chatInputText)
        // v1.0.8: headline là kết quả của LedgerQueryEngine (không phải lời AI); dòng đầu nói rõ số liệu chỉ gồm bản ghi đã lưu.
        assertFalse(after.chatAnswer!!.headline.contains("Mình tách"))
        assertEquals(com.timewallet.mvp.domain.usecase.WallyTurnPolicy.MIXED_LEAD_LINE, after.chatAnswer!!.lines.first())
        assertEquals(QueryMetric.UNTIMED, after.lastQueryContext!!.metric)
        assertFalse(after.isAnalyzingChat)
    }

    @Test
    fun `reducer - an error keeps the input and the existing drafts and creates no card`() {
        val existing = EditableProposalDraft.fromProposal(mixedOutcome().proposals.single())
        val before = TimeWalletUiState(chatInputText = "Học 45p", proposals = listOf(existing), chatAnswer = null)
        val failed = ask(Net(500 to "", 500 to ""), "Học 45p")
        assertEquals(AiFault.SERVER, failed.fault)
        val after = WallyChatReducer.applyResult(before, "Học 45p", failed, listOf(existing), null, compute)
        assertEquals("Học 45p", after.chatInputText)
        assertEquals(listOf(existing), after.proposals)
        assertNull(after.chatAnswer)
        assertEquals(AiFault.SERVER, after.chatFault)
        assertEquals(failed.retryCount, 1)
    }

    @Test
    fun `reducer - numbers are recomputed from the CURRENT ledger when it changed after the early computation`() {
        val outcome = mixedOutcome()
        val drafts = outcome.proposals.map { EditableProposalDraft.fromProposal(it) }
        val r1 = rec("learn", 30, emptyList())
        val oldRecords = listOf(r1)
        val stale = WallyChatReducer.Precomputed(compute(oldRecords, null), oldRecords, null)
        assertEquals(1800L, stale.answers.single().figures.untimedSeconds)

        // Cùng danh sách: dùng số tính trước
        val same = WallyChatReducer.applyResult(TimeWalletUiState(records = oldRecords), "q", outcome, drafts, stale) { _, _ -> error("không được tính lại") }
        assertTrue(same.chatAnswer!!.lines.any { it.contains("30") })

        // THÊM một bản ghi trong lúc chờ: phải ra số mới
        val r2 = rec("work", 60, oldRecords)
        val added = WallyChatReducer.applyResult(TimeWalletUiState(records = oldRecords + r2), "q", outcome, drafts, stale, compute)
        assertNotEquals(same.chatAnswer, added.chatAnswer)
        assertTrue(added.chatAnswer!!.lines.joinToString().contains("1h 30") || added.chatAnswer!!.lines.joinToString().contains("1h30") || added.chatAnswer!!.lines.joinToString().contains("90"))

        // XÓA bản ghi: số giảm lại
        val deleted = WallyChatReducer.applyResult(TimeWalletUiState(records = listOf(r2)), "q", outcome, drafts, stale, compute)
        assertTrue(deleted.chatAnswer!!.lines.joinToString().let { it.contains("1h") })
        assertNotEquals(added.chatAnswer, deleted.chatAnswer)
    }

    @Test
    fun `fresh numbers after add edit and delete - the engine is never served from a cache`() {
        fun total(recs: List<LedgerRecord>) = compute(recs, null).single().figures.untimedSeconds
        val a = rec("learn", 30, emptyList())
        assertEquals(1800L, total(listOf(a)))
        val b = rec("work", 45, listOf(a))
        assertEquals(4500L, total(listOf(a, b)))                       // thêm
        val bEdited = b.copy(durationSeconds = 60 * 60L)
        assertEquals(5400L, total(listOf(a, bEdited)))                 // sửa
        assertEquals(1800L, total(listOf(a)))                          // xóa
        assertEquals(0L, total(emptyList()))
    }

    // ---- BatchConfirm: đường ghi duy nhất, dùng chung với ViewModel ----
    private fun confirm(dao: InMemoryLedgerDao, drafts: List<EditableProposalDraft>, timer: (() -> ActiveTimerSession?) = { null }) = runBlocking {
        BatchConfirm.run(dao, drafts.map { it.toBatchInput() }, timer(), timer, nowMs, zone) { nowMs }
    }

    @Test
    fun `confirm path - nothing is written before confirmation, duplicates and replays are not written twice, equal activities stay separate`() {
        val dao = InMemoryLedgerDao()
        val outcome = ask(
            Net(200 to body("""{"proposals":[${item("Đọc sách", "learn", 30)},${item("Đọc sách", "learn", 30)}]}""")),
            "Đọc sách 30p, đọc sách 30p"
        )
        val drafts = outcome.proposals.map { EditableProposalDraft.fromProposal(it) }
        assertEquals(0, runBlocking { dao.getAllRecordsSnapshot().size })
        val first = confirm(dao, drafts) as ConfirmResult.Committed
        assertEquals(2, first.savedCount)
        val replay = confirm(dao, drafts) as ConfirmResult.Committed
        assertEquals(0, replay.savedCount)
        assertEquals(2, replay.skippedDuplicates)
        assertEquals(2, runBlocking { dao.getAllRecordsSnapshot().size })
    }

    @Test
    fun `confirm path - invalid and conflicting batches write nothing`() {
        val dao = InMemoryLedgerDao()
        val bad = EditableProposalDraft.fromProposal(
            mixedOutcome().proposals.single()
        ).copy(durationMinutesText = "0", categoryKey = "learn")
        assertTrue(confirm(dao, listOf(bad)) is ConfirmResult.Invalid)
        assertEquals(0, runBlocking { dao.getAllRecordsSnapshot().size })

        // Timer chạy trùng khoảng giờ của đề xuất có mốc giờ: bị chặn, không ghi
        val range = LedgerValidator.parseTimeRangeToEpochMillis(today.toString(), "17:00", "18:30", zone)!!
        val timed = EditableProposalDraft.fromProposal(mixedOutcome().proposals.single()).copy(startLocal = "17:00", endLocal = "18:30", durationMinutesText = "90", hasInitialTimeEvidence = true)
        val timer = ActiveTimerSession("Đang làm", "work", range.first + 60_000L, null, zone.id)
        val result = confirm(dao, listOf(timed)) { timer }
        assertTrue("timer đang chạy chặn ghi trùng: $result", result is ConfirmResult.Invalid || result is ConfirmResult.Aborted)
        assertEquals(0, runBlocking { dao.getAllRecordsSnapshot().size })
    }

    // ---- xóa nguyên tử: bằng chứng tĩnh (Room thật: NOT_TESTED trên thiết bị) ----
    @Test
    fun `record deletion and clear-all run inside a Room transaction`() {
        val vm = File("src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt").readText()
        val del = vm.substring(vm.indexOf("fun deleteRecord("), vm.indexOf("fun clearAllData()"))
        assertTrue(del.contains("database.withTransaction {"))
        val tx = del.substring(del.indexOf("withTransaction {"))
        assertTrue(tx.indexOf("ledgerDao.deleteById") in 0 until tx.indexOf("deleteActivityNoteByRecordId"))
        val clear = vm.substring(vm.indexOf("fun clearAllData()"), vm.indexOf("fun loadSampleData()"))
        assertTrue(clear.contains("database.withTransaction {") && clear.contains("deleteAll()") && clear.contains("deleteAllActivityNotes()"))
        // không còn lệnh xóa nằm ngoài giao dịch
        assertFalse(Regex("""\n\s+ledgerDao\.deleteById""").containsMatchIn(del.substringBefore("withTransaction")))
    }
}
