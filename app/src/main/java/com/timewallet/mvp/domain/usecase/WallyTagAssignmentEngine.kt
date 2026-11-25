package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.AiReadPermissions
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.RawTagAssignmentIntent
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TagAssignmentProposal
import com.timewallet.mvp.domain.model.TagAssignmentUndoToken
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.UUID

/**
 * Kết quả đối chiếu yêu cầu gắn/đổi/bỏ Tag cho một phiên cũ đã lưu.
 */
sealed class TagAssignmentResolution {
    data object NotApplicable : TagAssignmentResolution()
    data class PermissionDenied(val message: String) : TagAssignmentResolution()
    data class Single(val proposal: TagAssignmentProposal) : TagAssignmentResolution()
    data class Ambiguous(val question: String, val suggestions: List<String>) : TagAssignmentResolution()
    data class NoChange(val message: String) : TagAssignmentResolution()
    data class NotFound(val message: String) : TagAssignmentResolution()
}

/**
 * Bộ máy thuần Kotlin (ZERO Regex, 100% JVM-testable) cho nghiệp vụ gắn/đổi/bỏ Tag cho phiên đã lưu:
 * - Không bao giờ tự ghi dữ liệu khi chưa có người dùng duyệt.
 * - Đối chiếu xác định phiên theo tên, ngày, mốc giờ; hỏi lại nếu có nhiều phiên trùng khớp.
 * - Sau khi duyệt hoặc Hoàn tác, gọi [LedgerQueryEngine.execute] tính lại tổng tức thì ngay trong Chat.
 */
object WallyTagAssignmentEngine {

    private val vi = Locale("vi", "VN")
    private val hmFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val dmFmt = DateTimeFormatter.ofPattern("dd/MM")

    private val assignVerbs = listOf("gan tag", "gan nhan", "doi tag", "chuyen tag", "sua tag", "cap nhat tag", "them tag")
    private val removeVerbs = listOf("bo tag", "go tag", "xoa tag")
    private val existingRecordCues = listOf(
        "phien", "ban ghi", "hoat dong", "buoi", "luc ", "hom qua", "hom kia",
        "sang nay", "trua nay", "chieu nay", "toi nay", "tuan nay", "cho cai ", "vao phien"
    )
    private val stopWords = setOf(
        "gan", "doi", "chuyen", "sua", "cap", "nhat", "them", "bo", "go", "xoa",
        "tag", "nhan", "cho", "vao", "khoi", "cua", "o", "phien", "ban", "ghi",
        "hoat", "dong", "buoi", "luc", "ngay", "hom", "qua", "nay", "kia",
        "sang", "trua", "chieu", "toi", "tuan", "nay", "thanh", "la", "giup",
        "toi", "minh", "nhe", "nha", "duoc", "khong", "vua", "cu"
    )

    fun isExistingSessionTagRequest(raw: String, tags: List<Tag>): Boolean {
        val norm = normalizeAscii(raw)
        if (norm.isEmpty()) return false
        val hasAssign = assignVerbs.any { norm.contains(it) }
        val hasRemove = removeVerbs.any { norm.contains(it) }
        if (!hasAssign && !hasRemove) return false
        val hasSchedNew = WallyConversationEngine.hasSchedulingIntentWithoutTime(raw) ||
            listOf("ngay mai", "xep lich", "dat lich", "sap xep", "len lich").any { norm.contains(it) }
        if (hasSchedNew) return false
        val explicitRecord = existingRecordCues.any { norm.contains(it) }
        // Nếu người dùng nói rõ "gắn tag IELTS cho Luyện nghe" (không phải tạo lịch mới) -> coi là gắn tag phiên cũ.
        val hasCho = (" $norm ").contains(" cho ") || (" $norm ").contains(" vao ") || (" $norm ").contains(" khoi ")
        return explicitRecord || (hasAssign && hasCho) || (hasRemove && hasCho && tags.isNotEmpty())
    }

    fun findCandidateRecordsForContext(
        userText: String,
        records: List<LedgerRecord>,
        today: LocalDate,
        zone: ZoneId,
        limit: Int = 6
    ): List<LedgerRecord> {
        val norm = normalizeAscii(userText)
        val targetDate = resolveExplicitPastOrPresentDate(norm, today)
        val targetHm = WallyConversationEngine.extractExplicitClockHm(userText)
        return records.asSequence()
            .filter { !it.isCancelled && it.layer != TimeLayer.PLANNED && !it.status.equals("PLANNED", ignoreCase = true) }
            .sortedWith(compareByDescending<LedgerRecord> {
                scoreRecordMatch(it, norm, targetDate, targetHm, emptySet(), zone)
            }.thenByDescending { it.startedAt ?: it.createdAt })
            .take(limit)
            .toList()
    }

    fun resolve(
        userText: String,
        rawIntents: List<RawTagAssignmentIntent>,
        perms: AiReadPermissions,
        records: List<LedgerRecord>,
        tags: List<Tag>,
        today: LocalDate,
        zone: ZoneId
    ): TagAssignmentResolution {
        val firstRaw = rawIntents.firstOrNull()
        val isExplicitCmd = isExistingSessionTagRequest(userText, tags)
        if (firstRaw == null && !isExplicitCmd) {
            return TagAssignmentResolution.NotApplicable
        }
        if (!perms.readLedger) {
            return TagAssignmentResolution.PermissionDenied(
                "Chưa bật quyền AI đọc Sổ cái trong Cài đặt nên Wally chưa thể tìm phiên đã lưu để gắn Tag."
            )
        }

        val activeTags = tags.filter { !it.isArchived }
        val normInput = normalizeAscii(userText)
        val wantClear = removeVerbs.any { normInput.contains(it) } ||
            (firstRaw != null && firstRaw.tagName == null && removeVerbs.any { normInput.contains(it) })

        val targetTag: Tag? = if (wantClear) {
            null
        } else {
            val fromUser = TagRules.findExplicitMatchesInText(userText, activeTags).firstOrNull()
            val fromRaw = firstRaw?.tagName?.trim()?.takeIf { it.isNotEmpty() }?.let { rawName ->
                val k = TagRules.nameKey(rawName)
                activeTags.firstOrNull { it.nameKey == k }
                    ?: TagRules.findExplicitMatchesInText(rawName, activeTags).firstOrNull()
            }?.takeIf { candidate ->
                TagRules.findExplicitMatchesInText(userText, listOf(candidate)).isNotEmpty() ||
                    normalizeAscii(userText).contains(candidate.nameKey)
            }
            fromUser ?: fromRaw ?: return TagAssignmentResolution.NotFound(
                if (activeTags.isEmpty()) {
                    "Bạn chưa tạo Tag nào. Hãy tạo Tag trong Cài đặt › Quản lý Tag trước."
                } else {
                    "Wally chưa xác định được Tag bạn muốn gắn (${activeTags.take(5).joinToString(", ") { it.name }})."
                }
            )
        }

        val targetDate = firstRaw?.dateLocal?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: resolveExplicitPastOrPresentDate(normInput, today)
        val targetHm = firstRaw?.startHm?.trim()?.takeIf { it.isNotEmpty() }
            ?: WallyConversationEngine.extractExplicitClockHm(userText)
        val tagTokens = targetTag?.nameKey?.split(' ')?.filter { it.isNotBlank() }?.toSet().orEmpty()
        val hintText = firstRaw?.recordTitle?.takeIf { it.isNotBlank() } ?: userText
        val queryTokens = normalizeAscii(hintText)
            .split(' ', ',', '.', ';', ':', '?', '!', '"', '\'')
            .filter { it.length >= 2 && it !in stopWords && it !in tagTokens }
            .filterNot { it.endsWith("h") && it.dropLast(1).all(Char::isDigit) }
            .filterNot { it.contains(':') && it.substringBefore(':').all(Char::isDigit) }
            .toSet()

        val actualRecords = records.filter {
            !it.isCancelled &&
                it.layer != TimeLayer.PLANNED &&
                !it.status.equals("PLANNED", ignoreCase = true)
        }

        val scored = actualRecords.mapNotNull { rec ->
            val score = scoreRecordMatch(
                record = rec,
                normQuery = normalizeAscii(hintText),
                targetDate = targetDate,
                targetHm = targetHm,
                queryTokens = queryTokens,
                zone = zone
            )
            val dateMatches = targetDate == null || rec.date == targetDate.toString()
            val startHmMatches = targetHm == null || startHmOf(rec, zone) == targetHm
            val hasStrongCue = if (queryTokens.isNotEmpty()) {
                val recNorm = normalizeAscii(rec.title)
                queryTokens.any { recNorm.contains(it) }
            } else {
                targetDate != null || targetHm != null
            }
            if (score > 0 && dateMatches && startHmMatches && hasStrongCue) rec to score else null
        }.sortedWith(compareByDescending<Pair<LedgerRecord, Int>> { it.second }.thenByDescending { it.first.startedAt ?: it.first.createdAt })

        if (scored.isEmpty()) {
            return TagAssignmentResolution.NotFound(
                "Chưa tìm thấy phiên đã lưu nào khớp yêu cầu để ${targetTag?.let { "gắn tag ${it.name}" } ?: "bỏ tag"}. Bạn hãy nêu tên hoặc giờ/ngày cụ thể của phiên."
            )
        }

        val topScore = scored.first().second
        val topCandidates = scored.filter { it.second == topScore }.map { it.first }
        if (topCandidates.size > 1) {
            val tagPhrase = targetTag?.let { "gắn tag ${it.name}" } ?: "bỏ tag"
            val chips = topCandidates.take(3).map { rec ->
                val hm = timeLabelOf(rec, zone)
                "${targetTag?.let { "Gắn tag ${it.name}" } ?: "Bỏ tag"} cho \"${rec.title}\" (${rec.date} $hm)"
            }
            return TagAssignmentResolution.Ambiguous(
                question = "Có ${topCandidates.size} phiên khớp — bạn muốn $tagPhrase cho phiên nào?",
                suggestions = chips
            )
        }

        val chosen = topCandidates.single()
        if (chosen.tagId == targetTag?.id) {
            val tagLabel = targetTag?.name ?: "không gắn Tag"
            return TagAssignmentResolution.NoChange(
                "Phiên \"${chosen.title}\" (${chosen.date}) hiện đã mang $tagLabel rồi."
            )
        }

        val recDate = runCatching { LocalDate.parse(chosen.date) }.getOrDefault(today)
        val periodStart = recDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val periodEnd = periodStart.plusDays(6)
        val periodLabel = if (!today.isBefore(periodStart) && !today.isAfter(periodEnd)) {
            "tuần này"
        } else {
            "tuần ${periodStart.format(dmFmt)}–${periodEnd.format(dmFmt)}"
        }
        val prevTag = chosen.tagId?.let { tid -> tags.firstOrNull { it.id == tid } }
        val newCategoryKey = targetTag?.category?.key ?: chosen.category.key

        return TagAssignmentResolution.Single(
            TagAssignmentProposal(
                id = "tagassign_${chosen.id}_${UUID.randomUUID().toString().take(8)}",
                recordId = chosen.id,
                recordTitle = chosen.title,
                recordDateLocal = chosen.date,
                recordTimeLabel = timeLabelOf(chosen, zone),
                recordDurationSeconds = chosen.durationSeconds,
                previousTagId = chosen.tagId,
                previousTagName = prevTag?.name,
                previousCategoryKey = chosen.category.key,
                newTagId = targetTag?.id,
                newTagName = targetTag?.name,
                newCategoryKey = newCategoryKey,
                affectedPeriodStart = periodStart.toString(),
                affectedPeriodEnd = periodEnd.toString(),
                affectedPeriodLabel = periodLabel,
                needsConfirmation = true
            )
        )
    }

    fun buildRecalculatedChatAnswer(
        token: TagAssignmentUndoToken,
        updatedRecords: List<LedgerRecord>,
        tags: List<Tag>,
        budgets: List<CategoryTimeBudget>,
        goals: List<Goal>,
        activeTimer: ActiveTimerSession?,
        zone: ZoneId,
        nowEpochMs: Long,
        wasUndone: Boolean
    ): ChatAnswer {
        val periodStart = runCatching { LocalDate.parse(token.affectedPeriodStart) }.getOrDefault(LocalDate.now(zone))
        val periodEnd = runCatching { LocalDate.parse(token.affectedPeriodEnd) }.getOrDefault(periodStart.plusDays(6))
        val queryPeriod = QueryPeriod(periodStart, periodEnd, token.affectedPeriodLabel, QueryPeriodKind.WEEK)
        val targetTagIdForQuery = if (wasUndone) {
            token.previousTagId ?: token.appliedTagId
        } else {
            token.appliedTagId ?: token.previousTagId
        }
        val targetTag = targetTagIdForQuery?.let { id -> tags.firstOrNull { !it.isArchived && it.id == id } }

        val updatedTotals = if (targetTag != null) {
            LedgerQueryEngine.execute(
                intent = QueryIntent(
                    metric = QueryMetric.TOTAL,
                    period = queryPeriod,
                    category = targetTag.category,
                    tagId = targetTag.id,
                    tagName = targetTag.name
                ),
                records = updatedRecords,
                zoneId = zone,
                nowEpochMs = nowEpochMs,
                activeTimer = activeTimer,
                budgets = budgets,
                tags = tags,
                goals = goals
            )
        } else {
            val cat = TimeCategory.fromKeyOrDefault(if (wasUndone) token.previousCategoryKey else token.appliedCategoryKey)
            LedgerQueryEngine.execute(
                intent = QueryIntent(
                    metric = QueryMetric.TOTAL,
                    period = queryPeriod,
                    category = cat
                ),
                records = updatedRecords,
                zoneId = zone,
                nowEpochMs = nowEpochMs,
                activeTimer = activeTimer,
                budgets = budgets,
                tags = tags,
                goals = goals
            )
        }

        val headline = if (wasUndone) {
            "↶ Đã hoàn tác gắn tag cho \"${token.recordTitle}\""
        } else if (token.appliedTagName != null) {
            "✓ Đã gắn tag ${token.appliedTagName} cho \"${token.recordTitle}\""
        } else {
            "✓ Đã bỏ tag khỏi \"${token.recordTitle}\""
        }

        val lines = buildList {
            if (wasUndone) {
                add("Tag khôi phục: ${token.previousTagName ?: "Chưa gắn Tag"}")
            } else {
                val fromName = token.previousTagName ?: "Chưa gắn Tag"
                val toName = token.appliedTagName ?: "Không gắn Tag"
                add("Cập nhật Tag: $fromName → $toName")
            }
            add(updatedTotals.headline)
            updatedTotals.lines.take(3).forEach { add(it) }
        }

        return ChatAnswer(
            question = if (wasUndone) "Hoàn tác gắn Tag" else "Cập nhật Tag cho phiên đã lưu",
            headline = headline,
            lines = lines,
            notes = updatedTotals.notes,
            label = if (wasUndone) "Đã hoàn tác" else "Đã cập nhật & tính lại",
            undoToken = token.copy(undone = wasUndone)
        )
    }

    private fun scoreRecordMatch(
        record: LedgerRecord,
        normQuery: String,
        targetDate: LocalDate?,
        targetHm: String?,
        queryTokens: Set<String>,
        zone: ZoneId
    ): Int {
        var score = 0
        val recNorm = normalizeAscii(record.title)
        if (targetDate != null && record.date == targetDate.toString()) score += 40
        if (targetHm != null && startHmOf(record, zone) == targetHm) score += 50
        if (normQuery.contains(recNorm) && recNorm.length >= 3) score += 35
        for (tok in queryTokens) {
            if (recNorm.contains(tok)) score += 15
        }
        if (record.tagId == null) score += 2
        return score
    }

    private fun startHmOf(record: LedgerRecord, zone: ZoneId): String? =
        record.startedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime().format(hmFmt) }

    private fun timeLabelOf(record: LedgerRecord, zone: ZoneId): String {
        val s = record.startedAt
        val e = record.endedAt
        return if (s != null && e != null) {
            val st = Instant.ofEpochMilli(s).atZone(zone).toLocalTime().format(hmFmt)
            val en = Instant.ofEpochMilli(e).atZone(zone).toLocalTime().format(hmFmt)
            "$st–$en (${LedgerAnalyticsCalculator.formatCompactDurationSeconds(record.durationSeconds)})"
        } else {
            "Chưa gắn giờ (${LedgerAnalyticsCalculator.formatCompactDurationSeconds(record.durationSeconds)})"
        }
    }

    private fun resolveExplicitPastOrPresentDate(norm: String, today: LocalDate): LocalDate? {
        norm.split(' ', '(', ')', '[', ']', ',', ';', '\n')
            .asSequence()
            .map { it.trim() }
            .filter { it.length == 10 && it[4] == '-' && it[7] == '-' }
            .forEach { tok ->
                val parsed = runCatching { LocalDate.parse(tok) }.getOrNull()
                if (parsed != null && !parsed.isAfter(today)) return parsed
            }
        return when {
            norm.contains("hom kia") -> today.minusDays(2)
            norm.contains("hom qua") || norm.contains("dem qua") -> today.minusDays(1)
            norm.contains("hom nay") || norm.contains("sang nay") || norm.contains("trua nay") ||
                norm.contains("chieu nay") || norm.contains("toi nay") -> today
            else -> null
        }
    }

    private fun normalizeAscii(s: String): String {
        val decomposed = Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
        val sb = StringBuilder(decomposed.length)
        for (ch in decomposed) {
            if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) {
                sb.append(
                    when (ch) {
                        'đ' -> 'd'
                        'Đ' -> 'D'
                        else -> ch.lowercaseChar()
                    }
                )
            }
        }
        return sb.toString().trim().replace('\n', ' ')
    }
}
