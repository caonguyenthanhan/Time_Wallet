package com.timewallet.mvp.data.ai

import com.timewallet.mvp.domain.usecase.AiFault
import com.timewallet.mvp.domain.usecase.Rx
import org.json.JSONArray
import org.json.JSONObject

/**
 * WALLY v1.0.6 (F-V105-08, R03): hợp đồng phản hồi AI chặt, KHÔNG phụ thuộc cách triển khai `org.json`
 * (bản JVM khi test khác bản AOSP trên thiết bị).
 *
 * - Nội dung phải là MỘT object JSON trọn vẹn: không bị cắt, không có phần dư ngoài object (không trích một object hợp lệ
 *   rồi bỏ qua phần dữ liệu hỏng còn lại).
 * - Lời dẫn quanh JSON chỉ được chấp nhận khi JSON nằm trong khối ``` và phần ngoài khối không chứa `{ } [ ]`.
 * - Văn bản thường (không phải JSON) được coi là lời đáp, không bao giờ là đề xuất.
 * - Trường sai kiểu, `null` ở trường bắt buộc là mảng, phần tử không phải object ⇒ MALFORMED.
 */
internal object StrictJson {

    sealed class Extracted {
        data class Obj(val json: JSONObject) : Extracted()
        data class Text(val text: String) : Extracted()
    }

    private fun malformed(): Nothing = throw AiHttpException(AiFault.MALFORMED)

    private val fence = Rx.ascii("```(?:json|JSON)?\\s*([\\s\\S]*?)```")

    private fun stripBomAndTrim(s: String): String =
        s.trimStart { it == '\uFEFF' || it.isWhitespace() }.trimEnd()

    fun extract(raw: String): Extracted {
        val text = stripBomAndTrim(raw)
        if (text.isEmpty()) malformed()

        val f = fence.find(text)
        if (f != null) {
            val outside = text.substring(0, f.range.first) + " " + text.substring(f.range.last + 1)
            if (outside.any { it == '{' || it == '}' || it == '[' || it == ']' }) malformed()
            return Extracted.Obj(parseSingleObject(stripBomAndTrim(f.groupValues[1])))
        }
        if (text.contains("```")) malformed() // khối rào chưa đóng: phản hồi bị cắt

        if (text.startsWith("{")) return Extracted.Obj(parseSingleObject(text))

        // Lời dẫn trước/sau một object trọn vẹn (không có ngoặc nào ngoài object) vẫn được chấp nhận.
        val from = text.indexOf('{')
        if (from > 0) {
            val end = endOfObject(text.substring(from))
            if (end > 0) {
                val before = text.substring(0, from)
                val after = text.substring(from + end)
                val bracketFree = (before + after).none { it == '{' || it == '}' || it == '[' || it == ']' }
                if (bracketFree && looksLikeJson(text.substring(from, from + end))) {
                    return Extracted.Obj(parseSingleObject(text.substring(from, from + end)))
                }
            }
        }
        if (looksLikeJson(text)) malformed()
        return Extracted.Text(text)
    }

    /** JSON không phải object (null/true/false/số/chuỗi/mảng) hoặc object bị cắt/hỏng ⇒ không phải lời đáp thường. */
    private fun looksLikeJson(text: String): Boolean =
        Rx.ascii("^\\[\\s*(?:\\{|\\[|\"|-?\\d|true\\b|false\\b|null\\b|\\])").containsMatchIn(text) ||
            Rx.ascii("^(null|true|false|-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?)$").matches(text) ||
            (text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) ||
            (text.contains('{') && text.contains("\":"))

    /** Chỉ chấp nhận đúng một object, bắt đầu bằng `{` và không còn gì ngoài khoảng trắng phía sau. */
    fun parseSingleObject(s: String): JSONObject {
        val t = stripBomAndTrim(s)
        if (!t.startsWith("{")) malformed()
        val end = endOfObject(t)
        if (end < 0) malformed()
        if (t.substring(end).isNotBlank()) malformed()
        return try {
            JSONObject(t.substring(0, end))
        } catch (_: org.json.JSONException) {
            malformed()
        }
    }

    private fun endOfObject(s: String): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in s.indices) {
            val c = s[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i + 1
                }
            }
        }
        return -1
    }

    // ---------------------------------------------------------------- hình dạng (schema) chặt

    fun requireShape(root: JSONObject) {
        if (!root.has("proposals") && !root.has("reply") && !root.has("queries") && !root.has("tagAssignments")) malformed()
        if (root.has("reply")) {
            val r = root.get("reply")
            if (r != JSONObject.NULL && r !is String) malformed()
        }
        arrayField(root, "proposals")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.get(i)
                if (item !is JSONObject) malformed()
                requireProposalItem(item)
            }
        }
        arrayField(root, "tagAssignments")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.get(i)
                if (item !is JSONObject) malformed()
                listOf("recordTitle", "dateLocal", "startHm", "tagName").forEach { stringOrNull(item, it) }
            }
        }
        arrayField(root, "queries")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.get(i)
                if (item !is JSONObject) malformed()
                listOf("metric", "startDate", "endDate", "category", "tag", "compareStartDate", "compareEndDate").forEach { stringOrNull(item, it) }
            }
        }
    }

    /** Trường có mặt phải là mảng (null không được chấp nhận); vắng mặt thì trả null. */
    private fun arrayField(root: JSONObject, key: String): JSONArray? {
        if (!root.has(key)) return null
        val v = root.get(key)
        if (v !is JSONArray) malformed()
        return v
    }

    private fun stringOrNull(o: JSONObject, key: String) {
        if (!o.has(key)) return
        val v = o.get(key)
        if (v != JSONObject.NULL && v !is String) malformed()
    }

    private fun requireProposalItem(o: JSONObject) {
        listOf("title", "category", "dateLocal", "startLocal", "endLocal", "timeEvidence", "tagName").forEach { stringOrNull(o, it) }
        if (o.has("durationSeconds")) {
            val d = o.get("durationSeconds")
            if (d !is Number) malformed()
        }
        if (o.has("missingFields")) {
            val m = o.get("missingFields")
            if (m != JSONObject.NULL && m !is JSONArray) malformed()
        }
    }
}
