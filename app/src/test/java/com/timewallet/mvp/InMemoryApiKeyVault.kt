package com.timewallet.mvp

import com.timewallet.mvp.data.local.AesGcmKeystoreVault
import com.timewallet.mvp.data.local.ApiKeyVault
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Kho khóa GIẢ chỉ dành cho test JVM (WALLY v1.0.6, F-V105-05). Nằm trong source test nên KHÔNG có trong APK:
 * sản phẩm không còn khóa dự phòng trong RAM. Dùng cùng định dạng payload để các test mã hóa/di cư vẫn kiểm được hợp đồng.
 */
class InMemoryApiKeyVault(private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()) : ApiKeyVault {

    override fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val enc = Base64.getUrlEncoder().withoutPadding()
        return "${AesGcmKeystoreVault.PAYLOAD_PREFIX}${enc.encodeToString(iv)}:${enc.encodeToString(ct)}"
    }

    override fun decrypt(ciphertextPayload: String): String? {
        val trimmed = ciphertextPayload.trim()
        if (trimmed.isEmpty()) return ""
        if (!trimmed.startsWith(AesGcmKeystoreVault.PAYLOAD_PREFIX)) return null
        val parts = trimmed.removePrefix(AesGcmKeystoreVault.PAYLOAD_PREFIX).split(':')
        if (parts.size != 2) return null
        return try {
            val iv = Base64.getUrlDecoder().decode(parts[0])
            val ct = Base64.getUrlDecoder().decode(parts[1])
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }
}
