package com.timewallet.mvp

import com.timewallet.mvp.data.diagnostics.CrashReport
import com.timewallet.mvp.data.diagnostics.CrashRecorder
import com.timewallet.mvp.data.local.TimerAndSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/** WALLY WP1: báo cáo lỗi đã che nội dung + cờ khóa không dùng được. */
class WallyDiagnosticsTest {

    @Test
    fun `crash report keeps class and frames but never the exception message`() {
        val secret = "sk-secret-123 Hôm nay tôi học 2 tiếng"
        val error = IllegalStateException(secret, RuntimeException("inner $secret"))
        val report = CrashReport.redact(error, Instant.parse("2026-11-13T00:00:00Z"))
        assertTrue(report.contains("exception=java.lang.IllegalStateException"))
        assertTrue(report.contains("caused_by=java.lang.RuntimeException"))
        assertTrue(report.contains("WallyDiagnosticsTest"))
        assertFalse(report.contains("sk-secret"))
        assertFalse(report.contains("Hôm nay"))
    }

    @Test
    fun `recorder writes reads and clears the last report`() {
        val f = File.createTempFile("wally_crash", ".txt")
        try {
            CrashRecorder.install(f)
            CrashRecorder.record(IllegalArgumentException("x"))
            assertTrue(CrashRecorder.readLast()!!.contains("IllegalArgumentException"))
            CrashRecorder.clear()
            assertNull(CrashRecorder.readLast())
        } finally {
            f.delete()
        }
    }

    @Test
    fun `an undecryptable stored key raises keyUnavailable and a failed migration does too`() {
        val bad = object : com.timewallet.mvp.data.local.ApiKeyVault {
            override fun encrypt(plaintext: String): String = throw IllegalStateException("x")
            override fun decrypt(ciphertextPayload: String): String? = null
        }
        val prefs = InMemorySharedPreferences()
        prefs.edit()
            .putString(TimerAndSettingsStore.KEY_AI_API_KEY_ENCRYPTED, "enc:v1:AAAA:BBBB")
            .putString(TimerAndSettingsStore.KEY_AI_TRUSTED_HOST, "api.foza.ai")
            .commit()
        assertTrue(TimerAndSettingsStore(prefs, bad).loadAiPreferences().keyUnavailable)

        val legacy = InMemorySharedPreferences()
        legacy.edit().putString(TimerAndSettingsStore.KEY_AI_API_KEY_LEGACY, "sk-x").commit()
        val loaded = TimerAndSettingsStore(legacy, bad).loadAiPreferences()
        assertTrue(loaded.keyUnavailable)
        assertEquals("", loaded.apiKey)
    }

    @Test
    fun `healthy vault round trip leaves keyUnavailable false`() {
        val prefs = InMemorySharedPreferences()
        val store = TimerAndSettingsStore(prefs, InMemoryApiKeyVault())
        assertTrue(store.saveAiPreferences(com.timewallet.mvp.data.local.AiPreferences(optInEnabled = true, apiKey = "sk-ok")))
        val loaded = store.loadAiPreferences()
        assertEquals("sk-ok", loaded.apiKey)
        assertFalse(loaded.keyUnavailable)
    }
}
