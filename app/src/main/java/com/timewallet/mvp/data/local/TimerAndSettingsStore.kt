package com.timewallet.mvp.data.local

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.PausedTimerContinuation
import com.timewallet.mvp.domain.model.ActivityReviewNote
import com.timewallet.mvp.domain.model.AiReadPermissions
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.CustomizationSettings
import com.timewallet.mvp.domain.model.DailyReviewRecord
import com.timewallet.mvp.domain.model.FontScalePreference
import com.timewallet.mvp.domain.model.Habit
import com.timewallet.mvp.domain.model.HabitLog
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.LocalCategoryRule
import com.timewallet.mvp.domain.model.Project
import com.timewallet.mvp.domain.model.TagAssignmentProposal
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.ThemeModePreference
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.ApiKeyHygiene
import com.timewallet.mvp.domain.usecase.ChatHistoryEntry
import com.timewallet.mvp.domain.usecase.PendingProposalContext
import com.timewallet.mvp.domain.usecase.PersistedChatConversation
import com.timewallet.mvp.domain.usecase.QueryContext
import com.timewallet.mvp.domain.usecase.QueryMetric
import com.timewallet.mvp.domain.usecase.QueryPeriod
import com.timewallet.mvp.domain.usecase.QueryPeriodKind
import com.timewallet.mvp.domain.usecase.UpdatePreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.KeyStore
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Chế độ tốc độ/model Wally cho hai model Claude hiện hành (v2.1):
 * - Pro (mặc định): Claude Sonnet 4.6 (`cinb/claude-sonnet-4-6`) — ưu tiên chất lượng & suy luận phức tạp.
 * - Flash (tùy chọn): Claude Haiku 4.5 (`hoang/claude-haiku-4.5`) — ưu tiên tốc độ phản hồi cho tác vụ nhẹ.
 */
enum class AiProviderPreset(
    val storageKey: String,
    val labelVi: String,
    val baseUrl: String,
    val suggestedModels: List<String>
) {
    FOZA(
        storageKey = "foza",
        labelVi = "Foza",
        baseUrl = AiPreferences.DEFAULT_AI_BASE_URL,
        suggestedModels = listOf(AiPreferences.DEFAULT_AI_MODEL, AiPreferences.FLASH_AI_MODEL)
    ),
    OPENAI(
        storageKey = "openai",
        labelVi = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        suggestedModels = listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini")
    ),
    GEMINI(
        storageKey = "gemini",
        labelVi = "Gemini",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        suggestedModels = listOf("gemini-2.5-flash", "gemini-2.5-pro")
    ),
    OPENROUTER(
        storageKey = "openrouter",
        labelVi = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        suggestedModels = listOf("anthropic/claude-sonnet-4", "openai/gpt-4o-mini", "google/gemini-2.5-flash")
    ),
    GROQ(
        storageKey = "groq",
        labelVi = "Groq",
        baseUrl = "https://api.groq.com/openai/v1",
        suggestedModels = listOf("llama-3.3-70b-versatile", "qwen/qwen3-32b")
    ),
    CUSTOM(
        storageKey = "custom",
        labelVi = "HTTPS khác",
        baseUrl = "",
        suggestedModels = emptyList()
    );

    companion object {
        fun detectFromBaseUrl(rawUrl: String?): AiProviderPreset {
            val host = AiPreferences.extractValidatedHttpsHost(rawUrl.orEmpty())?.substringBefore(':')
                ?: return CUSTOM
            return entries.firstOrNull { preset ->
                preset.baseUrl.isNotEmpty() &&
                    AiPreferences.extractValidatedHttpsHost(preset.baseUrl)?.substringBefore(':') == host
            } ?: CUSTOM
        }
    }
}

enum class WallyModelPreset(
    val displayTitle: String,
    val shortLabel: String,
    val actualModelName: String,
    val modelId: String,
    val descriptionVi: String
) {
    PRO(
        displayTitle = "Wally Pro",
        shortLabel = "Pro",
        actualModelName = "Claude Sonnet 4.6",
        modelId = AiPreferences.DEFAULT_AI_MODEL,
        descriptionVi = "Mặc định · Suy luận sâu, mục tiêu & ngữ cảnh phức tạp"
    ),
    FLASH(
        displayTitle = "Wally Flash",
        shortLabel = "Flash",
        actualModelName = "Claude Haiku 4.5",
        modelId = AiPreferences.FLASH_AI_MODEL,
        descriptionVi = "Tùy chọn nhanh · Trò chuyện nhẹ & phản hồi tốc độ cao"
    );

    companion object {
        fun fromModelName(rawModel: String): WallyModelPreset? {
            val trimmed = rawModel.trim()
            return entries.firstOrNull { it.modelId.equals(trimmed, ignoreCase = true) }
        }
    }
}

data class AiPreferences(
    val optInEnabled: Boolean = false,
    val baseUrl: String = DEFAULT_AI_BASE_URL,
    val apiKey: String = "",
    val modelName: String = DEFAULT_AI_MODEL,
    val trustedHost: String = DEFAULT_AI_HOST,
    /** true: khóa đã lưu không đọc/ghi được (Keystore lỗi hoặc khóa đã mất); người dùng cần nhập lại khóa. */
    val keyUnavailable: Boolean = false
) {
    fun resolvedPreset(): WallyModelPreset? = WallyModelPreset.fromModelName(modelName)

    fun modeTitle(): String = resolvedPreset()?.displayTitle ?: "Wally Tùy chỉnh"

    fun actualModelDisplay(): String =
        resolvedPreset()?.actualModelName ?: modelName.trim().ifEmpty { DEFAULT_AI_MODEL }

    // WALLY: data class mặc định in cả khóa API; che khóa để không rò qua log/UI state.
    override fun toString(): String =
        "AiPreferences(optIn=$optInEnabled, baseUrl=$baseUrl, apiKey=${if (apiKey.isEmpty()) "<empty>" else "<redacted>"}, " +
            "model=$modelName, trustedHost=$trustedHost, keyUnavailable=$keyUnavailable)"

    companion object {
        const val DEFAULT_AI_BASE_URL = "https://api.foza.ai/v1"
        const val DEFAULT_AI_HOST = "api.foza.ai"
        const val DEFAULT_AI_MODEL = "cinb/claude-sonnet-4-6"
        const val FLASH_AI_MODEL = "hoang/claude-haiku-4.5"

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

/** Bản nháp đề xuất chờ xác nhận trong tab Wally (chỉ lưu UI draft cục bộ, KHÔNG phải bản ghi sổ cái). */
data class SavedProposalDraft(
    val id: String,
    val rawInput: String,
    val title: String,
    val categoryKey: String,
    val durationMinutesText: String,
    val dateLocal: String,
    val startLocal: String,
    val endLocal: String,
    val hasInitialTimeEvidence: Boolean,
    val isFuturePlanned: Boolean,
    val idempotencyKey: String,
    val tagId: String? = null,
    val isMarkedWaste: Boolean = false,
    val taskId: String? = null
)

/** Bản nháp form Nhập giờ thủ công (lưu cục bộ ngoài sổ cái, không tạo bản ghi và không ảnh hưởng thống kê). */
data class ManualEntryDraft(
    val sessionId: String,
    val categoryKey: String? = null,
    val hoursText: String = "",
    val minutesText: String = "",
    val dateLocal: String,
    val note: String = "",
    val modeName: String = "DURATION",
    val tagId: String? = null,
    val tagQuery: String = "",
    val startText: String = "",
    val endText: String = "",
    val endIsNow: Boolean = false,
    val saveAsPlanned: Boolean = false,
    val updatedAtEpochMs: Long = 0L,
    val isMarkedWaste: Boolean = false,
    val taskId: String? = null
) {
    fun hasUserData(todayLocal: String): Boolean =
        categoryKey != null ||
            tagId != null ||
            taskId != null ||
            tagQuery.isNotBlank() ||
            hoursText.isNotBlank() ||
            minutesText.isNotBlank() ||
            startText.isNotBlank() ||
            endText.isNotBlank() ||
            endIsNow ||
            saveAsPlanned ||
            isMarkedWaste ||
            note.isNotBlank() ||
            dateLocal != todayLocal

    fun isPersistable(todayLocal: String): Boolean =
        hasUserData(todayLocal) || modeName != "DURATION"
}

/** Bản nháp trước khi bấm Bắt đầu ở tab Đồng hồ. */
data class TimerInputDraft(
    val title: String = "",
    val categoryKey: String = "work",
    val tagId: String? = null
) {
    fun hasUserData(): Boolean =
        title.isNotBlank() || tagId != null || categoryKey != "work"
}

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
            startedBootCount = startedBootCount,
            tagId = prefs.getString(KEY_TIMER_TAG_ID, null)?.takeIf { it.isNotBlank() },
            taskId = prefs.getString(KEY_TIMER_TASK_ID, null)?.takeIf { it.isNotBlank() },
            habitId = prefs.getString(KEY_TIMER_HABIT_ID, null)?.takeIf { it.isNotBlank() }
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
                remove(KEY_TIMER_TAG_ID)
                remove(KEY_TIMER_TASK_ID)
                remove(KEY_TIMER_HABIT_ID)
            } else {
                putBoolean(KEY_TIMER_ACTIVE, true)
                if (session.tagId != null) putString(KEY_TIMER_TAG_ID, session.tagId) else remove(KEY_TIMER_TAG_ID)
                if (session.taskId != null) putString(KEY_TIMER_TASK_ID, session.taskId) else remove(KEY_TIMER_TASK_ID)
                if (session.habitId != null) putString(KEY_TIMER_HABIT_ID, session.habitId) else remove(KEY_TIMER_HABIT_ID)
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
            // v1.0.12: khóa đã lưu có thể chứa ký tự rác do dán (khoảng trắng/gạch lạ): chuẩn hóa khi đọc để không gửi đi một khóa lẫn ký tự rác.
            resolvedApiKey = ApiKeyHygiene.normalize(decrypted ?: "")
            resolvedTrustedHost = prefs.getString(KEY_AI_TRUSTED_HOST, null)
                ?.trim()
                ?.lowercase(Locale.US)
                .orEmpty()
            if (legacyPlaintext != null) {
                prefs.edit().remove(KEY_AI_API_KEY_LEGACY).commit()
            }
        } else if (!legacyPlaintext.isNullOrBlank()) {
            // Migrate an toàn từ plaintext cũ sang AES-GCM và xóa ngay bản ghi plaintext
            val cleanKey = ApiKeyHygiene.normalize(legacyPlaintext)
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
            ApiKeyHygiene.normalize(aiPreferences.apiKey)
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

    /** Đổi nhanh model/chế độ Wally (Pro/Flash) mà không chạm vào ciphertext khóa API hay origin đã gắn. */
    fun saveAiModelName(modelName: String): Boolean =
        prefs.edit()
            .putString(
                KEY_AI_MODEL,
                modelName.trim().ifEmpty { AiPreferences.DEFAULT_AI_MODEL }
            )
            .commit()

    fun saveManualEntryDraft(draft: ManualEntryDraft?, immediate: Boolean = false) {
        val edit = prefs.edit()
        if (draft == null) {
            edit.remove(KEY_MANUAL_DRAFT_JSON)
        } else {
            val json = JSONObject().apply {
                put("sessionId", draft.sessionId)
                put("categoryKey", draft.categoryKey ?: JSONObject.NULL)
                put("hoursText", draft.hoursText)
                put("minutesText", draft.minutesText)
                put("dateLocal", draft.dateLocal)
                put("note", draft.note)
                put("modeName", draft.modeName)
                put("tagId", draft.tagId ?: JSONObject.NULL)
                put("tagQuery", draft.tagQuery)
                put("startText", draft.startText)
                put("endText", draft.endText)
                put("endIsNow", draft.endIsNow)
                put("saveAsPlanned", draft.saveAsPlanned)
                put("isMarkedWaste", draft.isMarkedWaste)
                put("taskId", draft.taskId ?: JSONObject.NULL)
                put("updatedAtEpochMs", draft.updatedAtEpochMs)
            }.toString()
            edit.putString(KEY_MANUAL_DRAFT_JSON, json)
        }
        if (immediate) edit.commit() else edit.apply()
    }

    fun loadManualEntryDraft(): ManualEntryDraft? {
        val raw = prefs.getString(KEY_MANUAL_DRAFT_JSON, null)?.trim() ?: return null
        if (raw.isEmpty()) return null
        return try {
            val obj = JSONObject(raw)
            val sessionId = obj.optString("sessionId", "").trim()
            val dateLocal = obj.optString("dateLocal", "").trim()
            if (sessionId.isEmpty() || dateLocal.isEmpty()) return null
            val catRaw = if (obj.isNull("categoryKey")) null else obj.optString("categoryKey", "").takeIf { it.isNotBlank() }
            val tagRaw = if (obj.isNull("tagId")) null else obj.optString("tagId", "").takeIf { it.isNotBlank() }
            val taskRaw = if (obj.isNull("taskId")) null else obj.optString("taskId", "").takeIf { it.isNotBlank() }
            ManualEntryDraft(
                sessionId = sessionId,
                categoryKey = catRaw,
                hoursText = obj.optString("hoursText", ""),
                minutesText = obj.optString("minutesText", ""),
                dateLocal = dateLocal,
                note = obj.optString("note", ""),
                modeName = obj.optString("modeName", "DURATION").let { if (it == "RANGE") "RANGE" else "DURATION" },
                tagId = tagRaw,
                tagQuery = obj.optString("tagQuery", ""),
                startText = obj.optString("startText", ""),
                endText = obj.optString("endText", ""),
                endIsNow = obj.optBoolean("endIsNow", false),
                saveAsPlanned = obj.optBoolean("saveAsPlanned", false),
                updatedAtEpochMs = obj.optLong("updatedAtEpochMs", 0L),
                isMarkedWaste = obj.optBoolean("isMarkedWaste", false),
                taskId = taskRaw
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clearManualEntryDraft(immediate: Boolean = true) {
        saveManualEntryDraft(null, immediate = immediate)
    }

    fun saveTimerInputDraft(draft: TimerInputDraft?, immediate: Boolean = false) {
        val edit = prefs.edit()
        if (draft == null || !draft.hasUserData()) {
            edit.remove(KEY_TIMER_DRAFT_TITLE)
                .remove(KEY_TIMER_DRAFT_CATEGORY)
                .remove(KEY_TIMER_DRAFT_TAG_ID)
        } else {
            edit.putString(KEY_TIMER_DRAFT_TITLE, draft.title)
                .putString(KEY_TIMER_DRAFT_CATEGORY, draft.categoryKey)
            if (draft.tagId != null) edit.putString(KEY_TIMER_DRAFT_TAG_ID, draft.tagId) else edit.remove(KEY_TIMER_DRAFT_TAG_ID)
        }
        if (immediate) edit.commit() else edit.apply()
    }

    fun loadTimerInputDraft(): TimerInputDraft {
        val title = prefs.getString(KEY_TIMER_DRAFT_TITLE, "") ?: ""
        val cat = prefs.getString(KEY_TIMER_DRAFT_CATEGORY, "work")?.takeIf { it.isNotBlank() } ?: "work"
        val tagId = prefs.getString(KEY_TIMER_DRAFT_TAG_ID, null)?.takeIf { it.isNotBlank() }
        return TimerInputDraft(title = title, categoryKey = cat, tagId = tagId)
    }

    fun saveChatInputDraft(text: String, immediate: Boolean = false) {
        val edit = prefs.edit()
        if (text.isBlank()) edit.remove(KEY_CHAT_INPUT_DRAFT) else edit.putString(KEY_CHAT_INPUT_DRAFT, text)
        if (immediate) edit.commit() else edit.apply()
    }

    fun loadChatInputDraft(): String = prefs.getString(KEY_CHAT_INPUT_DRAFT, "") ?: ""

    fun saveChatHistory(entries: List<ChatHistoryEntry>, immediate: Boolean = false) {
        val edit = prefs.edit()
        if (entries.isEmpty()) {
            edit.remove(KEY_CHAT_HISTORY_JSON)
        } else {
            val arr = JSONArray()
            entries.takeLast(MAX_CHAT_HISTORY_ENTRIES).forEach { e ->
                arr.put(
                    JSONObject().apply {
                        put("id", e.id)
                        put("isUser", e.isUser)
                        put("headline", e.headline)
                        put("lines", JSONArray(e.lines))
                        put("notes", JSONArray(e.notes))
                        put("timeText", e.timeText)
                        put("isClarification", e.isClarification)
                        put("label", e.label ?: JSONObject.NULL)
                    }
                )
            }
            edit.putString(KEY_CHAT_HISTORY_JSON, arr.toString())
        }
        if (immediate) edit.commit() else edit.apply()
    }

    fun loadChatHistory(): List<ChatHistoryEntry> {
        val raw = prefs.getString(KEY_CHAT_HISTORY_JSON, null)?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val linesArr = o.optJSONArray("lines") ?: JSONArray()
                    val notesArr = o.optJSONArray("notes") ?: JSONArray()
                    add(
                        ChatHistoryEntry(
                            id = o.optString("id", "ch_$i"),
                            isUser = o.optBoolean("isUser", false),
                            headline = o.optString("headline", ""),
                            lines = (0 until linesArr.length()).mapNotNull { idx -> linesArr.optString(idx).takeIf { it.isNotEmpty() } },
                            notes = (0 until notesArr.length()).mapNotNull { idx -> notesArr.optString(idx).takeIf { it.isNotEmpty() } },
                            timeText = o.optString("timeText", ""),
                            isClarification = o.optBoolean("isClarification", false),
                            label = if (o.isNull("label")) null else o.optString("label").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }.filter { it.headline.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun saveChatProposalsDraft(drafts: List<SavedProposalDraft>, immediate: Boolean = false) {
        val edit = prefs.edit()
        if (drafts.isEmpty()) {
            edit.remove(KEY_CHAT_PROPOSALS_DRAFT_JSON)
        } else {
            val arr = JSONArray()
            drafts.take(15).forEach { d ->
                arr.put(
                    JSONObject().apply {
                        put("id", d.id)
                        put("rawInput", d.rawInput)
                        put("title", d.title)
                        put("categoryKey", d.categoryKey)
                        put("durationMinutesText", d.durationMinutesText)
                        put("dateLocal", d.dateLocal)
                        put("startLocal", d.startLocal)
                        put("endLocal", d.endLocal)
                        put("hasInitialTimeEvidence", d.hasInitialTimeEvidence)
                        put("isFuturePlanned", d.isFuturePlanned)
                        put("idempotencyKey", d.idempotencyKey)
                        put("tagId", d.tagId ?: JSONObject.NULL)
                        put("isMarkedWaste", d.isMarkedWaste)
                        put("taskId", d.taskId ?: JSONObject.NULL)
                    }
                )
            }
            edit.putString(KEY_CHAT_PROPOSALS_DRAFT_JSON, arr.toString())
        }
        if (immediate) edit.commit() else edit.apply()
    }

    fun loadChatProposalsDraft(): List<SavedProposalDraft> {
        val raw = prefs.getString(KEY_CHAT_PROPOSALS_DRAFT_JSON, null)?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id", "").takeIf { it.isNotBlank() } ?: continue
                    add(
                        SavedProposalDraft(
                            id = id,
                            rawInput = o.optString("rawInput", ""),
                            title = o.optString("title", "Hoạt động"),
                            categoryKey = o.optString("categoryKey", "other"),
                            durationMinutesText = o.optString("durationMinutesText", "30"),
                            dateLocal = o.optString("dateLocal", ""),
                            startLocal = o.optString("startLocal", ""),
                            endLocal = o.optString("endLocal", ""),
                            hasInitialTimeEvidence = o.optBoolean("hasInitialTimeEvidence", false),
                            isFuturePlanned = o.optBoolean("isFuturePlanned", false),
                            idempotencyKey = o.optString("idempotencyKey", id),
                            tagId = if (o.isNull("tagId")) null else o.optString("tagId").takeIf { it.isNotBlank() },
                            isMarkedWaste = o.optBoolean("isMarkedWaste", false),
                            taskId = if (o.isNull("taskId")) null else o.optString("taskId", "").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveChatConversationSnapshot(snapshot: PersistedChatConversation?, immediate: Boolean = false) {
        val edit = prefs.edit()
        if (snapshot == null) {
            edit.remove("chat_conversation_snapshot_v1")
        } else {
            val root = JSONObject().apply {
                put("isAdvisoryTopic", snapshot.isAdvisoryTopic)
                put("topicLabel", snapshot.topicLabel ?: JSONObject.NULL)
                put("focusCategoryKey", snapshot.focusCategoryKey ?: JSONObject.NULL)
                put("focusTagId", snapshot.focusTagId ?: JSONObject.NULL)
                put("targetDateLocal", snapshot.targetDateLocal ?: JSONObject.NULL)
                put("updatedAtEpochMs", snapshot.updatedAtEpochMs)
                put("permissionsFingerprint", snapshot.permissionsFingerprint)
                put("clarificationQuestion", snapshot.clarificationQuestion ?: JSONObject.NULL)
                put("clarificationSuggestions", JSONArray(snapshot.clarificationSuggestions))
                put(
                    "pendingProposal",
                    snapshot.pendingProposal?.let { p ->
                        JSONObject().apply {
                            put("draftId", p.draftId ?: JSONObject.NULL)
                            put("title", p.title)
                            put("categoryKey", p.categoryKey)
                            put("tagId", p.tagId ?: JSONObject.NULL)
                            put("tagName", p.tagName ?: JSONObject.NULL)
                            put("dateLocal", p.dateLocal)
                            put("startLocal", p.startLocal ?: JSONObject.NULL)
                            put("endLocal", p.endLocal ?: JSONObject.NULL)
                            put("durationMinutes", p.durationMinutes ?: JSONObject.NULL)
                            put("isFuturePlanned", p.isFuturePlanned)
                            put("taskId", p.taskId ?: JSONObject.NULL)
                            put("awaitingFields", JSONArray(p.awaitingFields.toList()))
                            put("explicitTagCleared", p.explicitTagCleared)
                        }
                    } ?: JSONObject.NULL
                )
                put(
                    "lastQuery",
                    snapshot.lastQuery?.let { q ->
                        JSONObject().apply {
                            put("metric", q.metric.name)
                            put("start", q.period.start.toString())
                            put("end", q.period.end.toString())
                            put("label", q.period.label)
                            put("kind", q.period.kind.name)
                            put("categoryKey", q.category?.key ?: JSONObject.NULL)
                            put("tagId", q.tagId ?: JSONObject.NULL)
                            put("tagName", q.tagName ?: JSONObject.NULL)
                        }
                    } ?: JSONObject.NULL
                )
            }
            edit.putString("chat_conversation_snapshot_v1", root.toString())
        }
        if (immediate) edit.commit() else edit.apply()
    }

    fun loadChatConversationSnapshot(): PersistedChatConversation? {
        val raw = prefs.getString("chat_conversation_snapshot_v1", null)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val o = JSONObject(raw)
            fun strOrNull(obj: JSONObject, key: String): String? =
                if (obj.isNull(key)) null else obj.optString(key, "").takeIf { it.isNotBlank() }

            val pendingObj = if (o.isNull("pendingProposal")) null else o.optJSONObject("pendingProposal")
            val pending = pendingObj?.let { p ->
                val awArr = p.optJSONArray("awaitingFields") ?: JSONArray()
                val awaiting = (0 until awArr.length()).mapNotNull { i -> awArr.optString(i).takeIf { it.isNotBlank() } }.toSet()
                PendingProposalContext(
                    draftId = strOrNull(p, "draftId"),
                    title = p.optString("title", "").ifBlank { return@let null },
                    categoryKey = p.optString("categoryKey", "other"),
                    tagId = strOrNull(p, "tagId"),
                    tagName = strOrNull(p, "tagName"),
                    dateLocal = p.optString("dateLocal", ""),
                    startLocal = strOrNull(p, "startLocal"),
                    endLocal = strOrNull(p, "endLocal"),
                    durationMinutes = if (p.isNull("durationMinutes")) null else p.optInt("durationMinutes").takeIf { it > 0 },
                    isFuturePlanned = p.optBoolean("isFuturePlanned", true),
                    taskId = strOrNull(p, "taskId"),
                    awaitingFields = awaiting,
                    explicitTagCleared = p.optBoolean("explicitTagCleared", false)
                )
            }
            val lqObj = if (o.isNull("lastQuery")) null else o.optJSONObject("lastQuery")
            val lastQuery = lqObj?.let { q ->
                val metric = runCatching { QueryMetric.valueOf(q.optString("metric", "")) }.getOrNull() ?: return@let null
                val start = runCatching { LocalDate.parse(q.optString("start", "")) }.getOrNull() ?: return@let null
                val end = runCatching { LocalDate.parse(q.optString("end", "")) }.getOrNull() ?: return@let null
                val kind = runCatching { QueryPeriodKind.valueOf(q.optString("kind", "")) }.getOrDefault(QueryPeriodKind.DAY)
                QueryContext(
                    metric = metric,
                    period = QueryPeriod(start, end, q.optString("label", ""), kind),
                    category = strOrNull(q, "categoryKey")?.let { TimeCategory.fromKeyOrNull(it) },
                    tagId = strOrNull(q, "tagId"),
                    tagName = strOrNull(q, "tagName")
                )
            }
            val sugArr = o.optJSONArray("clarificationSuggestions") ?: JSONArray()
            val sugs = (0 until sugArr.length()).mapNotNull { i -> sugArr.optString(i).takeIf { it.isNotBlank() } }
            PersistedChatConversation(
                pendingProposal = pending,
                lastQuery = lastQuery,
                focusTagId = strOrNull(o, "focusTagId"),
                topicLabel = strOrNull(o, "topicLabel"),
                focusCategoryKey = strOrNull(o, "focusCategoryKey"),
                targetDateLocal = strOrNull(o, "targetDateLocal"),
                isAdvisoryTopic = o.optBoolean("isAdvisoryTopic", false),
                updatedAtEpochMs = o.optLong("updatedAtEpochMs", 0L),
                permissionsFingerprint = o.optString("permissionsFingerprint", ""),
                clarificationQuestion = strOrNull(o, "clarificationQuestion"),
                clarificationSuggestions = sugs
            )
        }.getOrNull()
    }

    fun savePendingTagAssignments(list: List<TagAssignmentProposal>, immediate: Boolean = false) {
        val edit = prefs.edit()
        if (list.isEmpty()) {
            edit.remove("chat_pending_tag_assignments_v1")
        } else {
            val arr = JSONArray()
            list.take(6).forEach { a ->
                arr.put(
                    JSONObject().apply {
                        put("id", a.id)
                        put("recordId", a.recordId)
                        put("recordTitle", a.recordTitle)
                        put("recordDateLocal", a.recordDateLocal)
                        put("recordTimeLabel", a.recordTimeLabel)
                        put("recordDurationSeconds", a.recordDurationSeconds)
                        put("previousTagId", a.previousTagId ?: JSONObject.NULL)
                        put("previousTagName", a.previousTagName ?: JSONObject.NULL)
                        put("previousCategoryKey", a.previousCategoryKey)
                        put("newTagId", a.newTagId ?: JSONObject.NULL)
                        put("newTagName", a.newTagName ?: JSONObject.NULL)
                        put("newCategoryKey", a.newCategoryKey)
                        put("affectedPeriodStart", a.affectedPeriodStart)
                        put("affectedPeriodEnd", a.affectedPeriodEnd)
                        put("affectedPeriodLabel", a.affectedPeriodLabel)
                    }
                )
            }
            edit.putString("chat_pending_tag_assignments_v1", arr.toString())
        }
        if (immediate) edit.commit() else edit.apply()
    }

    fun loadPendingTagAssignments(): List<TagAssignmentProposal> {
        val raw = prefs.getString("chat_pending_tag_assignments_v1", null)?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id", "").takeIf { it.isNotBlank() } ?: continue
                    val recId = o.optString("recordId", "").takeIf { it.isNotBlank() } ?: continue
                    add(
                        TagAssignmentProposal(
                            id = id,
                            recordId = recId,
                            recordTitle = o.optString("recordTitle", ""),
                            recordDateLocal = o.optString("recordDateLocal", ""),
                            recordTimeLabel = o.optString("recordTimeLabel", ""),
                            recordDurationSeconds = o.optLong("recordDurationSeconds", 0L),
                            previousTagId = if (o.isNull("previousTagId")) null else o.optString("previousTagId").takeIf { it.isNotBlank() },
                            previousTagName = if (o.isNull("previousTagName")) null else o.optString("previousTagName").takeIf { it.isNotBlank() },
                            previousCategoryKey = o.optString("previousCategoryKey", "other"),
                            newTagId = if (o.isNull("newTagId")) null else o.optString("newTagId").takeIf { it.isNotBlank() },
                            newTagName = if (o.isNull("newTagName")) null else o.optString("newTagName").takeIf { it.isNotBlank() },
                            newCategoryKey = o.optString("newCategoryKey", "other"),
                            affectedPeriodStart = o.optString("affectedPeriodStart", ""),
                            affectedPeriodEnd = o.optString("affectedPeriodEnd", ""),
                            affectedPeriodLabel = o.optString("affectedPeriodLabel", "tuần này")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun hasSeenAppIntro(): Boolean = prefs.getBoolean(KEY_HAS_SEEN_APP_INTRO, false)

    fun setAppIntroSeen(seen: Boolean = true) {
        prefs.edit().putBoolean(KEY_HAS_SEEN_APP_INTRO, seen).apply()
    }

    fun isFocusPinEnabled(): Boolean = prefs.getBoolean(KEY_FOCUS_PIN_ENABLED, false)

    fun setFocusPinEnabled(enabled: Boolean, immediate: Boolean = false) {
        val edit = prefs.edit().putBoolean(KEY_FOCUS_PIN_ENABLED, enabled)
        if (immediate) edit.commit() else edit.apply()
    }

    fun loadCustomizationSettings(): CustomizationSettings {
        val raw = prefs.getString(KEY_CUSTOMIZATION_JSON, null) ?: return CustomizationSettings()
        return runCatching {
            val o = JSONObject(raw)
            CustomizationSettings(
                enableKanban = o.optBoolean("enableKanban", false),
                enableEisenhower = o.optBoolean("enableEisenhower", false),
                enableHabits = o.optBoolean("enableHabits", true),
                enablePomodoro = o.optBoolean("enablePomodoro", true),
                enableDice = o.optBoolean("enableDice", true),
                enableAiAssistant = o.optBoolean("enableAiAssistant", true),
                defaultPomodoroMinutes = o.optInt("defaultPomodoroMinutes", 25).coerceIn(5, 120),
                defaultBreakMinutes = o.optInt("defaultBreakMinutes", 5).coerceIn(1, 30),
                themeMode = ThemeModePreference.fromStorage(o.optString("themeMode", "LIGHT")),
                fontScale = FontScalePreference.fromStorage(o.optString("fontScale", "LARGE"))
            )
        }.getOrDefault(CustomizationSettings())
    }

    fun saveCustomizationSettings(settings: CustomizationSettings, immediate: Boolean = false) {
        val obj = JSONObject()
            .put("enableKanban", settings.enableKanban)
            .put("enableEisenhower", settings.enableEisenhower)
            .put("enableHabits", settings.enableHabits)
            .put("enablePomodoro", settings.enablePomodoro)
            .put("enableDice", settings.enableDice)
            .put("enableAiAssistant", settings.enableAiAssistant)
            .put("defaultPomodoroMinutes", settings.defaultPomodoroMinutes)
            .put("defaultBreakMinutes", settings.defaultBreakMinutes)
            .put("themeMode", settings.themeMode.name)
            .put("fontScale", settings.fontScale.name)
        val edit = prefs.edit().putString(KEY_CUSTOMIZATION_JSON, obj.toString())
        if (immediate) edit.commit() else edit.apply()
    }

    fun loadAiReadPermissions(): AiReadPermissions {
        val raw = prefs.getString(KEY_AI_READ_PERMS_JSON, null) ?: return AiReadPermissions()
        return runCatching {
            val o = JSONObject(raw)
            AiReadPermissions(
                readLedger = o.optBoolean("readLedger", true),
                readTasks = o.optBoolean("readTasks", true),
                readHabits = o.optBoolean("readHabits", true),
                readGoals = o.optBoolean("readGoals", true)
            )
        }.getOrDefault(AiReadPermissions())
    }

    fun saveAiReadPermissions(perms: AiReadPermissions, immediate: Boolean = false) {
        val obj = JSONObject()
            .put("readLedger", perms.readLedger)
            .put("readTasks", perms.readTasks)
            .put("readHabits", perms.readHabits)
            .put("readGoals", perms.readGoals)
        val edit = prefs.edit().putString(KEY_AI_READ_PERMS_JSON, obj.toString())
        if (immediate) edit.commit() else edit.apply()
    }

    fun loadLocalRules(): List<LocalCategoryRule> {
        val raw = prefs.getString(KEY_LOCAL_RULES_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val kw = o.optString("keyword").takeIf { it.isNotBlank() } ?: continue
                    add(
                        LocalCategoryRule(
                            id = id,
                            keyword = kw,
                            category = TimeCategory.fromKeyOrDefault(o.optString("categoryKey", "work")),
                            tagId = if (o.isNull("tagId")) null else o.optString("tagId").takeIf { it.isNotBlank() },
                            markWasteByDefault = o.optBoolean("markWasteByDefault", false),
                            enabled = o.optBoolean("enabled", true)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveLocalRules(rules: List<LocalCategoryRule>, immediate: Boolean = false) {
        val arr = JSONArray()
        for (r in rules) {
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("keyword", r.keyword)
                    .put("categoryKey", r.category.key)
                    .put("tagId", r.tagId ?: JSONObject.NULL)
                    .put("markWasteByDefault", r.markWasteByDefault)
                    .put("enabled", r.enabled)
            )
        }
        val edit = prefs.edit().putString(KEY_LOCAL_RULES_JSON, arr.toString())
        if (immediate) edit.commit() else edit.apply()
    }

    fun loadUpdatePreferences(): UpdatePreferences = UpdatePreferences(
        autoCheckEnabled = prefs.getBoolean(KEY_UPDATE_AUTO_CHECK, true),
        skippedVersionCode = prefs.getLong(KEY_UPDATE_SKIPPED_VERSION_CODE, 0L),
        lastCheckEpochMs = prefs.getLong(KEY_UPDATE_LAST_CHECK_MS, 0L),
        promptedVersionCode = prefs.getLong(KEY_UPDATE_PROMPTED_VERSION_CODE, 0L),
        checkInterval = com.timewallet.mvp.domain.usecase.UpdateCheckInterval.fromStorageKey(
            prefs.getString(KEY_UPDATE_CHECK_INTERVAL, null)
        )
    )

    fun saveUpdatePreferences(updatePreferences: UpdatePreferences, immediate: Boolean = false) {
        val edit = prefs.edit()
            .putBoolean(KEY_UPDATE_AUTO_CHECK, updatePreferences.autoCheckEnabled)
            .putLong(KEY_UPDATE_SKIPPED_VERSION_CODE, updatePreferences.skippedVersionCode)
            .putLong(KEY_UPDATE_LAST_CHECK_MS, updatePreferences.lastCheckEpochMs)
            .putLong(KEY_UPDATE_PROMPTED_VERSION_CODE, updatePreferences.promptedVersionCode)
            .putString(KEY_UPDATE_CHECK_INTERVAL, updatePreferences.checkInterval.storageKey)
        if (immediate) edit.commit() else edit.apply()
    }

    fun loadPausedTimer(): PausedTimerContinuation? {
        val raw = prefs.getString(KEY_TIMER_PAUSED_JSON, null)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val o = JSONObject(raw)
            val title = o.optString("title", "").takeIf { it.isNotBlank() } ?: return null
            PausedTimerContinuation(
                title = title,
                categoryKey = o.optString("categoryKey", "work"),
                targetDurationSeconds = if (o.has("targetDurationSeconds") && !o.isNull("targetDurationSeconds")) {
                    o.optLong("targetDurationSeconds").takeIf { it > 0L }
                } else null,
                pausedAtEpochMs = o.optLong("pausedAtEpochMs", 0L),
                accumulatedSeconds = o.optLong("accumulatedSeconds", 0L).coerceAtLeast(0L),
                tagId = if (o.isNull("tagId")) null else o.optString("tagId").takeIf { it.isNotBlank() },
                taskId = if (o.isNull("taskId")) null else o.optString("taskId").takeIf { it.isNotBlank() },
                habitId = if (o.isNull("habitId")) null else o.optString("habitId").takeIf { it.isNotBlank() }
            )
        }.getOrNull()
    }

    fun savePausedTimer(paused: PausedTimerContinuation?, immediate: Boolean = true) {
        val edit = prefs.edit()
        if (paused == null) {
            edit.remove(KEY_TIMER_PAUSED_JSON)
        } else {
            val o = JSONObject()
                .put("title", paused.title)
                .put("categoryKey", paused.categoryKey)
                .put("targetDurationSeconds", paused.targetDurationSeconds ?: JSONObject.NULL)
                .put("pausedAtEpochMs", paused.pausedAtEpochMs)
                .put("accumulatedSeconds", paused.accumulatedSeconds)
                .put("tagId", paused.tagId ?: JSONObject.NULL)
                .put("taskId", paused.taskId ?: JSONObject.NULL)
                .put("habitId", paused.habitId ?: JSONObject.NULL)
            edit.putString(KEY_TIMER_PAUSED_JSON, o.toString())
        }
        if (immediate) edit.commit() else edit.apply()
    }

    data class FloatingAssistantPrefs(
        val enabled: Boolean = false,
        val edgeLeft: Boolean = false,
        val yFraction: Float = 0.42f
    )

    fun loadFloatingAssistantPrefs(): FloatingAssistantPrefs = FloatingAssistantPrefs(
        enabled = prefs.getBoolean(KEY_FLOATING_ENABLED, false),
        edgeLeft = prefs.getBoolean(KEY_FLOATING_EDGE_LEFT, false),
        yFraction = prefs.getFloat(KEY_FLOATING_Y_FRACTION, 0.42f).coerceIn(0.08f, 0.85f)
    )

    fun saveFloatingAssistantPrefs(config: FloatingAssistantPrefs, immediate: Boolean = false) {
        val edit = prefs.edit()
            .putBoolean(KEY_FLOATING_ENABLED, config.enabled)
            .putBoolean(KEY_FLOATING_EDGE_LEFT, config.edgeLeft)
            .putFloat(KEY_FLOATING_Y_FRACTION, config.yFraction.coerceIn(0.08f, 0.85f))
        if (immediate) edit.commit() else edit.apply()
    }

    fun saveTodoAiAnalysisJson(rawJson: String?, immediate: Boolean = false) {
        val edit = prefs.edit()
        if (rawJson.isNullOrBlank()) edit.remove(KEY_TODO_AI_ANALYSIS_JSON)
        else edit.putString(KEY_TODO_AI_ANALYSIS_JSON, rawJson)
        if (immediate) edit.commit() else edit.apply()
    }

    fun loadTodoAiAnalysisJson(): String? =
        prefs.getString(KEY_TODO_AI_ANALYSIS_JSON, null)?.takeIf { it.isNotBlank() }

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
        const val KEY_TIMER_TAG_ID = "timer_tag_id"   // v1.1.0
        const val KEY_TIMER_TASK_ID = "timer_task_id" // v2.0
        const val KEY_TIMER_HABIT_ID = "timer_habit_id" // v2.0

        private const val KEY_AI_OPT_IN = "ai_opt_in_enabled"
        const val KEY_AI_BASE_URL = "ai_base_url"
        const val KEY_AI_API_KEY_LEGACY = "ai_gemini_api_key"
        const val KEY_AI_API_KEY_ENCRYPTED = "ai_api_key_aes_gcm_v1"
        const val KEY_AI_TRUSTED_HOST = "ai_trusted_host"
        private const val KEY_AI_MODEL = "ai_gemini_model"
        private const val KEY_MANUAL_DRAFT_JSON = "manual_entry_draft_v1"
        private const val KEY_TIMER_DRAFT_TITLE = "timer_draft_title_v1"
        private const val KEY_TIMER_DRAFT_CATEGORY = "timer_draft_category_v1"
        private const val KEY_TIMER_DRAFT_TAG_ID = "timer_draft_tag_id_v1"
        private const val KEY_CHAT_INPUT_DRAFT = "chat_input_draft_v1"
        private const val KEY_CHAT_HISTORY_JSON = "chat_history_v1"
        private const val KEY_CHAT_PROPOSALS_DRAFT_JSON = "chat_proposals_draft_v1"
        private const val KEY_HAS_SEEN_APP_INTRO = "has_seen_app_intro_v1"
        private const val KEY_FOCUS_PIN_ENABLED = "focus_pin_enabled_v1"
        private const val KEY_CUSTOMIZATION_JSON = "customization_settings_v2"
        private const val KEY_AI_READ_PERMS_JSON = "ai_read_permissions_v2"
        private const val KEY_LOCAL_RULES_JSON = "local_category_rules_v2"
        private const val KEY_UPDATE_AUTO_CHECK = "app_update_auto_check_v1"
        private const val KEY_UPDATE_SKIPPED_VERSION_CODE = "app_update_skipped_version_code_v1"
        private const val KEY_UPDATE_LAST_CHECK_MS = "app_update_last_check_ms_v1"
        private const val KEY_UPDATE_PROMPTED_VERSION_CODE = "app_update_prompted_version_code_v1"
        private const val KEY_UPDATE_CHECK_INTERVAL = "app_update_check_interval_v1"
        private const val KEY_TIMER_PAUSED_JSON = "timer_paused_continuation_v1"
        private const val KEY_FLOATING_ENABLED = "wally_floating_enabled_v1"
        private const val KEY_FLOATING_EDGE_LEFT = "wally_floating_edge_left_v1"
        private const val KEY_FLOATING_Y_FRACTION = "wally_floating_y_fraction_v1"
        private const val KEY_TODO_AI_ANALYSIS_JSON = "wally_todo_ai_analysis_v1"
        private const val MAX_CHAT_HISTORY_ENTRIES = 60

        fun isFocusPinEnabled(context: Context): Boolean =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_FOCUS_PIN_ENABLED, false)

        fun setFocusPinEnabled(context: Context, enabled: Boolean, immediate: Boolean = false) {
            val edit = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_FOCUS_PIN_ENABLED, enabled)
            if (immediate) edit.commit() else edit.apply()
        }

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
        activityNotes: List<ActivityReviewNote> = emptyList(),
        // v1.1.0: chỉ xuất khi có (đầu ra cũ không đổi khi chưa dùng Tag/Mục tiêu).
        tags: List<com.timewallet.mvp.domain.model.Tag> = emptyList(),
        goals: List<com.timewallet.mvp.domain.model.Goal> = emptyList(),
        // v2.0: mở rộng tùy chọn cho Tasks/Projects/Habits, tương thích ngược schemaVersion=1 khi rỗng.
        projects: List<Project> = emptyList(),
        tasks: List<TaskItem> = emptyList(),
        habits: List<Habit> = emptyList(),
        habitLogs: List<HabitLog> = emptyList()
    ): String {
        val notesByRecordId = activityNotes.associateBy { it.recordId }
        val exportVersion = if (projects.isEmpty() && tasks.isEmpty() && habits.isEmpty() && habitLogs.isEmpty()) 1 else 2
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"schemaVersion\": ").append(exportVersion).append(",\n")
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
            sb.append("      \"idempotencyKey\": ").append(serializeJsonString(r.idempotencyKey)).append(",\n")
            if (r.tagId != null) sb.append("      \"tagId\": ").append(serializeJsonString(r.tagId)).append(",\n")
            if (r.confirmationStateRaw != null) sb.append("      \"confirmationState\": ").append(serializeJsonString(r.confirmationStateRaw)).append(",\n")
            if (r.plannedStartedAt != null) sb.append("      \"plannedStartedAt\": ").append(r.plannedStartedAt).append(",\n")
            if (r.plannedEndedAt != null) sb.append("      \"plannedEndedAt\": ").append(r.plannedEndedAt).append(",\n")
            if (r.plannedDurationSeconds != null) sb.append("      \"plannedDurationSeconds\": ").append(r.plannedDurationSeconds).append(",\n")
            if (r.overlapResolutionRaw != null) sb.append("      \"overlapResolution\": ").append(serializeJsonString(r.overlapResolutionRaw)).append(",\n")
            if (r.relationGroupId != null) sb.append("      \"relationGroupId\": ").append(serializeJsonString(r.relationGroupId)).append(",\n")
            if (r.overlapWithTitle != null) sb.append("      \"overlapWithTitle\": ").append(serializeJsonString(r.overlapWithTitle)).append(",\n")
            if (r.isMarkedWaste) sb.append("      \"isMarkedWaste\": true,\n")
            if (r.taskId != null) sb.append("      \"taskId\": ").append(serializeJsonString(r.taskId)).append(",\n")
            if (r.habitId != null) sb.append("      \"habitId\": ").append(serializeJsonString(r.habitId)).append(",\n")
            sb.append("      \"outcomeNote\": ").append(note?.outcomeNote?.let { serializeJsonString(it) } ?: "null").append(",\n")
            sb.append("      \"feynmanPrompt\": ").append(
                if (r.category.key == "learn") serializeJsonString("Bạn sẽ giải thích điều vừa học cho người chưa biết như thế nào?") else "null"
            ).append(",\n")
            sb.append("      \"feynmanExplanation\": ").append(note?.feynmanExplanation?.let { serializeJsonString(it) } ?: "null")
            if (note != null) {
                sb.append(",\n      \"activityNoteUpdatedAt\": ").append(note.updatedAt)
            }
            sb.append("\n")
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
            sb.append("      \"priorityOrder\": ").append(b.priorityOrder).append(",\n")
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
        sb.append("  ]")
        if (tags.isNotEmpty()) {
            sb.append(",\n  \"tags\": [\n")
            tags.forEachIndexed { index, t ->
                sb.append("    {\"id\": ").append(serializeJsonString(t.id)).append(", \"name\": ").append(serializeJsonString(t.name))
                    .append(", \"nameKey\": ").append(serializeJsonString(t.nameKey))
                    .append(", \"category\": ").append(serializeJsonString(t.category.key))
                    .append(", \"archivedAt\": ").append(t.archivedAt?.toString() ?: "null")
                    .append(", \"createdAt\": ").append(t.createdAt)
                    .append(", \"updatedAt\": ").append(t.updatedAt)
                    .append("}").append(if (index < tags.lastIndex) "," else "").append("\n")
            }
            sb.append("  ]")
        }
        if (goals.isNotEmpty()) {
            sb.append(",\n  \"goals\": [\n")
            goals.forEachIndexed { index, g ->
                sb.append("    {\"id\": ").append(serializeJsonString(g.id)).append(", \"groupId\": ").append(serializeJsonString(g.groupId))
                    .append(", \"targetType\": ").append(serializeJsonString(g.targetType.storageKey))
                    .append(", \"target\": ").append(serializeJsonString(g.targetKey))
                    .append(", \"kind\": ").append(serializeJsonString(g.kind.storageKey))
                    .append(", \"period\": ").append(serializeJsonString(g.period.storageKey))
                    .append(", \"targetSeconds\": ").append(g.targetSeconds)
                    .append(", \"status\": ").append(serializeJsonString(g.status.storageKey))
                    .append(", \"validFrom\": ").append(serializeJsonString(g.validFrom))
                    .append(", \"validTo\": ").append(g.validTo?.let { serializeJsonString(it) } ?: "null")
                    .append(", \"createdAt\": ").append(g.createdAt)
                    .append(", \"updatedAt\": ").append(g.updatedAt)
                    .append(", \"isPriority\": ").append(g.isPriority)
                    .append(", \"priorityOrder\": ").append(g.priorityOrder)
                    .append("}")
                    .append(if (index < goals.lastIndex) "," else "").append("\n")
            }
            sb.append("  ]")
        }
        if (projects.isNotEmpty()) {
            sb.append(",\n  \"projects\": [\n")
            projects.forEachIndexed { index, p ->
                sb.append("    {\"id\": ").append(serializeJsonString(p.id))
                    .append(", \"name\": ").append(serializeJsonString(p.name))
                    .append(", \"colorHex\": ").append(p.colorHex)
                    .append(", \"archivedAt\": ").append(p.archivedAt?.toString() ?: "null")
                    .append(", \"createdAt\": ").append(p.createdAt)
                    .append(", \"updatedAt\": ").append(p.updatedAt)
                    .append("}").append(if (index < projects.lastIndex) "," else "").append("\n")
            }
            sb.append("  ]")
        }
        if (tasks.isNotEmpty()) {
            sb.append(",\n  \"tasks\": [\n")
            tasks.forEachIndexed { index, t ->
                sb.append("    {\"id\": ").append(serializeJsonString(t.id))
                    .append(", \"title\": ").append(serializeJsonString(t.title))
                    .append(", \"notes\": ").append(serializeJsonString(t.notes))
                    .append(", \"projectId\": ").append(t.projectId?.let { serializeJsonString(it) } ?: "null")
                    .append(", \"tagId\": ").append(t.tagId?.let { serializeJsonString(it) } ?: "null")
                    .append(", \"category\": ").append(serializeJsonString(t.category.key))
                    .append(", \"priority\": ").append(serializeJsonString(t.priority.name))
                    .append(", \"estimatedMinutes\": ").append(t.estimatedMinutes?.toString() ?: "null")
                    .append(", \"dueDateLocal\": ").append(t.dueDateLocal?.let { serializeJsonString(it) } ?: "null")
                    .append(", \"dueTimeLocal\": ").append(t.dueTimeLocal?.let { serializeJsonString(it) } ?: "null")
                    .append(", \"status\": ").append(serializeJsonString(t.status.name))
                    .append(", \"eisenhowerQuad\": ").append(t.eisenhowerQuad?.let { serializeJsonString(it.name) } ?: "null")
                    .append(", \"goalId\": ").append(t.goalId?.let { serializeJsonString(it) } ?: "null")
                    .append(", \"createdAt\": ").append(t.createdAt)
                    .append(", \"updatedAt\": ").append(t.updatedAt)
                    .append(", \"completedAt\": ").append(t.completedAt?.toString() ?: "null")
                    .append(", \"postponedCount\": ").append(t.postponedCount)
                    .append(", \"subtasks\": [")
                t.subtasks.forEachIndexed { sIdx, s ->
                    sb.append("{\"id\": ").append(serializeJsonString(s.id))
                        .append(", \"title\": ").append(serializeJsonString(s.title))
                        .append(", \"isDone\": ").append(s.isDone)
                        .append(", \"sortOrder\": ").append(s.sortOrder).append("}")
                    if (sIdx < t.subtasks.lastIndex) sb.append(", ")
                }
                sb.append("]}").append(if (index < tasks.lastIndex) "," else "").append("\n")
            }
            sb.append("  ]")
        }
        if (habits.isNotEmpty()) {
            sb.append(",\n  \"habits\": [\n")
            habits.forEachIndexed { index, h ->
                sb.append("    {\"id\": ").append(serializeJsonString(h.id))
                    .append(", \"title\": ").append(serializeJsonString(h.title))
                    .append(", \"tagId\": ").append(h.tagId?.let { serializeJsonString(it) } ?: "null")
                    .append(", \"category\": ").append(serializeJsonString(h.category.key))
                    .append(", \"frequency\": ").append(serializeJsonString(h.frequency.name))
                    .append(", \"daysMask\": ").append(h.daysMask)
                    .append(", \"targetPerPeriod\": ").append(h.targetPerPeriod)
                    .append(", \"minSessionMinutes\": ").append(h.minSessionMinutes)
                    .append(", \"goalId\": ").append(h.goalId?.let { serializeJsonString(it) } ?: "null")
                    .append(", \"archivedAt\": ").append(h.archivedAt?.toString() ?: "null")
                    .append(", \"createdAt\": ").append(h.createdAt)
                    .append(", \"updatedAt\": ").append(h.updatedAt)
                    .append("}").append(if (index < habits.lastIndex) "," else "").append("\n")
            }
            sb.append("  ]")
        }
        if (habitLogs.isNotEmpty()) {
            sb.append(",\n  \"habitLogs\": [\n")
            habitLogs.forEachIndexed { index, l ->
                sb.append("    {\"id\": ").append(serializeJsonString(l.id))
                    .append(", \"habitId\": ").append(serializeJsonString(l.habitId))
                    .append(", \"dateLocal\": ").append(serializeJsonString(l.dateLocal))
                    .append(", \"completed\": ").append(l.completed)
                    .append(", \"linkedLedgerId\": ").append(l.linkedLedgerId?.let { serializeJsonString(it) } ?: "null")
                    .append(", \"updatedAt\": ").append(l.updatedAt)
                    .append("}").append(if (index < habitLogs.lastIndex) "," else "").append("\n")
            }
            sb.append("  ]")
        }
        sb.append("\n}")
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
