package com.timewallet.mvp.data.ai

import com.timewallet.mvp.domain.usecase.AiUsage
import com.timewallet.mvp.domain.usecase.CacheStatus
import org.json.JSONObject

/**
 * Đọc `usage` từ phản hồi chat/completions. Không giả định nhà cung cấp hỗ trợ bộ nhớ đệm: chỉ báo HIT/MISS khi
 * nhà cung cấp THỰC SỰ trả trường token đệm (OpenAI: `prompt_tokens_details.cached_tokens`; một số cổng: `cached_tokens`,
 * `cache_read_input_tokens`; kiểu Gemini gốc: `cachedContentTokenCount`).
 */
object AiUsageParser {

    private fun JSONObject.intOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE && it >= 0 } else null

    fun parse(root: JSONObject): AiUsage? {
        val usage = root.optJSONObject("usage") ?: return null
        val prompt = usage.intOrNull("prompt_tokens") ?: usage.intOrNull("input_tokens") ?: usage.intOrNull("promptTokenCount")
        val completion = usage.intOrNull("completion_tokens") ?: usage.intOrNull("output_tokens") ?: usage.intOrNull("candidatesTokenCount")
        val cached = usage.optJSONObject("prompt_tokens_details")?.intOrNull("cached_tokens")
            ?: usage.optJSONObject("input_tokens_details")?.intOrNull("cached_tokens")
            ?: usage.intOrNull("cached_tokens")
            ?: usage.intOrNull("cache_read_input_tokens")
            ?: usage.intOrNull("cachedContentTokenCount")
            ?: usage.intOrNull("cached_content_token_count")
        val status = when {
            cached == null -> CacheStatus.NOT_REPORTED
            cached > 0 -> CacheStatus.HIT
            else -> CacheStatus.MISS
        }
        return AiUsage(prompt, completion, cached, status)
    }

    /** Đọc từ nội dung thô; trả null nếu không phải JSON object (không ném). */
    fun parseBody(body: String): AiUsage? = try {
        val cleaned = body.trimStart { it == '\uFEFF' || it.isWhitespace() }.trimEnd()
        parse(JSONObject(cleaned))
    } catch (_: Exception) {
        null
    }
}
