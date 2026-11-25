package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TimeCategory
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * v1.1.0 (B): quy tắc Tag cá nhân — thuần, không đụng sổ cái.
 * Tag là hoạt động cụ thể của người dùng; Category là nhóm tổng quát. Wally chỉ GỢI Ý phân loại, không áp đặt.
 */
object TagRules {
    const val MAX_NAME_LENGTH = 40

    sealed class NameCheck {
        data class Ok(val cleanName: String, val nameKey: String) : NameCheck()
        data class Invalid(val message: String) : NameCheck()
        /** Đã có Tag cùng tên (kể cả đã lưu trữ ⇒ nên khôi phục thay vì tạo trùng). */
        data class Duplicate(val existing: Tag) : NameCheck()
    }

    /** Bỏ dấu, thường, gọn khoảng trắng: "  Chạy   Bộ " → "chay bo". Dùng để chống trùng và tìm kiếm. */
    // Không dùng Regex ở đây: bỏ dấu và gộp khoảng trắng bằng vòng lặp ký tự (an toàn như nhau trên JVM và Android; Rx không cho phép \p{Mn}).
    private fun collapseSpaces(s: String): String {
        val sb = StringBuilder(s.length)
        var pendingSpace = false
        for (ch in s) {
            if (ch.isWhitespace()) { pendingSpace = sb.isNotEmpty(); continue }
            if (pendingSpace) { sb.append(' '); pendingSpace = false }
            sb.append(ch)
        }
        return sb.toString()
    }

    fun nameKey(name: String): String {
        val decomposed = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
        val noMarks = StringBuilder(decomposed.length)
        for (ch in decomposed) if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) noMarks.append(ch)
        return collapseSpaces(noMarks.toString().replace('đ', 'd').replace('Đ', 'D').lowercase())
    }

    fun cleanName(name: String): String = collapseSpaces(name.trim())

    fun checkName(raw: String, existing: List<Tag>, excludeId: String? = null): NameCheck {
        val clean = cleanName(raw)
        if (clean.isEmpty()) return NameCheck.Invalid("Hãy nhập tên Tag.")
        if (clean.length > MAX_NAME_LENGTH) return NameCheck.Invalid("Tên Tag tối đa $MAX_NAME_LENGTH ký tự.")
        if (clean.any { it.isISOControl() }) return NameCheck.Invalid("Tên Tag có ký tự không hợp lệ.")
        val key = nameKey(clean)
        if (key.isEmpty()) return NameCheck.Invalid("Hãy nhập tên Tag.")
        existing.firstOrNull { it.nameKey == key && it.id != excludeId }?.let { return NameCheck.Duplicate(it) }
        return NameCheck.Ok(clean, key)
    }

    fun newTag(cleanName: String, nameKey: String, category: TimeCategory, nowEpochMs: Long, id: String = "tag_${UUID.randomUUID()}"): Tag =
        Tag(id = id, name = cleanName, nameKey = nameKey, category = category, archivedAt = null, createdAt = nowEpochMs, updatedAt = nowEpochMs)

    fun rename(tag: Tag, cleanName: String, nameKey: String, nowEpochMs: Long): Tag = tag.copy(name = cleanName, nameKey = nameKey, updatedAt = nowEpochMs)

    /** Đổi Category chỉ áp cho bản ghi MỚI; bản ghi cũ giữ `category` đã ghi nhận. */
    fun recategorize(tag: Tag, category: TimeCategory, nowEpochMs: Long): Tag = tag.copy(category = category, updatedAt = nowEpochMs)

    fun archive(tag: Tag, nowEpochMs: Long): Tag = if (tag.isArchived) tag else tag.copy(archivedAt = nowEpochMs, updatedAt = nowEpochMs)

    fun restore(tag: Tag, nowEpochMs: Long): Tag = if (!tag.isArchived) tag else tag.copy(archivedAt = null, updatedAt = nowEpochMs)

    /**
     * Gợi ý Tag cho màn ghi nhận (B3): Tag đang dùng, "gần đây" trước (tối đa [recentCount]), sau đó "thường dùng" trong 30 ngày,
     * rồi Tag chưa dùng theo ngày tạo mới nhất. Người dùng không phải duyệt hàng chục Tag.
     */
    fun suggestions(
        tags: List<Tag>,
        records: List<LedgerRecord>,
        nowEpochMs: Long,
        zone: ZoneId,
        limit: Int = 8,
        recentCount: Int = 4
    ): List<Tag> {
        val active = tags.filter { !it.isArchived }
        if (active.isEmpty()) return emptyList()
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
        val since = today.minusDays(29)
        val lastUsed = HashMap<String, Long>()
        val uses30 = HashMap<String, Int>()
        for (r in records) {
            val id = r.tagId ?: continue
            val ts = r.userConfirmedAt.coerceAtLeast(r.createdAt)
            if (ts > (lastUsed[id] ?: Long.MIN_VALUE)) lastUsed[id] = ts
            val d = runCatching { LocalDate.parse(r.date) }.getOrNull() ?: continue
            if (!d.isBefore(since) && !d.isAfter(today)) uses30[id] = (uses30[id] ?: 0) + 1
        }
        val used = active.filter { lastUsed.containsKey(it.id) }
        val recent = used.sortedWith(compareByDescending<Tag> { lastUsed[it.id] }.thenBy { it.nameKey }).take(recentCount)
        val recentIds = recent.map { it.id }.toSet()
        val frequent = used.filter { it.id !in recentIds }
            .sortedWith(compareByDescending<Tag> { uses30[it.id] ?: 0 }.thenByDescending { lastUsed[it.id] }.thenBy { it.nameKey })
        val unused = active.filter { !lastUsed.containsKey(it.id) }.sortedWith(compareByDescending<Tag> { it.createdAt }.thenBy { it.nameKey })
        return (recent + frequent + unused).take(limit)
    }

    /** Tìm Tag (không phân biệt dấu/hoa thường), ưu tiên khớp đầu tên. Chỉ Tag đang dùng, trừ khi [includeArchived]. */
    fun search(tags: List<Tag>, query: String, includeArchived: Boolean = false, limit: Int = 12): List<Tag> {
        val q = nameKey(query)
        val pool = tags.filter { includeArchived || !it.isArchived }
        if (q.isEmpty()) return pool.sortedBy { it.nameKey }.take(limit)
        return pool.filter { q in it.nameKey }
            .sortedWith(compareBy<Tag>({ if (it.nameKey.startsWith(q)) 0 else 1 }, { it.nameKey }))
            .take(limit)
    }

    /**
     * Khớp xác định (không AI) giữa tiêu đề một đề xuất và Tag đang dùng: trùng hẳn tên (không dấu/hoa thường) hoặc tên Tag là cụm từ
     * nguyên vẹn trong tiêu đề; nhiều Tag khớp thì lấy Tag có tên dài nhất. Tên Tag quá ngắn (< 2 ký tự) không dùng để khớp từng phần.
     */
    fun matchTitle(title: String, tags: List<Tag>): Tag? {
        val key = nameKey(title)
        if (key.isEmpty()) return null
        val active = tags.filter { !it.isArchived && it.nameKey.isNotEmpty() }
        active.firstOrNull { it.nameKey == key }?.let { return it }
        val padded = " $key "
        return active.filter { it.nameKey.length >= 2 && padded.contains(" ${it.nameKey} ") }.maxByOrNull { it.nameKey.length }
    }

    /**
     * Khớp Tag được người dùng gọi tên tường minh trong câu nhập tự nhiên (kể cả Tag ít dùng).
     * Chỉ khớp cụm từ nguyên vẹn; không tự suy đoán từ khái niệm rộng hơn (ví dụ "tiếng Anh" không tự khớp "IELTS").
     */
    fun findExplicitMatchesInText(text: String, tags: List<Tag>): List<Tag> {
        val key = nameKey(text)
        if (key.isEmpty()) return emptyList()
        val active = tags.filter { !it.isArchived && it.nameKey.isNotEmpty() }
        val padded = " $key "
        return active.filter { tag ->
            tag.nameKey == key || (tag.nameKey.length >= 2 && padded.contains(" ${tag.nameKey} "))
        }.sortedWith(compareByDescending<Tag> { it.nameKey.length }.thenBy { it.nameKey })
    }

    /**
     * Tìm Tag cho một đề xuất từ AI/nhập liệu theo thứ tự tin cậy:
     * (1) Tag của To-do/bản nháp đang nối tiếp nếu chưa bị đổi/gỡ,
     * (2) Tag khớp tiêu đề đề xuất,
     * (3) Tag gọi tên tường minh trong đúng mệnh đề tương ứng của `rawInput`,
     * (4) Tag do AI gợi ý nhưng BẮT BUỘC phải có căn cứ tên Tag trong `title`/`rawInput` hoặc bản nháp/công việc gốc.
     */
    fun resolveProposalTag(
        title: String,
        rawInput: String,
        tags: List<Tag>,
        proposedTagId: String? = null,
        proposedTagName: String? = null,
        linkedTaskTagId: String? = null,
        inheritedDraftTagId: String? = null,
        isSingleProposal: Boolean = true,
        explicitClearRequested: Boolean = false
    ): Tag? {
        if (explicitClearRequested) return null
        val active = tags.filter { !it.isArchived && it.nameKey.isNotEmpty() }
        if (active.isEmpty()) return null
        val activeById = active.associateBy { it.id }

        matchTitle(title, active)?.let { return it }

        val relevantClause = if (isSingleProposal) {
            rawInput
        } else {
            bestMatchingClause(title = title, rawInput = rawInput) ?: ""
        }
        if (relevantClause.isNotBlank()) {
            matchTitle(relevantClause, active)?.let { return it }
        }

        if (isSingleProposal) {
            val allInRaw = findExplicitMatchesInText(rawInput, active)
            if (allInRaw.size == 1) return allInRaw.first()
        }

        val groundedPoolKeys = buildSet {
            add(nameKey(title))
            if (relevantClause.isNotBlank()) add(nameKey(relevantClause))
            if (isSingleProposal) add(nameKey(rawInput))
        }
        fun isGroundedInText(tag: Tag): Boolean = groundedPoolKeys.any { k ->
            k == tag.nameKey || (tag.nameKey.length >= 2 && " $k ".contains(" ${tag.nameKey} "))
        }

        proposedTagId?.let { activeById[it] }?.let { candidate ->
            if (candidate.id == linkedTaskTagId || candidate.id == inheritedDraftTagId || isGroundedInText(candidate)) {
                return candidate
            }
        }
        proposedTagName?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
            val key = nameKey(name)
            active.firstOrNull { it.nameKey == key }?.let { candidate ->
                if (candidate.id == linkedTaskTagId || candidate.id == inheritedDraftTagId || isGroundedInText(candidate)) {
                    return candidate
                }
            }
        }

        linkedTaskTagId?.let { activeById[it] }?.let { return it }
        inheritedDraftTagId?.let { activeById[it] }?.let { return it }
        return null
    }

    private fun bestMatchingClause(title: String, rawInput: String): String? {
        val clauses = rawInput.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (clauses.size <= 1) return clauses.firstOrNull()
        val titleTokens = nameKey(title).split(' ').filter { it.length >= 2 }.toSet()
        if (titleTokens.isEmpty()) return null
        return clauses.maxByOrNull { clause ->
            val cTokens = nameKey(clause).split(' ').filter { it.length >= 2 }.toSet()
            titleTokens.count { it in cTokens }
        }?.takeIf { clause ->
            val cTokens = nameKey(clause).split(' ').filter { it.length >= 2 }.toSet()
            titleTokens.any { it in cTokens }
        }
    }

    /** Số bản ghi gắn Tag — để hiển thị "đang dùng N lần" khi lưu trữ/đổi tên (không dùng để xóa dữ liệu). */
    fun usageCount(tagId: String, records: List<LedgerRecord>): Int = records.count { it.tagId == tagId }
}
