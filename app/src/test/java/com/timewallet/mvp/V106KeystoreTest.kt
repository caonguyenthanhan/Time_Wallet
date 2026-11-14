package com.timewallet.mvp

import com.timewallet.mvp.data.local.AesGcmKeystoreVault
import com.timewallet.mvp.data.local.AiPreferences
import com.timewallet.mvp.data.local.ApiKeyVault
import com.timewallet.mvp.data.local.TimerAndSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * WALLY v1.0.6 — F-V105-05: không còn khóa Keystore dự phòng trong RAM ở production; mã hóa lỗi => báo CHƯA lưu và giữ nguyên
 * cấu hình hợp lệ trước đó. Đây chỉ là bằng chứng ở mức JVM; đường Keystore thật trên thiết bị vẫn là NOT_TESTED.
 */
class V106KeystoreTest {

    private val key1 = "sk-synthetic-first-key"
    private val key2 = "sk-synthetic-second-key"

    /** Giữ nguyên kho thật nhưng cho phép "làm hỏng" riêng bước mã hóa. */
    private class SwitchableVault(private val inner: ApiKeyVault) : ApiKeyVault {
        var failEncrypt = false
        override fun encrypt(plaintext: String): String =
            if (failEncrypt) throw java.security.ProviderException("keystore unavailable (synthetic)") else inner.encrypt(plaintext)
        override fun decrypt(ciphertextPayload: String): String? = inner.decrypt(ciphertextPayload)
    }

    @Test
    fun `production vault without AndroidKeyStore refuses to encrypt instead of silently using a RAM key`() {
        // JVM không có AndroidKeyStore; bản v1.0.5 ở đây vẫn "mã hóa" thành công bằng khóa tạm trong RAM.
        val vault = AesGcmKeystoreVault()
        val failure = try {
            vault.encrypt("sk-synthetic")
            null
        } catch (e: Exception) {
            e
        }
        assertTrue("phải ném lỗi khi Keystore không dùng được: $failure", failure != null)
    }

    @Test
    fun `production vault without AndroidKeyStore cannot decrypt anything (no RAM key to decrypt with)`() {
        val payload = InMemoryApiKeyVault().encrypt("sk-synthetic")
        assertNull(AesGcmKeystoreVault().decrypt(payload))
    }

    @Test
    fun `production vault source has no volatile fallback key`() {
        val src = File("src/main/java/com/timewallet/mvp/data/local/TimerAndSettingsStore.kt").readText()
        val vault = src.substring(src.indexOf("class AesGcmKeystoreVault"), src.indexOf("class TimerAndSettingsStore"))
        assertFalse(vault.contains("volatileFallbackKey"))
        assertFalse(vault.contains("fallbackSecretKeyOverride"))
        assertFalse("không sinh khóa AES trong RAM", vault.contains("KeyGenerator.getInstance(\"AES\")"))
        assertFalse(vault.contains("SecureRandom"))
    }

    @Test
    fun `a failed key update reports failure and leaves the previous valid configuration byte for byte`() {
        val prefs = InMemorySharedPreferences()
        val vault = SwitchableVault(InMemoryApiKeyVault())
        val store = TimerAndSettingsStore(prefs, vault)
        assertTrue(store.saveAiPreferences(AiPreferences(optInEnabled = true, apiKey = key1, baseUrl = "https://api.foza.ai/v1", modelName = "m1")))
        val before = prefs.all.toMap()

        vault.failEncrypt = true
        val ok = store.saveAiPreferences(AiPreferences(optInEnabled = true, apiKey = key2, baseUrl = "https://api.foza.ai/v1", modelName = "m2"))

        assertFalse("phải báo chưa lưu", ok)
        assertEquals("không được ghi gì khi mã hóa lỗi", before, prefs.all.toMap())
        prefs.all.values.forEach { assertFalse("không có khóa dạng rõ", it.toString().contains(key1) || it.toString().contains(key2)) }

        vault.failEncrypt = false
        val loaded = store.loadAiPreferences()
        assertEquals("cấu hình cũ còn dùng được", key1, loaded.apiKey)
        assertEquals("m1", loaded.modelName)
        assertTrue(loaded.optInEnabled)
        assertFalse(loaded.keyUnavailable)
    }

    @Test
    fun `a failed first-time save writes no encrypted payload and no plaintext`() {
        val prefs = InMemorySharedPreferences()
        val vault = SwitchableVault(InMemoryApiKeyVault()).also { it.failEncrypt = true }
        val ok = TimerAndSettingsStore(prefs, vault).saveAiPreferences(AiPreferences(optInEnabled = true, apiKey = key1))
        assertFalse(ok)
        assertTrue("không có gì được ghi: ${prefs.all}", prefs.all.isEmpty())
    }

    @Test
    fun `test vault is only reachable through dependency injection and is absent from main sources`() {
        val mainHits = File("src/main").walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("InMemoryApiKeyVault") }.toList()
        assertTrue("InMemoryApiKeyVault không được có trong APK: $mainHits", mainHits.isEmpty())
        // Constructor production của store phải dùng kho Keystore thật, không phải kho giả.
        val src = File("src/main/java/com/timewallet/mvp/data/local/TimerAndSettingsStore.kt").readText()
        assertTrue(src.contains("private val defaultVault: ApiKeyVault = AesGcmKeystoreVault()"))
    }
}
