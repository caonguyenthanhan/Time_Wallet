package com.timewallet.mvp

import com.timewallet.mvp.data.local.AiPreferences
import com.timewallet.mvp.data.local.TimerAndSettingsStore
import com.timewallet.mvp.data.local.TimerCommit
import com.timewallet.mvp.data.local.TimerCommitResult
import com.timewallet.mvp.domain.model.ActivityProposal
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.ReplyGuard
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * WALLY v1.0.6 — vòng sửa sau bản đọc độc lập (Reviewer phụ, đọc mã nguồn): các đường lách ReplyGuard, bấm đúp "Kết thúc & lưu",
 * và ghi đè bản mã khi khóa tạm thời không đọc được. Mỗi test ghi lại một kịch bản cụ thể.
 */
class V106ReviewRound2Test {

    private val today = LocalDate.of(2026, 11, 13)
    private fun prop(minutes: Int, start: String? = null, end: String? = null) = ActivityProposal(
        id = "p$minutes", rawInput = "x", title = "Học", category = "learn", durationSeconds = minutes * 60L, dateLocal = today.toString(),
        startLocal = start, endLocal = end, timeEvidence = if (start != null) "explicit_range" else "duration_only",
        missingFields = emptyList(), needsConfirmation = true, isFuturePlanned = false
    )

    private fun cleaned(reply: String, proposals: List<ActivityProposal> = emptyList(), hasQueries: Boolean = false) =
        ReplyGuard.sanitize(reply, "x", proposals, hasQueries).first

    // ---------------------------------------------------------------- ReplyGuard: lời khẳng định đã ghi
    @Test
    fun `claims hidden behind punctuation quotes or paraphrase are still dropped`() {
        listOf(
            "\"Đã lưu\" hoạt động học", "(đã ghi) nhé", "Wally:đã lưu", "Mình đã giúp bạn lưu hoạt động",
            "Hoạt động đã được Wally ghi lại", "Mình ghi lại rồi nhé", "Xong! Hoạt động đã nằm trong sổ cái",
            "I've added it to your log", "Entry saved", "Mình vừa ghi nhận giúp bạn"
        ).forEach { assertNull("phải bị bỏ: \"$it\"", cleaned(it)) }
    }

    @Test
    fun `decomposed unicode cannot be used to hide a claim`() {
        val nfd = java.text.Normalizer.normalize("Mình đã lưu rồi", java.text.Normalizer.Form.NFD)
        assertNull(cleaned(nfd))
    }

    @Test
    fun `negated promises and harmless sentences are not mistaken for a claim`() {
        assertNotNull(cleaned("Mình sẽ không tự động xác nhận thay bạn."))
        assertNotNull(cleaned("Bạn xác nhận để mình ghi nhé."))
        assertNotNull(cleaned("Mình chưa ghi gì cả."))
        assertNotNull(cleaned("When you're ready, bấm Xác nhận nhé."))
    }

    // ---------------------------------------------------------------- ReplyGuard: số liệu
    @Test
    fun `number words missing from the first list, punctuation, clock forms and english words are figures`() {
        listOf(
            "Bạn học mười lăm phút", "Hai mươi mốt phút thôi", "Tuần này (hai giờ) thôi", "Khoảng 2:30 thôi", "Bạn học 1g30 hôm nay",
            "Mới 45 mins thôi", "Chỉ 3 hrs", "You studied two hours", "Khoảng one hour"
        ).forEach {
            assertNull("số liệu phải bị bỏ: \"$it\"", cleaned(it))
            assertNull("(có query) \"$it\"", cleaned(it, hasQueries = true))
        }
    }

    @Test
    fun `a count of zero is not backed when there are no proposals`() {
        assertNull(cleaned("Hôm nay bạn có 0 buổi học"))
    }

    @Test
    fun `the pending proposal may be repeated by duration or by its clock range`() {
        val p = listOf(prop(60, "09:00", "10:00"))
        assertNotNull(cleaned("Mình thấy học 60 phút, bạn xác nhận nhé.", p))
        assertNotNull(cleaned("Học từ 9 giờ đến 10 giờ, bạn xác nhận nhé.", p))
        assertNotNull(cleaned("Học 09:00 đến 10:00 nhé", p))
        assertNull("giờ khác thì không", cleaned("Học từ 11 giờ nhé", p))
        assertNotNull(cleaned("Mình tách 1 hoạt động, bạn xác nhận nhé.", p))
        // 2h30 = 150 phút: không được bị đọc thành "2h" rồi lọt qua vì có đề xuất 120 phút
        assertNull(cleaned("Học 2h30 nhé", listOf(prop(120))))
        assertNotNull(cleaned("Học 2h30 nhé", listOf(prop(150))))
    }

    // ---------------------------------------------------------------- bấm đúp "Kết thúc & lưu"
    @Test
    fun `two concurrent stops of the same session end as one save and one already-committed, never as a false overlap`() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val start = LocalDateTime.of(2026, 11, 10, 8, 0).atZone(zone).toInstant().toEpochMilli()
        val end = start + 26 * 3_600_000L
        val dao = InMemoryLedgerDao()
        val results = runBlocking {
            listOf(
                async { TimerCommit.run(dao, "Phiên dài", TimeCategory.WORK.key, start, end, zone) },
                async { TimerCommit.run(dao, "Phiên dài", TimeCategory.WORK.key, start, end, zone) }
            ).awaitAll()
        }
        assertEquals(1, results.count { it is TimerCommitResult.Saved })
        assertTrue("lượt còn lại phải là AlreadyCommitted: $results", results.any { it == TimerCommitResult.AlreadyCommitted })
        assertFalse(results.any { it is TimerCommitResult.Overlap })
        assertEquals(2, runBlocking { dao.getAllRecordsSnapshot() }.size)
    }

    /** DAO mô phỏng cuộc đua: lần đầu hỏi khóa chống ghi trùng thì "chưa thấy" (lượt kia chưa kịp commit), sau đó trả đúng. */
    private class RacyDao(private val inner: InMemoryLedgerDao) : com.timewallet.mvp.data.local.LedgerDao by inner {
        var lieOnce = true
        override suspend fun countByIdempotencyKey(idempotencyKey: String): Int {
            if (lieOnce && idempotencyKey.startsWith("timer_")) { lieOnce = false; return 0 }
            return inner.countByIdempotencyKey(idempotencyKey)
        }
    }

    @Test
    fun `a stop that passed the early committed check but collides with the other stop's rows reports AlreadyCommitted`() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val start = LocalDateTime.of(2026, 11, 10, 8, 0).atZone(zone).toInstant().toEpochMilli()
        val end = start + 3_600_000L
        val inner = InMemoryLedgerDao()
        assertTrue(runBlocking { TimerCommit.run(inner, "x", TimeCategory.WORK.key, start, end, zone) } is TimerCommitResult.Saved)
        val racy = RacyDao(inner)
        val second = runBlocking { TimerCommit.run(racy, "x", TimeCategory.WORK.key, start, end, zone) }
        assertEquals("không được báo trùng giờ giả với chính phiên vừa lưu", TimerCommitResult.AlreadyCommitted, second)
        assertEquals(1, runBlocking { inner.getAllRecordsSnapshot() }.size)
    }

    @Test
    fun `a second stop after the first finished is AlreadyCommitted even when the later end overlaps its own rows`() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val start = LocalDateTime.of(2026, 11, 10, 8, 0).atZone(zone).toInstant().toEpochMilli()
        val dao = InMemoryLedgerDao()
        assertTrue(runBlocking { TimerCommit.run(dao, "x", TimeCategory.WORK.key, start, start + 3_600_000L, zone) } is TimerCommitResult.Saved)
        assertEquals(TimerCommitResult.AlreadyCommitted, runBlocking { TimerCommit.run(dao, "x", TimeCategory.WORK.key, start, start + 7_200_000L, zone) })
    }

    @Test
    fun `the ViewModel guards Stop against double taps and refreshes derived stats after clearing the session`() {
        val vm = java.io.File("src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt").readText()
        assertTrue(vm.contains("timerStopGuard.tryAcquire()") && vm.contains("timerStopGuard.release()"))
        val finish = vm.substring(vm.indexOf("private fun finishTimerAfterCommit()"), vm.indexOf("fun cancelRunningTimer()"))
        assertTrue(finish.contains("recomputeStats("))
    }

    // ---------------------------------------------------------------- khóa tạm thời không đọc được rồi người dùng lưu
    private val keyA = "sk-synthetic-key-a"

    @Test
    fun `saving with a blank key field while the stored key is unreadable keeps the stored ciphertext`() {
        val prefs = InMemorySharedPreferences()
        val good = InMemoryApiKeyVault()
        val store = TimerAndSettingsStore(prefs, good)
        assertTrue(store.saveAiPreferences(AiPreferences(optInEnabled = true, apiKey = keyA, baseUrl = "https://api.foza.ai/v1", modelName = "m1")))
        val cipherBefore = prefs.getString(TimerAndSettingsStore.KEY_AI_API_KEY_ENCRYPTED, null)
        val hostBefore = prefs.getString(TimerAndSettingsStore.KEY_AI_TRUSTED_HOST, null)

        // Keystore tạm thời hỏng: nạp ra keyUnavailable, ô khóa trống; người dùng chỉ đổi tên model rồi Lưu.
        val broken = object : com.timewallet.mvp.data.local.ApiKeyVault {
            override fun encrypt(plaintext: String): String = throw java.security.ProviderException("transient")
            override fun decrypt(ciphertextPayload: String): String? = null
        }
        val loaded = TimerAndSettingsStore(prefs, broken).loadAiPreferences()
        assertTrue(loaded.keyUnavailable)
        assertTrue(TimerAndSettingsStore(prefs, broken).saveAiPreferences(
            AiPreferences(optInEnabled = true, apiKey = "", baseUrl = "https://api.foza.ai/v1", modelName = "m2"),
            keepStoredKeyIfBlank = loaded.keyUnavailable
        ))
        assertEquals("bản mã cũ được giữ", cipherBefore, prefs.getString(TimerAndSettingsStore.KEY_AI_API_KEY_ENCRYPTED, null))
        assertEquals(hostBefore, prefs.getString(TimerAndSettingsStore.KEY_AI_TRUSTED_HOST, null))

        // Keystore hồi phục: khóa cũ dùng lại được; model mới được lưu; AI không tự bật khi lúc lưu không có khóa.
        val recovered = TimerAndSettingsStore(prefs, good).loadAiPreferences()
        assertEquals(keyA, recovered.apiKey)
        assertEquals("m2", recovered.modelName)
        assertFalse("không tự bật AI khi lúc lưu không có khóa", recovered.optInEnabled)
    }

    @Test
    fun `an unreadable key is never kept across an origin change`() {
        val prefs = InMemorySharedPreferences()
        val good = InMemoryApiKeyVault()
        val store = TimerAndSettingsStore(prefs, good)
        assertTrue(store.saveAiPreferences(AiPreferences(optInEnabled = true, apiKey = keyA, baseUrl = "https://api.foza.ai/v1")))
        assertTrue(store.saveAiPreferences(
            AiPreferences(optInEnabled = true, apiKey = "", baseUrl = "https://other.example/v1"),
            keepStoredKeyIfBlank = true
        ))
        assertEquals("đổi origin: bản mã cũ phải bị xóa, không được gắn sang origin mới", "", prefs.getString(TimerAndSettingsStore.KEY_AI_API_KEY_ENCRYPTED, null))
        assertEquals("other.example", prefs.getString(TimerAndSettingsStore.KEY_AI_TRUSTED_HOST, null))
    }

    @Test
    fun `without the keep flag a blank key still clears the stored key (explicit removal)`() {
        val prefs = InMemorySharedPreferences()
        val store = TimerAndSettingsStore(prefs, InMemoryApiKeyVault())
        assertTrue(store.saveAiPreferences(AiPreferences(optInEnabled = true, apiKey = keyA, baseUrl = "https://api.foza.ai/v1")))
        assertTrue(store.saveAiPreferences(AiPreferences(optInEnabled = false, apiKey = "", baseUrl = "https://api.foza.ai/v1")))
        assertEquals("", store.loadAiPreferences().apiKey)
    }
}
