package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActivityProposal
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.TimeCategory
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Kết quả sau khi điều phối & hợp nhất ngữ cảnh hội thoại nhiều lượt cho một lượt Chat.
 * Dùng chung cho Chat trong app và Chat nổi.
 */
data class ReconciledChatOutcome(
    val outcome: ParseChatOutcome,
    val replacesDraftId: String? = null,
    val pendingClarification: PendingProposalContext? = null,
    val suggestionChips: List<String> = emptyList(),
    val explicitTagCleared: Boolean = false
)

/**
 * Ảnh chụp nhẹ của một bản nháp đề xuất đang mở trong Chat (trong app hoặc nổi),
 * giúp lượt chỉnh sửa / trả lời tiếp theo cập nhật đúng thẻ thay vì tạo thẻ mới trùng lặp.
 */
data class OpenChatDraftSnapshot(
    val id: String,
    val title: String,
    val categoryKey: String,
    val tagId: String? = null,
    val dateLocal: String,
    val startLocal: String? = null,
    val endLocal: String? = null,
    val durationMinutes: Int? = null,
    val isFuturePlanned: Boolean = false,
    val taskId: String? = null
)

/**
 * Bộ máy thuần Kotlin (ZERO Regex mới, 100% JVM-testable) cho vòng hội thoại nhiều lượt và bảo toàn Tag:
 * 1. Nhận diện yêu cầu xếp lịch còn thiếu giờ/thời lượng ("Xếp buổi ôn IELTS ngày mai") -> dựng `PendingProposalContext`.
 * 2. Nhận diện câu trả lời bổ sung ("20h, học 45 phút nhé", "đổi sang 19h30", "bỏ tag") và hợp nhất với đề xuất đang mở,
 *    giữ nguyên ngày mục tiêu, Tag đã chọn/gọi tên và tên hoạt động gốc.
 * 3. Bảo vệ không suy đoán Tag khi câu mơ hồ ("mai học tiếng Anh" không tự biến thành Tag "IELTS").
 */
object WallyConversationEngine {

    private val vi = Locale("vi", "VN")
    private val hmFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private val scheduleVerbs = listOf("xep lich", "sap xep", "dat lich", "bo tri", "len lich", "xep")
    private val explicitClearTagPhrases = listOf(
        "bo tag", "go tag", "xoa tag", "khong gan tag", "khong dung tag", "bo nhan tag"
    )
    private val refinementVerbs = listOf(
        "doi sang", "doi thanh", "chuyen sang", "sua thanh", "sua gio", "doi gio", "lui sang",
        "day sang", "chon", "ok", "duoc", "nhung", "hay la", "bo tag", "go tag", "gan tag"
    )
    private val genericTitles = setOf(
        "hoat dong", "hoc", "on", "tap", "lam", "lam viec", "choi", "ngu", "nghi", "doc",
        "buoi hoc", "buoi on", "hoat dong moi"
    )

    fun extractExplicitClockHm(raw: String): String? {
        val norm = normalizeAscii(raw)
        if (norm.isEmpty()) return null
        val tokens = tokenizeAscii(norm)
        for (i in tokens.indices) {
            val tok = tokens[i]
            val prev = tokens.getOrNull(i - 1).orEmpty()
            val next = tokens.getOrNull(i + 1).orEmpty()

            // Dạng HH:mm
            val colonIdx = tok.indexOf(':')
            if (colonIdx in 1..2 && tok.length <= 5) {
                val h = tok.substring(0, colonIdx).toIntOrNull()
                val m = tok.substring(colonIdx + 1).toIntOrNull()
                if (h != null && m != null && h in 0..23 && m in 0..59) {
                    val period = if (next in setOf("sang", "trua", "chieu", "toi", "dem")) next else null
                    val hh = adjustHourForPeriod(h, period)
                    return "%02d:%02d".format(hh, m)
                }
            }

            // Dạng 20h, 19h30, 8h tối, 7h30 sáng
            val hPos = tok.indexOf('h')
            if (hPos in 1..2) {
                val hPart = tok.substring(0, hPos)
                val mPartRaw = tok.substring(hPos + 1)
                if (hPart.all(Char::isDigit) && (mPartRaw.isEmpty() || (mPartRaw.length in 1..2 && mPartRaw.all(Char::isDigit)))) {
                    val h = hPart.toIntOrNull() ?: continue
                    val m = if (mPartRaw.isEmpty()) 0 else mPartRaw.toIntOrNull() ?: continue
                    if (h in 0..23 && m in 0..59) {
                        val period = if (next in setOf("sang", "trua", "chieu", "toi", "dem")) next else null
                        val hasClockCue = prev in setOf("luc", "vao", "tu", "den", "toi", "sang", "thanh", "gio") ||
                            period != null ||
                            h > 12 ||
                            m > 0 ||
                            hasWordBoundarySeparatorAfter(raw, hPart + "h")
                        // Tránh nhầm độ dài "học 2h" với giờ đồng hồ 02:00 nếu đứng sau tên hoạt động và không có chỉ dấu giờ.
                        val isLikelyDurationOnly = !hasClockCue && h in 1..12 && m == 0
                        if (!isLikelyDurationOnly) {
                            val hh = adjustHourForPeriod(h, period)
                            return "%02d:%02d".format(hh, m)
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Tách danh sách thời lượng (phút) mà người dùng nêu rõ, KHÔNG nhầm mốc giờ đồng hồ kiểu "20h", "19h30", "8h tối" hay "8h,".
     */
    private fun extractNonClockDurationMinutesList(raw: String): List<Int> {
        val restored = VietnameseChatParser.restoreUnaccented(Normalizer.normalize(raw, Normalizer.Form.NFC).lowercase(vi))
        val tokens = tokenizeAscii(normalizeAscii(restored))
        val out = mutableListOf<Int>()
        for (i in tokens.indices) {
            val tok = tokens[i]
            val prev = tokens.getOrNull(i - 1).orEmpty()
            val next = tokens.getOrNull(i + 1).orEmpty()

            if ((tok.endsWith("p") || tok.endsWith("phut") || tok.endsWith("m")) && tok.length >= 2) {
                val numStr = tok.removeSuffix("phut").removeSuffix("p").removeSuffix("m")
                val v = numStr.toIntOrNull()
                if (v != null && v in 1..1440) {
                    out += v
                    continue
                }
            }
            if (tok.all(Char::isDigit) && next in setOf("phut", "min", "tieng")) {
                val v = tok.toIntOrNull() ?: continue
                val mins = if (next == "tieng") v * 60 else v
                if (mins in 1..1440) {
                    out += mins
                    continue
                }
            }
            if (tok.all(Char::isDigit) && next == "gio") {
                val afterNext = tokens.getOrNull(i + 2).orEmpty()
                if (prev !in setOf("luc", "vao", "tu", "den", "toi") &&
                    afterNext !in setOf("sang", "trua", "chieu", "toi", "dem")
                ) {
                    val v = tok.toIntOrNull() ?: continue
                    if (v in 1..24) {
                        out += v * 60
                        continue
                    }
                }
            }
            if (tok.endsWith("h") && tok.length in 2..3) {
                val nStr = tok.dropLast(1)
                val v = nStr.toIntOrNull()
                if (v != null && v in 1..12 && prev !in setOf("luc", "vao", "tu", "den", "toi", "sang", "thanh") &&
                    next !in setOf("sang", "trua", "chieu", "toi", "dem") && !hasWordBoundarySeparatorAfter(raw, tok)
                ) {
                    out += v * 60
                }
            }
        }
        return out
    }

    /** Tách thời lượng đầu tiên (phút) không phải mốc giờ đồng hồ. */
    fun extractClarificationDurationMinutes(raw: String): Int? = extractNonClockDurationMinutesList(raw).firstOrNull()

    fun hasSchedulingIntentWithoutTime(raw: String): Boolean {
        val norm = normalizeAscii(raw)
        if (norm.isEmpty()) return false
        val hasSched = scheduleVerbs.any { v -> hasPhrase(norm, v) }
        if (!hasSched) return false
        val hasClock = extractExplicitClockHm(raw) != null || VietnameseChatParser.extractTimeRange(
            VietnameseChatParser.restoreUnaccented(raw.lowercase(vi))
        ) != null
        return !hasClock
    }

    fun hasExplicitDateCue(raw: String): Boolean {
        val norm = normalizeAscii(raw)
        if (listOf(
                "hom nay", "toi nay", "sang nay", "chieu nay", "trua nay", "dem nay",
                "hom qua", "dem qua", "hom kia", "ngay mai", "ngay kia", "ngay mot", "cuoi tuan"
            ).any { hasPhrase(norm, it) }
        ) {
            return true
        }
        val tokens = tokenizeAscii(norm)
        if (tokens.any { it == "mai" }) return true
        return tokens.any { t ->
            (t.contains('/') && t.any(Char::isDigit)) ||
                (t.count { it == '-' } == 2 && t.length == 10 && t[4] == '-')
        }
    }

    fun containsExplicitClearTagCommand(raw: String): Boolean {
        val norm = normalizeAscii(raw)
        return explicitClearTagPhrases.any { hasPhrase(norm, it) }
    }

    fun looksLikeFollowUpToPendingDraft(
        raw: String,
        pending: PendingProposalContext?,
        tags: List<Tag>
    ): Boolean {
        if (pending == null) return false
        val norm = normalizeAscii(raw)
        if (norm.isEmpty()) return false
        if (CancelCommandParser.parse(raw) != null || WallyTurnPolicy.isStatusQuestion(raw)) return false
        if (hasPhrase(norm, "hom qua") || hasPhrase(norm, "hom kia") || hasPhrase(norm, "tuan truoc")) return false
        if (listOf("bao nhieu", "bao lau", "thong ke", "tong ket", "bao cao", "khung gio", "nhieu nhat", "it nhat").any { hasPhrase(norm, it) }) {
            return false
        }

        val hasClock = extractExplicitClockHm(raw) != null
        val nonClockDurations = extractNonClockDurationMinutesList(raw)
        val hasDur = nonClockDurations.isNotEmpty()
        val hasClearTag = containsExplicitClearTagCommand(raw)
        val hasRefinementCue = refinementVerbs.any { hasPhrase(norm, it) }
        val explicitTag = TagRules.findExplicitMatchesInText(raw, tags).firstOrNull()
        val tokenCount = tokenizeAscii(norm).size
        val durationCount = nonClockDurations.size

        if (pending.draftId == null) {
            if (durationCount >= 2) return false
            // Đang chờ người dùng trả lời câu hỏi làm rõ: giờ / thời lượng / tag / đổi lựa chọn đều là lượt nối tiếp.
            return hasClock ||
                hasClearTag ||
                explicitTag != null ||
                hasRefinementCue ||
                (hasDur && !hasExplicitDateCue(raw)) ||
                (tokenCount <= 6 && !raw.trim().endsWith("?"))
        }
        // Đã có thẻ nháp đang mở trong danh sách chờ: cập nhật tại chỗ khi người dùng sửa giờ/thời lượng/tag hoặc trả lời ngắn về khung giờ.
        if (hasRefinementCue || hasClearTag) return true
        if (durationCount <= 1 && (hasClock || hasDur) && !hasExplicitDateCue(raw) && tokenCount <= 8) return true
        return false
    }

    fun buildClarificationCandidate(
        userText: String,
        route: ContextRouteDecision,
        tags: List<Tag>,
        tasks: List<TaskItem> = emptyList(),
        today: LocalDate,
        reply: String? = null
    ): PendingProposalContext? {
        val norm = normalizeAscii(userText)
        if (norm.isEmpty()) return null
        if (CancelCommandParser.parse(userText) != null || WallyTurnPolicy.isStatusQuestion(userText)) return null
        val isPureQuery = QueryIntentParser.parse(VietnameseChatParser.restoreUnaccented(norm), today) is ChatRoute.Query
        if (isPureQuery) return null

        val explicitTag = TagRules.findExplicitMatchesInText(userText, tags).firstOrNull()
        val matchedTask = tasks.firstOrNull { t ->
            !t.isDone && TagRules.nameKey(t.title).let { tk -> tk.length >= 3 && norm.contains(tk) }
        }
        val tag = explicitTag ?: matchedTask?.tagId?.let { id -> tags.firstOrNull { !it.isArchived && it.id == id } }
        val targetDate = route.targetDate ?: AdaptiveContextRouter.resolveTargetDate(
            VietnameseChatParser.restoreUnaccented(userText.lowercase(vi)),
            today,
            null,
            LocalTime.NOON
        )
        val startHm = extractExplicitClockHm(userText)
        val dur = extractClarificationDurationMinutes(userText)
        val hasAskBack = reply?.trim()?.endsWith("?") == true
        val schedNoTime = hasSchedulingIntentWithoutTime(userText)

        if (!schedNoTime && !hasAskBack) return null
        // Tránh mở clarification cho câu tán gẫu không liên quan hoạt động/ngày/tag
        if (!schedNoTime && explicitTag == null && matchedTask == null && route.focusCategory == null && !hasExplicitDateCue(userText) && dur == null && startHm == null) {
            return null
        }

        val catKey = tag?.category?.key
            ?: matchedTask?.category?.key
            ?: route.focusCategory?.key
            ?: VietnameseChatParser.classifyCategory(VietnameseChatParser.restoreUnaccented(userText.lowercase(vi)))
        val cleanTitle = extractCleanActivityTitle(userText, tag, TimeCategory.fromKeyOrDefault(catKey))
        val awaiting = buildSet {
            if (startHm == null) add("startLocal")
            if (dur == null) add("durationMinutes")
        }
        return PendingProposalContext(
            draftId = null,
            title = cleanTitle,
            categoryKey = catKey,
            tagId = tag?.id,
            tagName = tag?.name,
            dateLocal = targetDate.toString(),
            startLocal = startHm,
            durationMinutes = dur,
            isFuturePlanned = targetDate.isAfter(today) || schedNoTime,
            taskId = matchedTask?.id,
            awaitingFields = awaiting
        )
    }

    fun reconcileTurnOutcome(
        userText: String,
        rawOutcome: ParseChatOutcome,
        route: ContextRouteDecision,
        adaptiveContext: AdaptivePromptContext?,
        conversationState: ConversationState?,
        openDrafts: List<OpenChatDraftSnapshot>,
        tags: List<Tag>,
        tasks: List<TaskItem>,
        today: LocalDate,
        nowTime: LocalTime
    ): ReconciledChatOutcome {
        if (rawOutcome.fault != null) {
            return ReconciledChatOutcome(outcome = rawOutcome)
        }
        val activeTags = tags.filter { !it.isArchived }
        val activePending = resolveActivePendingContext(userText, conversationState, openDrafts, activeTags)
        val clearTag = containsExplicitClearTagCommand(userText)
        val isFollowUp = activePending != null && looksLikeFollowUpToPendingDraft(userText, activePending, activeTags)

        // Trường hợp người dùng vừa hỏi "Xếp buổi ôn IELTS ngày mai" (thiếu giờ), nếu AI vội trả lời 1 đề xuất tự đoán giờ hoặc chưa hỏi lại:
        val schedNoTime = hasSchedulingIntentWithoutTime(userText)
        if (!isFollowUp && schedNoTime && rawOutcome.queries.isEmpty()) {
            val allUnbacked = rawOutcome.proposals.none { p ->
                p.timeEvidence == "explicit_range" && !p.startLocal.isNullOrBlank() && extractExplicitClockHm(userText) != null
            }
            if (rawOutcome.proposals.isEmpty() || allUnbacked) {
                val pending = buildClarificationCandidate(
                    userText = userText,
                    route = route,
                    tags = activeTags,
                    tasks = tasks,
                    today = today,
                    reply = rawOutcome.reply
                )
                val askQuestion = rawOutcome.reply?.takeIf { it.trim().endsWith("?") }
                    ?: buildScheduleClarifyQuestion(pending, adaptiveContext?.feasibleSlots.orEmpty(), today)
                val chips = buildClarificationSuggestionChips(pending, adaptiveContext?.feasibleSlots.orEmpty(), askQuestion)
                return ReconciledChatOutcome(
                    outcome = rawOutcome.copy(
                        proposals = emptyList(),
                        reply = askQuestion
                    ),
                    pendingClarification = pending,
                    suggestionChips = chips
                )
            }
        }

        // Lượt 2: cập nhật / hoàn thiện đề xuất đang mở ("20h, học 45 phút nhé")
        if (isFollowUp && rawOutcome.queries.isEmpty()) {
            val completed = reconcileFollowUpProposal(
                userText = userText,
                rawOutcome = rawOutcome,
                pending = activePending!!,
                tags = activeTags,
                today = today,
                nowTime = nowTime,
                clearTag = clearTag
            )
            if (completed != null) {
                val (proposal, remainingPending) = completed
                val nextChips = buildClarificationSuggestionChips(
                    remainingPending,
                    adaptiveContext?.feasibleSlots.orEmpty(),
                    rawOutcome.reply
                )
                val replyText = if (remainingPending != null) {
                    rawOutcome.reply?.takeIf { it.trim().endsWith("?") }
                        ?: buildScheduleClarifyQuestion(remainingPending, adaptiveContext?.feasibleSlots.orEmpty(), today)
                } else null
                return ReconciledChatOutcome(
                    outcome = rawOutcome.copy(
                        proposals = if (remainingPending == null) listOf(proposal) else emptyList(),
                        reply = replyText
                    ),
                    replacesDraftId = if (remainingPending == null) activePending.draftId else null,
                    pendingClarification = remainingPending,
                    suggestionChips = nextChips,
                    explicitTagCleared = clearTag
                )
            }
        }

        // Luồng bình thường: gán Tag xác định + taskId từ tên việc/clause (chống suy đoán mù từ "tiếng Anh" -> "IELTS")
        val isSingle = rawOutcome.proposals.size <= 1
        val taggedProposals = rawOutcome.proposals.map { p ->
            val explicitMentioned = TagRules.findExplicitMatchesInText(userText, activeTags)
            val matchedTask = p.taskId?.let { id -> tasks.firstOrNull { it.id == id } }
                ?: tasks.firstOrNull { t -> !t.isDone && TagRules.nameKey(t.title) == TagRules.nameKey(p.title) }
            val resolvedTag = TagRules.resolveProposalTag(
                title = p.title,
                rawInput = userText,
                tags = activeTags,
                proposedTagId = p.tagId,
                proposedTagName = p.tagName,
                linkedTaskTagId = matchedTask?.tagId,
                inheritedDraftTagId = null,
                isSingleProposal = isSingle,
                explicitClearRequested = clearTag
            )
            val effectiveTag = resolvedTag ?: if (isSingle && explicitMentioned.size == 1 && !clearTag) {
                // Nếu AI rút gọn tiêu đề (vd "Luyện nghe" từ "xếp 1 giờ luyện nghe IELTS") vẫn giữ tag gọi tên tường minh
                explicitMentioned.first()
            } else null
            p.copy(
                tagId = effectiveTag?.id,
                tagName = effectiveTag?.name,
                category = effectiveTag?.category?.key ?: p.category,
                taskId = p.taskId ?: matchedTask?.id
            )
        }

        val pendingFromClarify = if (taggedProposals.isEmpty() && rawOutcome.queries.isEmpty()) {
            buildClarificationCandidate(
                userText = userText,
                route = route,
                tags = activeTags,
                tasks = tasks,
                today = today,
                reply = rawOutcome.reply
            )
        } else null
        val chips = buildClarificationSuggestionChips(
            pendingFromClarify,
            adaptiveContext?.feasibleSlots.orEmpty(),
            rawOutcome.reply
        )

        return ReconciledChatOutcome(
            outcome = rawOutcome.copy(proposals = taggedProposals),
            pendingClarification = pendingFromClarify,
            suggestionChips = chips,
            explicitTagCleared = clearTag
        )
    }

    private fun resolveActivePendingContext(
        userText: String,
        conversationState: ConversationState?,
        openDrafts: List<OpenChatDraftSnapshot>,
        activeTags: List<Tag>
    ): PendingProposalContext? {
        val convPending = conversationState?.pendingProposal
        if (convPending != null) return convPending
        if (openDrafts.size == 1) {
            val single = openDrafts.first()
            val candidate = PendingProposalContext(
                draftId = single.id,
                title = single.title,
                categoryKey = single.categoryKey,
                tagId = single.tagId,
                tagName = single.tagId?.let { tid -> activeTags.firstOrNull { it.id == tid }?.name },
                dateLocal = single.dateLocal,
                startLocal = single.startLocal,
                endLocal = single.endLocal,
                durationMinutes = single.durationMinutes,
                isFuturePlanned = single.isFuturePlanned,
                taskId = single.taskId
            )
            if (looksLikeFollowUpToPendingDraft(userText, candidate, activeTags)) {
                return candidate
            }
        }
        return null
    }

    private fun reconcileFollowUpProposal(
        userText: String,
        rawOutcome: ParseChatOutcome,
        pending: PendingProposalContext,
        tags: List<Tag>,
        today: LocalDate,
        nowTime: LocalTime,
        clearTag: Boolean
    ): Pair<ActivityProposal, PendingProposalContext?>? {
        val aiProp = rawOutcome.proposals.firstOrNull()
        val parsedDateExplicit = if (hasExplicitDateCue(userText)) {
            AdaptiveContextRouter.resolveTargetDate(
                VietnameseChatParser.restoreUnaccented(userText.lowercase(vi)),
                today,
                null,
                nowTime
            )
        } else null
        val effectiveDate = (parsedDateExplicit ?: LocalDate.parse(pending.dateLocal)).toString()
        val parsedClockStart = extractExplicitClockHm(userText)
        val parsedDur = extractClarificationDurationMinutes(userText)

        val effectiveTag = TagRules.resolveProposalTag(
            title = aiProp?.title ?: pending.title,
            rawInput = userText,
            tags = tags,
            proposedTagId = aiProp?.tagId,
            proposedTagName = aiProp?.tagName,
            linkedTaskTagId = pending.tagId,
            inheritedDraftTagId = pending.tagId,
            isSingleProposal = true,
            explicitClearRequested = clearTag
        )
        val effectiveCategory = effectiveTag?.category?.key ?: aiProp?.category ?: pending.categoryKey
        val effectiveTitle = resolveMergedTitle(
            aiTitle = aiProp?.title,
            pendingTitle = pending.title,
            tag = effectiveTag,
            userText = userText
        )

        val resolvedStart = parsedClockStart ?: aiProp?.startLocal ?: pending.startLocal
        val resolvedDur = parsedDur
            ?: aiProp?.durationMinutes?.takeIf { it > 0 && (parsedClockStart == null || aiProp.timeEvidence == "explicit_range") }
            ?: pending.durationMinutes

        if (resolvedStart == null || resolvedDur == null) {
            val nextPending = pending.copy(
                title = effectiveTitle,
                categoryKey = effectiveCategory,
                tagId = effectiveTag?.id,
                tagName = effectiveTag?.name,
                dateLocal = effectiveDate,
                startLocal = resolvedStart,
                durationMinutes = resolvedDur,
                explicitTagCleared = clearTag,
                awaitingFields = buildSet {
                    if (resolvedStart == null) add("startLocal")
                    if (resolvedDur == null) add("durationMinutes")
                }
            )
            return ActivityProposal(
                id = aiProp?.id ?: "clarify_${UUID.randomUUID()}",
                rawInput = userText,
                title = effectiveTitle,
                category = effectiveCategory,
                durationSeconds = ((resolvedDur ?: 45) * 60L),
                dateLocal = effectiveDate,
                startLocal = resolvedStart,
                endLocal = null,
                timeEvidence = "duration_only",
                missingFields = nextPending.awaitingFields.toList(),
                needsConfirmation = true,
                isFuturePlanned = pending.isFuturePlanned,
                tagId = effectiveTag?.id,
                tagName = effectiveTag?.name,
                taskId = pending.taskId
            ) to nextPending
        }

        val resolvedEnd = aiProp?.endLocal?.takeIf {
            parsedClockStart == null && parsedDur == null && aiProp.startLocal == resolvedStart
        } ?: addMinutesToHm(resolvedStart, resolvedDur)

        val targetDate = runCatching { LocalDate.parse(effectiveDate) }.getOrDefault(today)
        val startLt = runCatching { LocalTime.parse(resolvedStart) }.getOrNull()
        val futurePlanned = when {
            targetDate.isAfter(today) -> true
            targetDate.isBefore(today) -> false
            startLt != null -> startLt.isAfter(nowTime)
            else -> pending.isFuturePlanned
        }

        val completed = ActivityProposal(
            id = pending.draftId ?: aiProp?.id ?: "prop_${UUID.randomUUID()}",
            rawInput = userText,
            title = effectiveTitle,
            category = effectiveCategory,
            durationSeconds = resolvedDur.toLong() * 60L,
            dateLocal = effectiveDate,
            startLocal = resolvedStart,
            endLocal = resolvedEnd,
            timeEvidence = "explicit_range",
            missingFields = emptyList(),
            needsConfirmation = true,
            isFuturePlanned = futurePlanned,
            tagId = effectiveTag?.id,
            tagName = effectiveTag?.name,
            taskId = pending.taskId ?: aiProp?.taskId
        )
        return completed to null
    }

    private fun resolveMergedTitle(
        aiTitle: String?,
        pendingTitle: String,
        tag: Tag?,
        userText: String
    ): String {
        val cleanPending = pendingTitle.trim()
        val cleanAi = aiTitle?.trim().orEmpty()
        if (cleanAi.isEmpty()) return cleanPending
        val aiKey = normalizeAscii(cleanAi)
        val pendingKey = normalizeAscii(cleanPending)
        if (aiKey in genericTitles || aiKey == "hoc tap") return cleanPending
        if (tag != null && !aiKey.contains(tag.nameKey) && pendingKey.contains(tag.nameKey)) {
            return cleanPending
        }
        val userKey = normalizeAscii(userText)
        // Nếu người dùng chỉ bổ sung giờ/thời lượng chứ không gõ tên hoạt động mới dài hơn
        if (tokenizeAscii(userKey).size <= 6 && extractExplicitClockHm(userText) != null && pendingKey.isNotBlank()) {
            return cleanPending
        }
        return cleanAi.take(110)
    }

    private fun buildScheduleClarifyQuestion(
        pending: PendingProposalContext?,
        slots: List<FeasibleSlot>,
        today: LocalDate
    ): String {
        val label = pending?.title?.ifBlank { pending.tagName.orEmpty() } ?: "hoạt động này"
        val dayLabel = when (pending?.dateLocal) {
            today.plusDays(1).toString() -> "ngày mai"
            today.toString() -> "hôm nay"
            null -> ""
            else -> "ngày ${pending.dateLocal}"
        }
        val candidateHours = suggestCandidateClockHints(slots)
        val hourHint = if (candidateHours.size >= 2) {
            "lúc ${candidateHours[0]} hay ${candidateHours[1]}"
        } else {
            "lúc mấy giờ"
        }
        val needDur = pending?.durationMinutes == null
        return if (pending?.startLocal == null && needDur) {
            "Bạn muốn $label $dayLabel $hourHint, và trong bao lâu?"
        } else if (pending?.startLocal == null) {
            "Bạn muốn $label $dayLabel $hourHint?"
        } else {
            "Bạn muốn $label ${dayLabel} trong bao lâu?"
        }
    }

    fun buildClarificationSuggestionChips(
        pending: PendingProposalContext?,
        slots: List<FeasibleSlot>,
        reply: String?
    ): List<String> {
        if (pending == null) return emptyList()
        val hours = linkedSetOf<String>()
        if (!reply.isNullOrBlank()) {
            tokenizeAscii(normalizeAscii(reply)).forEach { tok ->
                extractExplicitClockHm(tok)?.let { hours += it }
            }
        }
        if (hours.isEmpty()) {
            hours.addAll(suggestCandidateClockHints(slots))
        }
        if (pending.startLocal != null && pending.durationMinutes == null) {
            return listOf("30 phút", "45 phút", "60 phút")
        }
        val dur = pending.durationMinutes
        val chosen = hours.take(2)
        return when {
            chosen.isEmpty() -> listOf("19h, học 45 phút", "20h, học 45 phút")
            dur != null -> chosen.map { "$it" }
            else -> chosen.map { "$it, học 45 phút" }
        }
    }

    private fun suggestCandidateClockHints(slots: List<FeasibleSlot>): List<String> {
        val anchorMinutes = listOf(19 * 60, 20 * 60, 9 * 60, 14 * 60, 21 * 60)
        val result = mutableListOf<String>()
        for (m in anchorMinutes) {
            if (slots.any { s -> s.startMinute <= m && s.endMinute >= m + 30 }) {
                result += TimeRange.formatHm(m)
            }
            if (result.size >= 2) break
        }
        if (result.isEmpty()) {
            slots.filter { it.startMinute in (6 * 60)..(21 * 60) && it.durationMinutes >= 30 }
                .take(2)
                .forEach { result += it.startHm }
        }
        return if (result.size >= 2) result else listOf("19:00", "20:00")
    }

    private fun extractCleanActivityTitle(raw: String, tag: Tag?, fallbackCat: TimeCategory): String {
        val words = raw.trim().split(' ').filter { it.isNotBlank() }
        val filtered = mutableListOf<String>()
        var skipNextDateWord = false
        for (i in words.indices) {
            val w = words[i]
            val k = normalizeAscii(w)
            if (skipNextDateWord) {
                skipNextDateWord = false
                continue
            }
            if (i == 0 && k in setOf("xep", "hay", "giup", "dat", "len", "sap", "bo")) {
                if (k == "dat" || k == "len" || k == "sap" || k == "bo") {
                    val nextK = normalizeAscii(words.getOrNull(i + 1).orEmpty())
                    if (nextK in setOf("lich", "xep", "tri")) skipNextDateWord = true
                }
                continue
            }
            if (k in setOf("giup", "cho", "minh", "toi", "buoi", "lich", "mot") && filtered.isEmpty()) continue
            if (k == "ngay" && normalizeAscii(words.getOrNull(i + 1).orEmpty()) in setOf("mai", "nay", "kia", "mot")) {
                skipNextDateWord = true
                continue
            }
            if (k in setOf("mai", "hom", "nhe", "nha", "voi") && i >= words.lastIndex - 1) continue
            filtered += w
        }
        val rawTitle = filtered.joinToString(" ").trim(' ', ',', '.', ':', '-', '?')
        val out = rawTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase(vi) else it.toString() }
        return if (out.isNotBlank()) out.take(110) else (tag?.name ?: fallbackCat.displayName)
    }

    private fun addMinutesToHm(startHm: String, durationMinutes: Int): String {
        val start = runCatching { LocalTime.parse(startHm) }.getOrElse { return "23:59" }
        val total = (start.hour * 60 + start.minute) + durationMinutes.coerceIn(1, 1440)
        val wrapped = ((total % 1440) + 1440) % 1440
        return LocalTime.of(wrapped / 60, wrapped % 60).format(hmFormatter)
    }

    private fun adjustHourForPeriod(hour: Int, period: String?): Int = when (period) {
        "chieu", "toi" -> if (hour in 1..11) hour + 12 else hour
        "trua" -> if (hour in 1..10) hour + 12 else hour
        "sang", "dem" -> if (hour == 12) 0 else hour
        else -> hour
    }

    private fun hasWordBoundarySeparatorAfter(raw: String, tokenPrefix: String): Boolean {
        val idx = raw.lowercase(vi).indexOf(tokenPrefix.lowercase(vi))
        if (idx < 0) return false
        val afterIdx = idx + tokenPrefix.length
        val after = raw.getOrNull(afterIdx) ?: return true
        return after == ',' || after == ';' || after == ':'
    }

    private fun normalizeAscii(s: String): String {
        val decomposed = Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
        val sb = StringBuilder(decomposed.length)
        for (ch in decomposed) {
            if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) {
                val mapped = when (ch) {
                    'đ' -> 'd'
                    'Đ' -> 'D'
                    else -> ch.lowercaseChar()
                }
                sb.append(mapped)
            }
        }
        val collapsed = StringBuilder(sb.length)
        var pendingSpace = false
        for (c in sb) {
            if (c.isWhitespace()) {
                pendingSpace = collapsed.isNotEmpty()
            } else {
                if (pendingSpace) {
                    collapsed.append(' ')
                    pendingSpace = false
                }
                collapsed.append(c)
            }
        }
        return collapsed.toString()
    }

    private fun tokenizeAscii(norm: String): List<String> =
        norm.split(' ', ',', ';', '.', '!', '?', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    private fun hasPhrase(haystack: String, phrase: String): Boolean =
        (" $haystack ").contains(" $phrase ")
}
