package com.timewallet.mvp

import com.timewallet.mvp.data.local.AiPreferences
import com.timewallet.mvp.data.local.ApiKeyVault
import com.timewallet.mvp.data.local.TimerAndSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WALLY F6 giả thuyết 1: Keystore ném ngoại lệ khi nạp/lưu cấu hình AI. Lệnh nạp chạy trong `init` của ViewModel,
 * nên ngoại lệ chưa bắt = app dừng ở mọi lần mở. Chỉ dùng API cũ để chạy được cả trước và sau khi sửa (RED/GREEN).
 */
class WallyKeystoreFaultTest {

    private class ThrowingVault : ApiKeyVault {
        override fun encrypt(plaintext: String): String = throw java.security.ProviderException("keystore unavailable (synthetic)")
        override fun decrypt(ciphertextPayload: String): String? = throw java.security.ProviderException("keystore unavailable (synthetic)")
    }

    private val syntheticKey = "sk-synthetic-wally-test-key"

    @Test
    fun `legacy plaintext migration with a failing keystore does not throw and leaves no plaintext behind`() {
        val prefs = InMemorySharedPreferences()
        prefs.edit()
            .putBoolean("ai_opt_in_enabled", true)
            .putString(TimerAndSettingsStore.KEY_AI_BASE_URL, "https://api.foza.ai/v1")
            .putString(TimerAndSettingsStore.KEY_AI_API_KEY_LEGACY, syntheticKey)
            .commit()
        val store = TimerAndSettingsStore(prefs, ThrowingVault())

        val loaded = store.loadAiPreferences()   // trước khi sửa: ném ProviderException => crash lúc khởi động

        assertEquals("", loaded.apiKey)
        assertFalse(loaded.optInEnabled)
        assertNull("không được để lại khóa dạng plaintext", prefs.getString(TimerAndSettingsStore.KEY_AI_API_KEY_LEGACY, null))
    }

    @Test
    fun `saving the key with a failing keystore reports failure instead of throwing`() {
        val prefs = InMemorySharedPreferences()
        val store = TimerAndSettingsStore(prefs, ThrowingVault())

        val ok = store.saveAiPreferences(
            AiPreferences(optInEnabled = true, apiKey = syntheticKey, baseUrl = "https://api.foza.ai/v1")
        )

        assertFalse(ok)
        prefs.all.values.forEach { assertFalse("không lưu khóa dạng rõ", it.toString().contains(syntheticKey)) }
    }

    @Test
    fun `AiPreferences toString never prints the key`() {
        val text = AiPreferences(optInEnabled = true, apiKey = syntheticKey).toString()
        assertFalse(text.contains(syntheticKey))
        assertTrue(text.contains("AiPreferences"))
    }

    @Test
    fun `undecryptable stored key does not throw and yields an empty key`() {
        val prefs = InMemorySharedPreferences()
        prefs.edit()
            .putString(TimerAndSettingsStore.KEY_AI_API_KEY_ENCRYPTED, "enc:v1:AAAA:BBBB")
            .putString(TimerAndSettingsStore.KEY_AI_TRUSTED_HOST, "api.foza.ai")
            .putBoolean("ai_opt_in_enabled", true)
            .commit()
        val loaded = TimerAndSettingsStore(prefs, ThrowingVault()).loadAiPreferences()
        assertEquals("", loaded.apiKey)
    }
}
