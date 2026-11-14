package com.timewallet.mvp.data.local

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.ActivityReviewNote
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.DailyReviewRecord
import com.timewallet.mvp.domain.model.LedgerRecord
import org.json.JSONObject
import java.net.URI
import java.security.KeyStore
import java.time.Instant
import java.util.Base64
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AiPreferences(
    val optInEnabled: Boolean = false,
    val baseUrl: String = DEFAULT_AI_BASE_URL,
    val apiKey: String = "",
    val modelName: String = DEFAULT_AI_MODEL,
    val trustedHost: String = DEFAULT_AI_HOST,
    /** true: khóa đã lưu không đọc/ghi được (Keystore lỗi hoặc khóa đã mất); người dùng cần nhập lại khóa. */
    val keyUnavailable: Boolean = false
) {
    // WALLY: data class mặc định in cả khóa API; che khóa để không rò qua log/UI state.
    override fun toString(): String =
        "AiPreferences(optIn=$optInEnabled, baseUrl=$baseUrl, apiKey=${if (apiKey.isEmpty()) "<empty>" else "<redacted>"}, " +
            "model=$modelName, trustedHost=$trustedHost, keyUnavailable=$keyUnavailable)"

    companion object {
        const val DEFAULT_AI_BASE_URL = "https://api.foza.ai/v1"
        const val DEFAULT_AI_HOST = "api.foza.ai"
        const val DEFAULT_AI_MODEL = "cinb/gemini-3.8-flash"

        /**
         * Chỉ chấp nhận URL có scheme HTTPS hợp lệ, không chứa userInfo (@), và trả về host viết thường.
         */
        fun extractValidatedHttpsHost(rawBaseUrl: String): String? {
            val trimmed = rawBaseUrl.trim()
            if (trimmed.isEmpty()) return null
            return try {
                val uri = URI(trimmed)
                val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
                if (scheme != "https") return null
                if (!uri.userInfo.isNullOrEmpty()) return null
                // WALLY: endpoint được nối thêm "/chat/completions"; query/fragment sẽ làm sai URL hoặc giấu tham số.
                if (uri.rawQuery != null || uri.rawFragment != null) return null
                val host = uri.host?.trim()?.lowercase(Locale.US)?.trimEnd('.') ?: return null
                if (host.isEmpty() || host.contains(' ')) return null
                // WALLY: ORIGIN = scheme + host + cổng. Khóa API gắn với origin này; cổng mặc định (443) để ngầm định
                // nên giá trị đã lưu ở các bản trước (chỉ có host) vẫn khớp; cổng khác thì ghi rõ "host:cổng".
                originString(host, uri.port)
            } catch (_: Exception) {
                null
            }
        }

        /** Dạng chuẩn của origin https: "host" (cổng 443 hoặc không nêu) hoặc "host:cổng". Cổng ngoài 1..65535 là không hợp lệ. */
        private fun originString(host: String, port: Int): String? = when {
            port == -1 || port == 443 -> host
            port in 1..65535 -> "$host:$port"
            else -> null
        }

        /**
         * Origin của URL thực sự sắp được mở, cùng định dạng với [extractValidatedHttpsHost]. Dùng để chặn sai lệch giữa
         * bộ đọc URI (lúc xác thực) và bộ đọc URL (lúc kết nối) trước khi gửi khóa.
         */
        fun originOf(url: java.net.URL): String? {
            if (!url.protocol.equals("https", ignoreCase = true)) return null
            if (!url.userInfo.isNullOrEmpty()) return null
            val host = url.host?.trim()?.lowercase(Locale.US)?.trimEnd('.') ?: return null
            if (host.isEmpty() || host.contains(' ')) return null
            return originString(host, url.port)
        }
    }
}

/**
 * Giao diện mã hóa/giải mã khóa API bằng AES-GCM (Patch v1.0.1 — F1).
 */
interface ApiKeyVault {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertextPayload: String): String?
}

/** Keystore không dùng được (không có provider, bị khóa, hỏng khóa...). KHÔNG bao giờ được thay bằng khóa trong RAM. */
class KeyVaultUnavailableException(cause: Throwable?) : Exception("KEYSTORE_UNAVAILABLE", cause)

/**
 * AES-GCM 256-bit (thẻ xác thực 128-bit) bằng AndroidKeyStore.
 *
 * WALLY v1.0.6 (F-V105-05): KHÔNG còn khóa dự phòng trong bộ nhớ. Bản v1.0.5 âm thầm dùng khóa AES tạm trong RAM khi Keystore
 * lỗi: khóa API được "mã hóa" bằng một khóa biến mất khi tiến trình chết, trong lúc ứng dụng vẫn báo đã lưu. Nay nếu Keystore
 * không dùng được thì [encrypt] ném [KeyVaultUnavailableException] (người gọi báo "chưa lưu" và giữ nguyên cấu hình cũ), còn
 * [decrypt] trả null (người dùng được yêu cầu nhập lại khóa). Kho giả cho test chỉ được đưa vào qua DI (kho trong bộ nhớ
 * nằm trong source test, không có trong APK).
 */
class AesGcmKeystoreVault : ApiKeyVault {

    private fun resolveSecretKey(): SecretKey {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
            val existing = (ks.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            if (existing != null) {
                existing
            } else {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE_PROVIDER
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (t: Throwable) {
            throw KeyVaultUnavailableException(t)
        }
    }

    override fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        val secretKey = resolveSecretKey()
        val payload = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey) // khóa Keystore tự sinh IV ngẫu nhiên
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val ivB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(iv)
            val ctB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(encryptedBytes)
            "$PAYLOAD_PREFIX$ivB64:$ctB64"
        } catch (t: Throwable) {
            throw KeyVaultUnavailableException(t)
        }
        return payload
    }

    override fun decrypt(ciphertextPayload: String): String? {
        val trimmed = ciphertextPayload.trim()
        if (trimmed.isEmpty()) return ""
        if (!trimmed.startsWith(PAYLOAD_PREFIX)) return null
        val body = trimmed.removePrefix(PAYLOAD_PREFIX)
        val parts = body.split(':')
        if (parts.size != 2) return null
        return try {
            val iv = Base64.getUrlDecoder().decode(parts[0])
            val ct = Base64.getUrlDecoder().decode(parts[1])
            if (iv.isEmpty() || ct.isEmpty()) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, resolveSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "time_wallet_api_key_master_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        const val PAYLOAD_PREFIX = "enc:v1:"
    }
}

class TimerAndSettingsStore(
    private val prefs: SharedPreferences,
    private val apiKeyVault: ApiKeyVault = defaultVault
) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        defaultVault
    )

    fun loadActiveTimer(): ActiveTimerSession? {
        val active = prefs.getBoolean(KEY_TIMER_ACTIVE, false)
        if (!active) return null
        val title = prefs.getString(KEY_TIMER_TITLE, null) ?: return null
        val category = prefs.getString(KEY_TIMER_CATEGORY, "work") ?: "work"
        val startedAt = prefs.getLong(KEY_TIMER_START_EPOCH, -1L)
        if (startedAt <= 0L) return null
        val elapsedAtStart = prefs.getLong(KEY_TIMER_START_ELAPSED, -1L).takeIf { it >= 0L }
        val tz = prefs.getString(KEY_TIMER_TIMEZONE, "Asia/Ho_Chi_Minh") ?: "Asia/Ho_Chi_Minh"
        val targetSecs = prefs.getLong(KEY_TIMER_TARGET_SECONDS, -1L).takeIf { it > 0L }
        val startedBootCount = if (prefs.contains(KEY_TIMER_BOOT_COUNT)) {
            prefs.getInt(KEY_TIMER_BOOT_COUNT, -1).takeIf { it >= 0 }
        } else {
            null
        }

        return ActiveTimerSession(
            title = title,
            categoryKey = category,
            startedAtEpochMs = startedAt,
            startedElapsedRealtimeMs = elapsedAtStart,
            timezone = tz,
            targetDurationSeconds = targetSecs,
            startedBootCount = startedBootCount
        )
    }

    /**
     * Lưu đồng bộ bằng commit() để đảm bảo trạng thái Timer (kèm startedBootCount) được ghi xuống ổ cứng
     * ngay lập tức trước khi tiến trình bị hệ điều hành thu hồi hoặc thiết bị reboot.
     */
    fun saveActiveTimer(session: ActiveTimerSession?) {
        prefs.edit().apply {
            if (session == null) {
                putBoolean(KEY_TIMER_ACTIVE, false)
                remove(KEY_TIMER_TITLE)
                remove(KEY_TIMER_CATEGORY)
                remove(KEY_TIMER_START_EPOCH)
                remove(KEY_TIMER_START_ELAPSED)
                remove(KEY_TIMER_TIMEZONE)
                remove(KEY_TIMER_TARGET_SECONDS)
                remove(KEY_TIMER_BOOT_COUNT)
            } else {
                putBoolean(KEY_TIMER_ACTIVE, true)
                putString(KEY_TIMER_TITLE, session.title)
                putString(KEY_TIMER_CATEGORY, session.categoryKey)
                putLong(KEY_TIMER_START_EPOCH, session.startedAtEpochMs)
                if (session.startedElapsedRealtimeMs != null) {
                    putLong(KEY_TIMER_START_ELAPSED, session.startedElapsedRealtimeMs)
                } else {
                    remove(KEY_TIMER_START_ELAPSED)
                }
                if (session.targetDurationSeconds != null) {
                    putLong(KEY_TIMER_TARGET_SECONDS, session.targetDurationSeconds)
                } else {
                    remove(KEY_TIMER_TARGET_SECONDS)
                }
                if (session.startedBootCount != null && session.startedBootCount >= 0) {
                    putInt(KEY_TIMER_BOOT_COUNT, session.startedBootCount)
                } else {
                    remove(KEY_TIMER_BOOT_COUNT)
                }
                putString(KEY_TIMER_TIMEZONE, session.timezone)
            }
        }.commit()
    }

    /**
     * Đọc cấu hình AI và tự động migrate khóa API plaintext cũ (nếu có) sang AES-GCM ciphertext,
     * đồng thời xóa khóa plaintext khỏi SharedPreferences (Patch v1.0.1 — F1 & F2).
     */
    fun loadAiPreferences(): AiPreferences {
        val rawBaseUrl = prefs.getString(KEY_AI_BASE_URL, AiPreferences.DEFAULT_AI_BASE_URL)
            ?.trim()
            ?.ifEmpty { AiPreferences.DEFAULT_AI_BASE_URL }
            ?: AiPreferences.DEFAULT_AI_BASE_URL

        val validatedHost = AiPreferences.extractValidatedHttpsHost(rawBaseUrl)
        val effectiveBaseUrl = if (validatedHost != null) rawBaseUrl else AiPreferences.DEFAULT_AI_BASE_URL
        val effectiveHost = validatedHost ?: AiPreferences.DEFAULT_AI_HOST

        val encryptedPayload = prefs.getString(KEY_AI_API_KEY_ENCRYPTED, null)
        val legacyPlaintext = prefs.getString(KEY_AI_API_KEY_LEGACY, null)

        val resolvedApiKey: String
        val resolvedTrustedHost: String
        var keyUnavailable = false

        if (!encryptedPayload.isNullOrBlank()) {
            val decrypted = try {
                apiKeyVault.decrypt(encryptedPayload)
            } catch (_: Exception) {
                null
            }
            keyUnavailable = decrypted == null
            resolvedApiKey = decrypted ?: ""
            resolvedTrustedHost = prefs.getString(KEY_AI_TRUSTED_HOST, null)
                ?.trim()
                ?.lowercase(Locale.US)
                .orEmpty()
            if (legacyPlaintext != null) {
                prefs.edit().remove(KEY_AI_API_KEY_LEGACY).commit()
            }
        } else if (!legacyPlaintext.isNullOrBlank()) {
            // Migrate an toàn từ plaintext cũ sang AES-GCM và xóa ngay bản ghi plaintext
            val cleanKey = legacyPlaintext.trim()
            val ciphertext = try {
                apiKeyVault.encrypt(cleanKey)
            } catch (_: Exception) {
                null // WALLY F6: Keystore lỗi không được làm app dừng lúc khởi động
            }
            if (ciphertext != null) {
                prefs.edit()
                    .putString(KEY_AI_API_KEY_ENCRYPTED, ciphertext)
                    .putString(KEY_AI_TRUSTED_HOST, effectiveHost)
                    .remove(KEY_AI_API_KEY_LEGACY)
                    .commit()
                resolvedApiKey = cleanKey
            } else {
                // Không giữ plaintext: xóa khóa cũ, yêu cầu nhập lại. Sổ cái và nhập thủ công không bị ảnh hưởng.
                prefs.edit().remove(KEY_AI_API_KEY_LEGACY).commit()
                keyUnavailable = true
                resolvedApiKey = ""
            }
            resolvedTrustedHost = effectiveHost
        } else {
            if (legacyPlaintext != null) {
                prefs.edit().remove(KEY_AI_API_KEY_LEGACY).commit()
            }
            resolvedApiKey = ""
            resolvedTrustedHost = prefs.getString(KEY_AI_TRUSTED_HOST, effectiveHost)
                ?.trim()
                ?.lowercase(Locale.US)
                ?: effectiveHost
        }

        // Nếu host trong baseUrl không khớp với trustedHost đã gắn với khóa API, không trả khóa cho host lạ
        val hostMatchesCredential = resolvedTrustedHost.isNotEmpty() &&
            effectiveHost.equals(resolvedTrustedHost, ignoreCase = true)

        return AiPreferences(
            optInEnabled = prefs.getBoolean(KEY_AI_OPT_IN, false) && validatedHost != null && hostMatchesCredential &&
                resolvedApiKey.isNotEmpty(),
            baseUrl = effectiveBaseUrl,
            apiKey = if (hostMatchesCredential) resolvedApiKey else "",
            modelName = prefs.getString(KEY_AI_MODEL, AiPreferences.DEFAULT_AI_MODEL)
                ?.trim()
                ?.ifEmpty { AiPreferences.DEFAULT_AI_MODEL }
                ?: AiPreferences.DEFAULT_AI_MODEL,
            trustedHost = if (hostMatchesCredential) resolvedTrustedHost else effectiveHost,
            keyUnavailable = keyUnavailable
        )
    }

    /**
     * Lưu cấu hình AI:
     * - Bắt buộc HTTPS hợp lệ.
     * - Mã hóa API key bằng AES-GCM trước khi ghi xuống SharedPreferences.
     * - Nếu đổi sang host mới mà chưa xác nhận cấu hình lại (`hostChangeConfirmed = false`),
     *   tự động xóa token cũ để không bao giờ gửi nhầm token sang host mới.
     */
    fun saveAiPreferences(
        aiPreferences: AiPreferences,
        hostChangeConfirmed: Boolean = true,
        /**
         * true khi khóa đã lưu hiện KHÔNG đọc được (`keyUnavailable`) và ô khóa để trống: đừng ghi đè bản mã cũ bằng chuỗi rỗng
         * (có thể đọc lại được sau khi Keystore hồi phục). Chỉ giữ khi vẫn cùng origin; đổi origin thì xóa như trước.
         */
        keepStoredKeyIfBlank: Boolean = false
    ): Boolean {
        val cleanUrl = aiPreferences.baseUrl.trim().ifEmpty { AiPreferences.DEFAULT_AI_BASE_URL }
        val newHost = AiPreferences.extractValidatedHttpsHost(cleanUrl) ?: return false

        val previousTrustedHost = prefs.getString(KEY_AI_TRUSTED_HOST, null)
            ?.trim()
            ?.lowercase(Locale.US)

        val isChangingHost = !previousTrustedHost.isNullOrEmpty() &&
            !previousTrustedHost.equals(newHost, ignoreCase = true)

        val effectiveKey = if (isChangingHost && !hostChangeConfirmed) {
            ""
        } else {
            aiPreferences.apiKey.trim()
        }

        val encryptedPayload = if (effectiveKey.isNotEmpty()) {
            try {
                apiKeyVault.encrypt(effectiveKey)
            } catch (_: Exception) {
                return false // Không ghi gì (kể cả plaintext) khi không mã hóa được
            }
        } else {
            ""
        }

        val preserveStoredCiphertext = effectiveKey.isEmpty() && keepStoredKeyIfBlank && !isChangingHost &&
            !prefs.getString(KEY_AI_API_KEY_ENCRYPTED, null).isNullOrBlank()

        val editor = prefs.edit()
            .putBoolean(KEY_AI_OPT_IN, aiPreferences.optInEnabled && effectiveKey.isNotEmpty())
            .putString(KEY_AI_BASE_URL, cleanUrl)
            .putString(
                KEY_AI_MODEL,
                aiPreferences.modelName.trim().ifEmpty { AiPreferences.DEFAULT_AI_MODEL }
            )
            .remove(KEY_AI_API_KEY_LEGACY)
        if (!preserveStoredCiphertext) {
            editor.putString(KEY_AI_API_KEY_ENCRYPTED, encryptedPayload).putString(KEY_AI_TRUSTED_HOST, newHost)
        }
        editor.commit()
        return true
    }

    companion object {
        const val PREFS_NAME = "time_wallet_prefs_v1"
        private const val KEY_TIMER_ACTIVE = "timer_active"
        private const val KEY_TIMER_TITLE = "timer_title"
        private const val KEY_TIMER_CATEGORY = "timer_category"
        private const val KEY_TIMER_START_EPOCH = "timer_start_epoch"
        private const val KEY_TIMER_START_ELAPSED = "timer_start_elapsed"
        private const val KEY_TIMER_TIMEZONE = "timer_timezone"
        private const val KEY_TIMER_TARGET_SECONDS = "timer_target_seconds"
        const val KEY_TIMER_BOOT_COUNT = "timer_boot_count"

        private const val KEY_AI_OPT_IN = "ai_opt_in_enabled"
        const val KEY_AI_BASE_URL = "ai_base_url"
        const val KEY_AI_API_KEY_LEGACY = "ai_gemini_api_key"
        const val KEY_AI_API_KEY_ENCRYPTED = "ai_api_key_aes_gcm_v1"
        const val KEY_AI_TRUSTED_HOST = "ai_trusted_host"
        private const val KEY_AI_MODEL = "ai_gemini_model"

        private val defaultVault: ApiKeyVault = AesGcmKeystoreVault()

        /**
         * Đọc BOOT_COUNT của thiết bị từ Settings.Global để phân biệt same-boot với reboot.
         */
        fun readDeviceBootCount(context: Context): Int? {
            return try {
                val count = Settings.Global.getInt(
                    context.contentResolver,
                    Settings.Global.BOOT_COUNT,
                    -1
                )
                count.takeIf { it >= 0 }
            } catch (_: Throwable) {
                null
            }
        }
    }
}

object LedgerJsonExporter {

    /**
     * Xuất toàn bộ dữ liệu sổ cái, ngân sách thời gian, ghi chú kết quả hoạt động và Daily Review ra JSON chuẩn.
     * Sử dụng serializer chuẩn `org.json.JSONObject.quote` (RFC 8259) để xử lý an toàn mọi control character
     * U+0000..U+001F (\b, \f, \n, \r, \t, \u00xx) và ký tự Unicode tiếng Việt (Patch v1.0.1 — F4).
     */
    fun exportToJsonString(
        records: List<LedgerRecord>,
        activeTimer: ActiveTimerSession?,
        exportedAtIso: String = Instant.now().toString(),
        budgets: List<CategoryTimeBudget> = emptyList(),
        dailyReviews: List<DailyReviewRecord> = emptyList(),
        activityNotes: List<ActivityReviewNote> = emptyList()
    ): String {
        val notesByRecordId = activityNotes.associateBy { it.recordId }
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"schemaVersion\": 1,\n")
        sb.append("  \"exportedAt\": ").append(serializeJsonString(exportedAtIso)).append(",\n")
        if (activeTimer != null) {
            sb.append("  \"activeTimer\": {\n")
            sb.append("    \"title\": ").append(serializeJsonString(activeTimer.title)).append(",\n")
            sb.append("    \"category\": ").append(serializeJsonString(activeTimer.categoryKey)).append(",\n")
            sb.append("    \"startedAt\": ").append(activeTimer.startedAtEpochMs).append(",\n")
            sb.append("    \"timezone\": ").append(serializeJsonString(activeTimer.timezone)).append("\n")
            sb.append("  },\n")
        } else {
            sb.append("  \"activeTimer\": null,\n")
        }

        // Danh sách bản ghi Ledger kèm ghi chú kết quả / Feynman (nếu có)
        sb.append("  \"items\": [\n")
        records.forEachIndexed { index, r ->
            val note = notesByRecordId[r.id]
            sb.append("    {\n")
            sb.append("      \"id\": ").append(serializeJsonString(r.id)).append(",\n")
            sb.append("      \"source\": ").append(serializeJsonString(r.source)).append(",\n")
            sb.append("      \"layer\": ").append(serializeJsonString(r.layer.name)).append(",\n")
            sb.append("      \"createdAt\": ").append(r.createdAt).append(",\n")
            sb.append("      \"userConfirmedAt\": ").append(r.userConfirmedAt).append(",\n")
            sb.append("      \"category\": ").append(serializeJsonString(r.category.key)).append(",\n")
            sb.append("      \"title\": ").append(serializeJsonString(r.title)).append(",\n")
            sb.append("      \"date\": ").append(serializeJsonString(r.date)).append(",\n")
            sb.append("      \"durationSeconds\": ").append(r.durationSeconds).append(",\n")
            sb.append("      \"startedAt\": ").append(r.startedAt?.toString() ?: "null").append(",\n")
            sb.append("      \"endedAt\": ").append(r.endedAt?.toString() ?: "null").append(",\n")
            sb.append("      \"timezone\": ").append(serializeJsonString(r.timezone)).append(",\n")
            sb.append("      \"status\": ").append(serializeJsonString(r.status)).append(",\n")
            sb.append("      \"outcomeNote\": ").append(note?.outcomeNote?.let { serializeJsonString(it) } ?: "null").append(",\n")
            sb.append("      \"feynmanPrompt\": ").append(
                if (r.category.key == "learn") serializeJsonString("Bạn sẽ giải thích điều vừa học cho người chưa biết như thế nào?") else "null"
            ).append(",\n")
            sb.append("      \"feynmanExplanation\": ").append(note?.feynmanExplanation?.let { serializeJsonString(it) } ?: "null").append("\n")
            sb.append("    }").append(if (index < records.lastIndex) "," else "").append("\n")
        }
        sb.append("  ],\n")

        // Danh sách Ngân sách Thời gian (Time Budget)
        sb.append("  \"budgets\": [\n")
        budgets.forEachIndexed { index, b ->
            sb.append("    {\n")
            sb.append("      \"category\": ").append(serializeJsonString(b.category.key)).append(",\n")
            sb.append("      \"period\": ").append(serializeJsonString(b.period.storageKey)).append(",\n")
            sb.append("      \"targetSeconds\": ").append(b.targetSeconds).append(",\n")
            sb.append("      \"isPriority\": ").append(b.isPriority).append(",\n")
            sb.append("      \"updatedAt\": ").append(b.updatedAt).append("\n")
            sb.append("    }").append(if (index < budgets.lastIndex) "," else "").append("\n")
        }
        sb.append("  ],\n")

        // Danh sách Daily Review đã lưu
        sb.append("  \"dailyReviews\": [\n")
        dailyReviews.forEachIndexed { index, dr ->
            sb.append("    {\n")
            sb.append("      \"dateLocal\": ").append(serializeJsonString(dr.dateLocal)).append(",\n")
            sb.append("      \"summaryText\": ").append(serializeJsonString(dr.summaryText)).append(",\n")
            sb.append("      \"question1\": ").append(serializeJsonString(dr.question1)).append(",\n")
            sb.append("      \"question2\": ").append(serializeJsonString(dr.question2)).append(",\n")
            sb.append("      \"answer1\": ").append(serializeJsonString(dr.answer1)).append(",\n")
            sb.append("      \"answer2\": ").append(serializeJsonString(dr.answer2)).append(",\n")
            sb.append("      \"generalNote\": ").append(serializeJsonString(dr.generalNote)).append(",\n")
            sb.append("      \"updatedAt\": ").append(dr.updatedAt).append("\n")
            sb.append("    }").append(if (index < dailyReviews.lastIndex) "," else "").append("\n")
        }
        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }

    /**
     * Sử dụng serializer chuẩn `org.json.JSONObject.quote` để đảm bảo mọi control character U+0000..U+001F
     * và ký tự đặc biệt đều tạo ra chuỗi JSON hợp lệ theo RFC 8259.
     */
    internal fun serializeJsonString(value: String): String {
        val stdQuoted = JSONObject.quote(value)
        if (!stdQuoted.isNullOrEmpty() && stdQuoted.startsWith("\"") && stdQuoted.endsWith("\"")) {
            return stdQuoted
        }
        // Fallback dự phòng trong trường hợp stub android.jar trả về null
        val sb = StringBuilder(value.length + 8)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append(String.format(Locale.US, "\\u%04x", ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
