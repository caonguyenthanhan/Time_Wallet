package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.GoalGroupState
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class GoalRowLabels(
    val headerTargetText: String,
    val progressText: String,
    val statusText: String,
    val accessibilityText: String
)

/**
 * Chuẩn hóa số phút mục tiêu để thanh tiến độ, lời kể Wally và "Phân tích AI" luôn khớp nhau 100%.
 * Quy ước: `remainingMinutes = (targetMinutes - usedMinutes).coerceAtLeast(0)` tránh lệch làm tròn.
 */
data class GoalDisplayMath(
    val usedMinutes: Long,
    val targetMinutes: Long,
    val remainingMinutes: Long,
    val overMinutes: Long,
    val usedLabel: String,
    val targetLabel: String,
    val remainingLabel: String,
    val overLabel: String
) {
    companion object {
        fun from(usedSeconds: Long, targetSeconds: Long): GoalDisplayMath {
            val usedM = roundedMinutes(usedSeconds)
            val targetM = roundedMinutes(targetSeconds).coerceAtLeast(1L)
            val remM = (targetM - usedM).coerceAtLeast(0L)
            val overM = (usedM - targetM).coerceAtLeast(0L)
            return GoalDisplayMath(
                usedMinutes = usedM,
                targetMinutes = targetM,
                remainingMinutes = remM,
                overMinutes = overM,
                usedLabel = formatGoalMinutes(usedM),
                targetLabel = formatGoalMinutes(targetM),
                remainingLabel = formatGoalMinutes(remM),
                overLabel = formatGoalMinutes(overM)
            )
        }

        /** Nhãn hiển thị dùng chung cho hàng mục tiêu ở GoalsDialog, thẻ Tổng quan và TalkBack. */
        fun rowLabels(summary: GoalRules.Summary): GoalRowLabels {
            val g = summary.latest
            val p = summary.progress
            val per = if (g.period == GoalPeriod.DAY) "ngày" else "tuần"
            val sign = if (g.kind == GoalKind.MIN) "≥ " else "≤ "
            val targetOnly = formatGoalMinutes(roundedMinutes(g.targetSeconds).coerceAtLeast(1L))
            val headerTargetText = "$sign$targetOnly/$per"
            if (p == null) {
                val stateText = if (summary.state == GoalGroupState.PAUSED) "Đang tạm dừng" else "Đã kết thúc"
                return GoalRowLabels(
                    headerTargetText = headerTargetText,
                    progressText = "",
                    statusText = stateText,
                    accessibilityText = "${summary.name}. $stateText"
                )
            }
            val math = from(p.usedSeconds, p.targetSeconds)
            val statusText = when {
                g.kind == GoalKind.MIN && p.met -> "Đã đạt ✓"
                g.kind == GoalKind.MIN && math.remainingMinutes > 0L -> "Còn thiếu ${math.remainingLabel}"
                g.kind == GoalKind.MIN -> "Còn thiếu < 1p"
                p.met -> "Còn ${math.remainingLabel} trong giới hạn"
                math.overMinutes > 0L -> "Vượt ${math.overLabel}"
                else -> "Vượt < 1p"
            }
            return GoalRowLabels(
                headerTargetText = headerTargetText,
                progressText = "${math.usedLabel} / ${math.targetLabel}",
                statusText = statusText,
                accessibilityText = "${summary.name}: ${math.usedLabel} trên ${math.targetLabel}. $statusText"
            )
        }

        fun roundedMinutes(seconds: Long): Long =
            if (seconds <= 0L) 0L else (seconds + 30L) / 60L

        /**
         * Hiển thị thời lượng mục tiêu nhất quán:
         * - 0p -> "0p"
         * - 1..90p -> "${m}p" (ví dụ: 1p/60p -> còn 59p, 0p/60p -> còn 60p)
         * - > 90p -> "${h}h" hoặc "${h}h ${m}p"
         */
        fun formatGoalMinutes(minutes: Long): String {
            if (minutes <= 0L) return "0p"
            if (minutes <= 90L) return "${minutes}p"
            val h = minutes / 60L
            val m = minutes % 60L
            return if (m > 0L) "${h}h ${m}p" else "${h}h"
        }
    }
}

/**
 * Bộ phân tích chứng cứ & lời kể dữ liệu cho thẻ "Wally kể từ dữ liệu" và bảng "Phân tích AI".
 *
 * Nguyên tắc:
 * - Tối đa 1 phát hiện chính + tối đa 1 hành động cụ thể; lưu ý chỉ hiện khi làm thay đổi cách hiểu.
 * - Khi dữ liệu chỉ đủ tính tổng tuần (không có mẫu lặp, biến động cùng kỳ, hay ngữ cảnh mục tiêu rõ ràng),
 *   trả về `null` để ẩn phần lời kể — không lặp lại thứ hạng Ngủ/Công việc hay nhắc gắn Tag chung chung.
 * - Không bao giờ đề xuất khung giờ nằm trong khoảng nghỉ 22:00–06:00 cho Vận động/Học tập/Công việc.
 */
object WallyDataStoryEngine {

    private const val NIGHT_REST_END_MINUTE = 6 * 60     // 06:00
    private const val NIGHT_REST_START_MINUTE = 22 * 60  // 22:00
    private val dmFmt = DateTimeFormatter.ofPattern("dd/MM")

    private data class HistoricalSessionSlot(
        val date: LocalDate,
        val startMinute: Int,
        val endMinute: Int,
        val durationMinutes: Int,
        val title: String
    )

    fun build(
        records: List<LedgerRecord>,
        tags: List<Tag>,
        summaries: List<GoalRules.Summary>,
        goalItems: List<EfficiencyGoalItem>,
        activeTimer: ActiveTimerSession?,
        endDate: LocalDate,
        todayLocal: LocalDate,
        zoneId: ZoneId,
        nowEpochMs: Long
    ): WallyStorySummary? {
        val todayStr = todayLocal.toString()
        val nowZoned = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId)
        val nowMinute = nowZoned.hour * 60 + nowZoned.minute
        val periodStartDefault = endDate.minusDays(6)

        // 1) Ưu tiên phát hiện thiếu Tag CHỈ KHI nó cản trở trực tiếp một mục tiêu theo Tag đang ở mức 0p.
        val tagGoalStory = buildTagBlockingGoalStory(
            records = records,
            tags = tags,
            summaries = summaries,
            goalItems = goalItems,
            endDate = endDate,
            zoneId = zoneId,
            nowEpochMs = nowEpochMs
        )
        if (tagGoalStory != null) return tagGoalStory

        // 2) Phân tích mục tiêu MIN đang chậm: phân biệt chưa dành chỗ / đã dành chỗ chưa ghi nhận / chưa đủ dữ liệu.
        val unmetGoalStory = buildUnmetMinGoalStory(
            records = records,
            tags = tags,
            summaries = summaries,
            activeTimer = activeTimer,
            endDate = endDate,
            todayLocal = todayLocal,
            nowMinute = nowMinute,
            todayStr = todayStr,
            zoneId = zoneId,
            nowEpochMs = nowEpochMs
        )
        if (unmetGoalStory != null) return unmetGoalStory

        // 3) Có phiên quá giờ dự kiến hoặc chồng lấn cần xác nhận/đối soát đang làm thiếu số liệu thực tế.
        val pendingStory = buildPendingConfirmationStory(
            records = records,
            periodStart = periodStartDefault,
            endDate = endDate,
            todayStr = todayStr,
            nowEpochMs = nowEpochMs,
            zoneId = zoneId
        )
        if (pendingStory != null) return pendingStory

        // 4) Đối chiếu cùng phần thời gian đã trôi qua giữa kỳ này và kỳ trước.
        val elapsedComparisonStory = buildSameElapsedComparisonStory(
            records = records,
            activeTimer = activeTimer,
            endDate = endDate,
            todayLocal = todayLocal,
            nowEpochMs = nowEpochMs,
            zoneId = zoneId
        )
        if (elapsedComparisonStory != null) return elapsedComparisonStory

        // 5) Mẫu lặp độ lệch kế hoạch tập trung (>= 2 phiên tập trung có kế hoạch >= 15p).
        val planDriftStory = buildFocusPlanPatternStory(records)
        if (planDriftStory != null) return planDriftStory

        // Chỉ có tổng giờ trong tuần, không đủ căn cứ rút nhận xét -> ẩn phần lời kể.
        return null
    }

    private fun buildTagBlockingGoalStory(
        records: List<LedgerRecord>,
        tags: List<Tag>,
        summaries: List<GoalRules.Summary>,
        goalItems: List<EfficiencyGoalItem>,
        endDate: LocalDate,
        zoneId: ZoneId,
        nowEpochMs: Long
    ): WallyStorySummary? {
        val hintGoal = goalItems.firstOrNull { !it.met && !it.hintVi.isNullOrBlank() } ?: return null
        val summary = summaries.firstOrNull { it.groupId == hintGoal.id } ?: return null
        val progress = summary.progress ?: return null
        val tag = tags.firstOrNull { it.id == summary.latest.targetKey } ?: return null
        val (pStart, pEnd) = GoalRules.periodBounds(summary.latest.period, endDate)
        val searchFrom = minOf(pStart, endDate.minusDays(6))

        val untaggedSessions = records.filter { r ->
            !r.isCancelled &&
                !r.isNeedsReconciliation &&
                r.layer != TimeLayer.PLANNED &&
                !r.status.equals("PLANNED", ignoreCase = true) &&
                r.category == tag.category &&
                r.tagId == null &&
                LedgerAnalyticsCalculator.recordActualSecondsInDateRange(
                    record = r,
                    from = searchFrom,
                    to = pEnd,
                    nowEpochMs = nowEpochMs,
                    zoneId = zoneId
                ) > 0L
        }.sortedByDescending { it.startedAt ?: it.createdAt }

        val untaggedSecs = untaggedSessions.sumOf { r ->
            LedgerAnalyticsCalculator.recordActualSecondsInDateRange(
                record = r,
                from = searchFrom,
                to = pEnd,
                nowEpochMs = nowEpochMs,
                zoneId = zoneId
            )
        }
        if (untaggedSessions.isEmpty() || untaggedSecs < 900L) return null

        val math = GoalDisplayMath.from(progress.usedSeconds, progress.targetSeconds)
        val untaggedDurText = LedgerAnalyticsCalculator.formatCompactDurationSeconds(untaggedSecs)
        val btnLabel = "Gắn Tag cho ${tag.category.displayName}"
        val evidence = untaggedSessions.take(6).map { r ->
            val secs = LedgerAnalyticsCalculator.recordActualSecondsInDateRange(
                record = r,
                from = searchFrom,
                to = pEnd,
                nowEpochMs = nowEpochMs,
                zoneId = zoneId
            )
            WallyEvidenceItem(
                dateLocal = r.date,
                label = "${r.title} (${tag.category.displayName})",
                detailVi = "${formatTimeSpan(r, zoneId)} · ${LedgerAnalyticsCalculator.formatCompactDurationSeconds(secs)} · chưa gắn Tag"
            )
        }

        return WallyStorySummary(
            mainFinding = "Mục tiêu \"${summary.name}\" đang ở ${math.usedLabel}/${math.targetLabel} (còn ${math.remainingLabel}), nhưng có ${untaggedSessions.size} phiên ${tag.category.displayName} ($untaggedDurText) chưa gắn Tag.",
            findingDetail = "Các phiên này chưa mang Tag \"${tag.name}\" nên chưa được cộng vào tiến độ mục tiêu.",
            actionLabel = btnLabel,
            action = WallyStoryAction.OpenTagAssign(btnLabel, tag.category.key),
            uncertaintyNote = "Tiến độ ${math.usedLabel} hiện chưa phản ánh ${untaggedSessions.size} phiên ${tag.category.displayName} chưa gắn Tag.",
            comparisonPeriodVi = "Kỳ mục tiêu: ${pStart.format(dmFmt)}–${pEnd.format(dmFmt)} (${math.usedLabel}/${math.targetLabel}, còn ${math.remainingLabel}).",
            rationaleVi = "Wally chỉ nhắc thiếu Tag vì mục tiêu \"${summary.name}\" theo dõi riêng Tag \"${tag.name}\" nhưng tìm thấy $untaggedDurText cùng danh mục chưa gắn Tag.",
            evidenceItems = evidence
        )
    }

    private fun buildUnmetMinGoalStory(
        records: List<LedgerRecord>,
        tags: List<Tag>,
        summaries: List<GoalRules.Summary>,
        activeTimer: ActiveTimerSession?,
        endDate: LocalDate,
        todayLocal: LocalDate,
        nowMinute: Int,
        todayStr: String,
        zoneId: ZoneId,
        nowEpochMs: Long
    ): WallyStorySummary? {
        val candidateGoals = summaries.filter {
            it.progress != null && !it.progress.met && it.latest.kind == GoalKind.MIN
        }
        if (candidateGoals.isEmpty()) return null

        val prioritized = candidateGoals.sortedWith(
            compareByDescending<GoalRules.Summary> { it.latest.isPriority }
                .thenBy { it.progress!!.fraction }
                .thenByDescending { it.progress!!.remainingSeconds }
        )

        for (summary in prioritized) {
            val progress = summary.progress ?: continue
            val math = GoalDisplayMath.from(progress.usedSeconds, progress.targetSeconds)
            val goal = summary.latest
            val (pStart, pEnd) = GoalRules.periodBounds(goal.period, endDate)
            val pStartMs = pStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val pEndMs = pEnd.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val lookbackStart = pStart.minusDays(21)

            val matchingRecords = records.filter { !it.isCancelled && GoalRules.qualifies(goal, it) }

            val pastPlannedUnconfirmed = matchingRecords.filter { r ->
                val s = r.startedAt ?: return@filter false
                val e = r.endedAt ?: return@filter false
                val inPeriod = s < pEndMs && e > pStartMs
                if (!inPeriod || e > nowEpochMs) return@filter false
                val state = r.resolveConfirmationStateAt(nowEpochMs, todayStr)
                r.layer == TimeLayer.PLANNED ||
                    r.status.equals("PLANNED", ignoreCase = true) ||
                    state == ConfirmationState.PLANNED ||
                    state == ConfirmationState.PENDING_CONFIRMATION
            }.sortedBy { it.startedAt ?: it.createdAt }

            val upcomingPlanned = matchingRecords.filter { r ->
                val s = r.startedAt
                val e = r.endedAt
                val d = runCatching { LocalDate.parse(r.date) }.getOrNull()
                val inPeriod = if (s != null && e != null) s < pEndMs && e > pStartMs else d != null && !d.isBefore(pStart) && !d.isAfter(pEnd)
                if (!inPeriod) return@filter false
                val state = r.resolveConfirmationStateAt(nowEpochMs, todayStr)
                val futureEnd = e == null || e > nowEpochMs
                futureEnd && (r.layer == TimeLayer.PLANNED || r.status.equals("PLANNED", ignoreCase = true) || state == ConfirmationState.PLANNED)
            }.sortedBy { it.startedAt ?: Long.MAX_VALUE }

            val prevCompletedHistory = extractCompletedSlots(
                records = matchingRecords,
                from = lookbackStart,
                to = pStart.minusDays(1),
                nowEpochMs = nowEpochMs,
                zoneId = zoneId
            )
            val allCompletedHistory = extractCompletedSlots(
                records = matchingRecords,
                from = lookbackStart,
                to = endDate,
                nowEpochMs = nowEpochMs,
                zoneId = zoneId
            )

            // Đếm các buổi dự kiến quá giờ trùng với Công việc/Học tập thực tế trong khung giờ đó.
            val nonGoalWorkOrLearn = records.filter { r ->
                !r.isCancelled &&
                    !r.isNeedsReconciliation &&
                    r.layer != TimeLayer.PLANNED &&
                    !r.status.equals("PLANNED", ignoreCase = true) &&
                    r.startedAt != null &&
                    r.endedAt != null &&
                    r.category in setOf(TimeCategory.WORK, TimeCategory.LEARN) &&
                    !GoalRules.qualifies(goal, r)
            }

            data class PlannedOverlapInfo(
                val planned: LedgerRecord,
                val overlappedBy: LedgerRecord?,
                val startMinute: Int
            )

            val overlapInfos = pastPlannedUnconfirmed.map { planned ->
                val ps = planned.startedAt!!
                val pe = planned.endedAt!!
                val startMin = Instant.ofEpochMilli(ps).atZone(zoneId).let { it.hour * 60 + it.minute }
                val overlapHit = nonGoalWorkOrLearn.firstOrNull { w ->
                    val ws = w.startedAt!!
                    val we = w.endedAt!!
                    ws < pe && we > ps
                }
                PlannedOverlapInfo(planned, overlapHit, startMin)
            }
            val coincidedCount = overlapInfos.count { it.overlappedBy != null }
            val predominantCoincideCat = overlapInfos.mapNotNull { it.overlappedBy?.category }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            val anyLongCoincide = overlapInfos.any { info ->
                val w = info.overlappedBy ?: return@any false
                val planSec = w.plannedDurationSeconds
                (planSec != null && w.durationSeconds > planSec) || w.durationSeconds >= 3 * 3600L
            }
            val allPlannedInEvening = overlapInfos.isNotEmpty() && overlapInfos.all { it.startMinute >= 17 * 60 }

            val timedDaysCount = countDaysWithTimedData(records, minOf(pStart, endDate.minusDays(6)), endDate, nowEpochMs, zoneId)
            val hasAnyPatternOrSchedule = pastPlannedUnconfirmed.isNotEmpty() ||
                upcomingPlanned.isNotEmpty() ||
                allCompletedHistory.isNotEmpty() ||
                timedDaysCount > 0

            // Nếu thậm chí mục tiêu hoàn toàn không có lịch sử, không có buổi dự kiến, và tuần không có bất kỳ bản ghi gắn giờ nào:
            // tránh suy đoán, chỉ báo chưa đủ dữ liệu nếu thực sự có bối cảnh mục tiêu.
            if (!hasAnyPatternOrSchedule && candidateGoals.size == 1 && records.isEmpty()) {
                return null
            }

            val plannedUpcomingSeconds = upcomingPlanned.sumOf { it.durationSeconds }
            val upcomingAlreadyCoversGap = plannedUpcomingSeconds >= progress.remainingSeconds

            val slot = if (!upcomingAlreadyCoversGap && math.remainingMinutes > 0L) {
                findBestFutureDaytimeSlot(
                    goal = goal,
                    goalTitle = resolveDefaultSlotTitle(goal, summary.name, tags),
                    remainingMinutes = math.remainingMinutes,
                    historySlots = if (prevCompletedHistory.isNotEmpty()) prevCompletedHistory else allCompletedHistory,
                    records = records,
                    activeTimer = activeTimer,
                    pEnd = pEnd,
                    todayLocal = todayLocal,
                    nowMinute = nowMinute,
                    zoneId = zoneId,
                    nowEpochMs = nowEpochMs
                )
            } else {
                null
            }

            val evidenceItems = mutableListOf<WallyEvidenceItem>()
            overlapInfos.forEach { info ->
                val w = info.overlappedBy
                val statusDetail = if (w != null) {
                    val longText = if ((w.plannedDurationSeconds != null && w.durationSeconds > w.plannedDurationSeconds) || w.durationSeconds >= 3 * 3600L) {
                        "kéo dài"
                    } else {
                        "trùng giờ"
                    }
                    "${formatTimeSpan(info.planned, zoneId)} · chưa ghi nhận hoàn thành (${w.category.displayName} $longText ${formatTimeSpan(w, zoneId)})"
                } else {
                    "${formatTimeSpan(info.planned, zoneId)} · chưa ghi nhận hoàn thành"
                }
                evidenceItems += WallyEvidenceItem(
                    dateLocal = info.planned.date,
                    label = "${info.planned.title} (dự kiến)",
                    detailVi = statusDetail
                )
            }
            if (prevCompletedHistory.isNotEmpty()) {
                prevCompletedHistory.take(4).forEach { h ->
                    evidenceItems += WallyEvidenceItem(
                        dateLocal = h.date.toString(),
                        label = "${h.title} (kỳ trước)",
                        detailVi = "${TimeRange.formatHm(h.startMinute)}–${TimeRange.formatHm(h.endMinute)} (${h.durationMinutes}p)"
                    )
                }
            } else {
                allCompletedHistory.take(4).forEach { h ->
                    evidenceItems += WallyEvidenceItem(
                        dateLocal = h.date.toString(),
                        label = "${h.title} (đã ghi nhận)",
                        detailVi = "${TimeRange.formatHm(h.startMinute)}–${TimeRange.formatHm(h.endMinute)} (${h.durationMinutes}p)"
                    )
                }
            }
            upcomingPlanned.take(3).forEach { u ->
                evidenceItems += WallyEvidenceItem(
                    dateLocal = u.date,
                    label = "${u.title} (đã xếp tới)",
                    detailVi = "${formatTimeSpan(u, zoneId)} · ${GoalDisplayMath.formatGoalMinutes(GoalDisplayMath.roundedMinutes(u.durationSeconds))}"
                )
            }

            val bandSuffix = if (allPlannedInEvening) " buổi tối" else ""
            val historySummaryLine = buildHistoryClusterNote(prevCompletedHistory)

            val mainFinding: String
            val findingDetail: String?
            val uncertainty: String?

            when {
                pastPlannedUnconfirmed.isNotEmpty() -> {
                    mainFinding = when {
                        coincidedCount >= 1 && predominantCoincideCat != null -> {
                            val durAdj = if (anyLongCoincide) " kéo dài" else ""
                            "Có $coincidedCount/${pastPlannedUnconfirmed.size} buổi dự kiến \"${summary.name}\"$bandSuffix trùng giờ ${predominantCoincideCat.displayName}$durAdj và đều chưa ghi nhận hoàn thành (hiện ${math.usedLabel}/${math.targetLabel}, còn ${math.remainingLabel})."
                        }
                        else ->
                            "Bạn đã dành chỗ ${pastPlannedUnconfirmed.size} buổi \"${summary.name}\"$bandSuffix trên lịch nhưng chưa ghi nhận hoàn thành (hiện ${math.usedLabel}/${math.targetLabel}, còn ${math.remainingLabel})."
                    }
                    findingDetail = when {
                        slot != null && historySummaryLine != null ->
                            "$historySummaryLine ${formatDateLabel(LocalDate.parse(slot.dateLocal), todayLocal)} đang trống ${TimeRange.formatHm(slot.startMinute)}–${TimeRange.formatHm(slot.endMinute)}."
                        slot != null -> slot.slotReasonVi
                        else -> historySummaryLine
                    }
                    uncertainty = null
                }

                upcomingPlanned.isNotEmpty() -> {
                    val upcomingDur = GoalDisplayMath.formatGoalMinutes(GoalDisplayMath.roundedMinutes(plannedUpcomingSeconds))
                    mainFinding = "Mục tiêu \"${summary.name}\" đang ở ${math.usedLabel}/${math.targetLabel} (còn ${math.remainingLabel}); bạn đã dành chỗ ${upcomingPlanned.size} buổi dự kiến ($upcomingDur) trong phần còn lại của kỳ."
                    findingDetail = historySummaryLine
                    uncertainty = null
                }

                timedDaysCount == 0 && allCompletedHistory.isEmpty() -> {
                    mainFinding = "Dữ liệu hiện có chưa đủ để đánh giá nhịp tập \"${summary.name}\" (hiện ${math.usedLabel}/${math.targetLabel}, còn ${math.remainingLabel}) và chưa có lịch dự kiến."
                    findingDetail = null
                    uncertainty = "Thiếu các phiên có mốc giờ hoặc lịch sử buổi trước đó nên Wally không chọn khung giờ cụ thể."
                }

                else -> {
                    mainFinding = "Mục tiêu \"${summary.name}\" đang ở ${math.usedLabel}/${math.targetLabel} (còn ${math.remainingLabel}) và bạn chưa dành chỗ nào trên lịch kỳ này."
                    findingDetail = when {
                        slot != null && historySummaryLine != null ->
                            "$historySummaryLine ${formatDateLabel(LocalDate.parse(slot.dateLocal), todayLocal)} đang trống ${TimeRange.formatHm(slot.startMinute)}–${TimeRange.formatHm(slot.endMinute)}."
                        slot != null -> slot.slotReasonVi
                        else -> historySummaryLine
                    }
                    uncertainty = if (slot == null) {
                        "Chưa tìm được khung giờ ban ngày phù hợp (06:00–22:00) có đủ căn cứ trống để gợi ý xếp lịch."
                    } else if (timedDaysCount < 2 && allCompletedHistory.isEmpty()) {
                        "Tuần này mới có $timedDaysCount ngày có mốc giờ và chưa có buổi \"${summary.name}\" trước đó."
                    } else {
                        null
                    }
                }
            }

            val actionLabel = slot?.buttonLabelVi
            val action = slot?.let { WallyStoryAction.ScheduleSlot(it) }
            val compPeriod = "Kỳ mục tiêu ${pStart.format(dmFmt)}–${pEnd.format(dmFmt)} (${math.usedLabel}/${math.targetLabel}, còn ${math.remainingLabel})" +
                if (prevCompletedHistory.isNotEmpty()) "; đối chiếu thêm kỳ trước ${lookbackStart.format(dmFmt)}–${pStart.minusDays(1).format(dmFmt)}." else "."
            val rationale = buildString {
                append("Mục tiêu \"${summary.name}\" đang ở ${math.usedLabel}/${math.targetLabel} (còn ${math.remainingLabel}). ")
                when {
                    pastPlannedUnconfirmed.isNotEmpty() ->
                        append("Có ${pastPlannedUnconfirmed.size} buổi dự kiến đã qua giờ nhưng chưa được xác nhận hoàn thành. ")
                    upcomingPlanned.isNotEmpty() ->
                        append("Đã có ${upcomingPlanned.size} buổi dự kiến phía trước nên không cần xếp trùng thêm. ")
                    else ->
                        append("Chưa có buổi dự kiến nào trong phần còn lại của kỳ. ")
                }
                if (slot != null) {
                    append(slot.slotReasonVi)
                } else if (!upcomingAlreadyCoversGap) {
                    append("Không chọn khung 22:00–06:00 và không mặc định ngày trống hoàn toàn là rảnh.")
                }
            }

            return WallyStorySummary(
                mainFinding = mainFinding,
                findingDetail = findingDetail,
                actionLabel = actionLabel,
                action = action,
                uncertaintyNote = uncertainty,
                comparisonPeriodVi = compPeriod,
                rationaleVi = rationale,
                evidenceItems = evidenceItems
            )
        }
        return null
    }

    private fun buildPendingConfirmationStory(
        records: List<LedgerRecord>,
        periodStart: LocalDate,
        endDate: LocalDate,
        todayStr: String,
        nowEpochMs: Long,
        zoneId: ZoneId
    ): WallyStorySummary? {
        val startMs = periodStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMs = endDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val pending = records.filter { r ->
            !r.isCancelled &&
                r.startedAt != null &&
                r.endedAt != null &&
                r.endedAt > startMs &&
                r.startedAt < endMs &&
                (r.isNeedsReconciliation || r.resolveConfirmationStateAt(nowEpochMs, todayStr) == ConfirmationState.PENDING_CONFIRMATION)
        }.sortedByDescending { it.startedAt ?: it.createdAt }

        val totalSecs = pending.sumOf { it.durationSeconds }
        if (pending.size < 2 && totalSecs < 1800L) return null

        val firstDate = pending.firstOrNull()?.date ?: todayStr
        val durText = LedgerAnalyticsCalculator.formatCompactDurationSeconds(totalSecs)
        val btnLabel = "Xem lịch chờ xác nhận"
        return WallyStorySummary(
            mainFinding = "Có ${pending.size} phiên ($durText) đã qua giờ dự kiến hoặc chồng lấn nhưng chưa xác nhận hoàn thành nên chưa tính vào số liệu thực tế.",
            findingDetail = "Xác nhận hoặc điều chỉnh lại giúp tổng giờ thực tế phản ánh đúng thực tế hơn.",
            actionLabel = btnLabel,
            action = WallyStoryAction.OpenConfirmPending(btnLabel, firstDate),
            uncertaintyNote = "Số liệu thực tế trong kỳ có thể tăng thêm tối đa $durText sau khi các phiên này được xác nhận.",
            comparisonPeriodVi = "Kỳ kiểm tra: ${periodStart.format(dmFmt)}–${endDate.format(dmFmt)}.",
            rationaleVi = "Wally tách riêng các mục chờ xác nhận hoặc cần đối soát để không tự ý cộng thời lượng dự kiến vào giờ thực tế.",
            evidenceItems = pending.take(6).map { r ->
                WallyEvidenceItem(
                    dateLocal = r.date,
                    label = r.title,
                    detailVi = "${formatTimeSpan(r, zoneId)} · dự kiến ${LedgerAnalyticsCalculator.formatCompactDurationSeconds(r.durationSeconds)} · chờ xác nhận"
                )
            }
        )
    }

    private fun buildSameElapsedComparisonStory(
        records: List<LedgerRecord>,
        activeTimer: ActiveTimerSession?,
        endDate: LocalDate,
        todayLocal: LocalDate,
        nowEpochMs: Long,
        zoneId: ZoneId
    ): WallyStorySummary? {
        val weekMon = endDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val prevMon = weekMon.minusWeeks(1)
        val prevEnd = endDate.minusWeeks(1)
        val elapsedDays = ChronoUnit.DAYS.between(weekMon, endDate).toInt() + 1
        val nowZoned = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId)
        val curCutoffMs = if (!endDate.isBefore(todayLocal)) {
            nowEpochMs
        } else {
            endDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        }
        val prevCutoffMs = if (!endDate.isBefore(todayLocal)) {
            prevEnd.atTime(nowZoned.toLocalTime()).atZone(zoneId).toInstant().toEpochMilli()
        } else {
            prevEnd.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        }

        val curDaysCount = countDaysWithTimedData(records, weekMon, endDate, curCutoffMs, zoneId)
        val prevDaysCount = countDaysWithTimedData(records, prevMon, prevEnd, prevCutoffMs, zoneId)
        if (curDaysCount < 2 || prevDaysCount < 2) return null

        val targetCategories = listOf(TimeCategory.WORK, TimeCategory.LEARN, TimeCategory.HEALTH)
        var bestCat: TimeCategory? = null
        var bestCurSec = 0L
        var bestPrevSec = 0L
        var bestDiffAbs = 0L
        var bestPct = 0

        val curTotalAll = sumRangeActualSeconds(records, activeTimer, weekMon, endDate, curCutoffMs, zoneId, null)
        val prevTotalAll = sumRangeActualSeconds(records, null, prevMon, prevEnd, prevCutoffMs, zoneId, null)
        if (curTotalAll < 2 * 3600L || prevTotalAll < 2 * 3600L) return null

        for (cat in targetCategories) {
            val curCat = sumRangeActualSeconds(records, activeTimer, weekMon, endDate, curCutoffMs, zoneId, cat)
            val prevCat = sumRangeActualSeconds(records, null, prevMon, prevEnd, prevCutoffMs, zoneId, cat)
            if (prevCat < 2 * 3600L) continue
            val diff = abs(curCat - prevCat)
            if (diff < 3600L) continue
            val pct = (((curCat - prevCat).toDouble() / prevCat.toDouble()) * 100.0).roundToInt()
            if (abs(pct) >= 20 && diff > bestDiffAbs) {
                bestDiffAbs = diff
                bestCat = cat
                bestCurSec = curCat
                bestPrevSec = prevCat
                bestPct = pct
            }
        }

        val winnerCat = bestCat ?: return null
        val curText = LedgerAnalyticsCalculator.formatCompactDurationSeconds(bestCurSec)
        val prevText = LedgerAnalyticsCalculator.formatCompactDurationSeconds(bestPrevSec)
        val diffText = LedgerAnalyticsCalculator.formatCompactDurationSeconds(bestDiffAbs)
        val dir = if (bestPct > 0) "tăng" else "giảm"
        val sign = if (bestPct > 0) "+" else ""
        val timeLabel = if (!endDate.isBefore(todayLocal)) "tới ${TimeRange.formatHm(nowZoned.hour * 60 + nowZoned.minute)}" else "hết ngày"

        val evidence = (0 until elapsedDays).mapNotNull { offset ->
            val dCur = weekMon.plusDays(offset.toLong())
            val dPrev = prevMon.plusDays(offset.toLong())
            val cCutoff = if (dCur == endDate) curCutoffMs else dCur.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val pCutoff = if (dPrev == prevEnd) prevCutoffMs else dPrev.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val cSec = sumRangeActualSeconds(records, activeTimer, dCur, dCur, cCutoff, zoneId, winnerCat)
            val pSec = sumRangeActualSeconds(records, null, dPrev, dPrev, pCutoff, zoneId, winnerCat)
            if (cSec == 0L && pSec == 0L) null else {
                WallyEvidenceItem(
                    dateLocal = dCur.toString(),
                    label = "${dCur.format(dmFmt)} vs ${dPrev.format(dmFmt)}",
                    detailVi = "Tuần này ${LedgerAnalyticsCalculator.formatCompactDurationSeconds(cSec)} · Tuần trước ${LedgerAnalyticsCalculator.formatCompactDurationSeconds(pSec)}"
                )
            }
        }

        val partialNote = if (!endDate.isBefore(todayLocal) && curDaysCount < elapsedDays) {
            "Tuần này mới có dữ liệu $curDaysCount/$elapsedDays ngày đã qua; so sánh chỉ tính các ngày đã ghi nhận."
        } else {
            null
        }

        return WallyStorySummary(
            mainFinding = "Trong $elapsedDays ngày đầu tuần ($timeLabel), ${winnerCat.displayName} đạt $curText — $dir $diffText ($sign$bestPct%) so với cùng kỳ tuần trước ($prevText).",
            findingDetail = "Đối chiếu cùng phần thời gian đã trôi qua giữa hai tuần để tránh lệch vì tuần hiện tại chưa kết thúc.",
            actionLabel = null,
            action = null,
            uncertaintyNote = partialNote,
            comparisonPeriodVi = "${weekMon.format(dmFmt)}–${endDate.format(dmFmt)} ($timeLabel) so với ${prevMon.format(dmFmt)}–${prevEnd.format(dmFmt)} ($timeLabel).",
            rationaleVi = "Chỉ mục \"${winnerCat.displayName}\" thay đổi từ $prevText sang $curText ($sign$bestPct%) khi đặt trên cùng phần thời gian đã trôi qua.",
            evidenceItems = evidence
        )
    }

    private fun buildFocusPlanPatternStory(records: List<LedgerRecord>): WallyStorySummary? {
        val acc = TaskHabitEngine.computePlanVsActualDelta(records)
        if (acc.sampleCount < 2 || acc.usedMinutesDisplayOnly || abs(acc.avgDeltaPercent) < 25) return null
        val pctEvidence = acc.sessionEvidence.filter { it.deltaPercent != null }
        if (pctEvidence.size < 2) return null

        val catName = acc.topDriftCategory?.displayName ?: "tập trung"
        val sign = if (acc.avgDeltaPercent > 0) "+" else ""
        val dir = if (acc.avgDeltaPercent > 0) "dài hơn" else "ngắn hơn"
        val items = pctEvidence.take(6).map { ev ->
            val evSign = if (ev.deltaMinutes > 0L) "+" else ""
            WallyEvidenceItem(
                dateLocal = ev.dateLocal,
                label = "${ev.title} (${ev.category.displayName})",
                detailVi = "Kế hoạch ${GoalDisplayMath.formatGoalMinutes(ev.plannedMinutes)} → Thực tế ${GoalDisplayMath.formatGoalMinutes(ev.actualMinutes)} ($evSign${ev.deltaMinutes}p)"
            )
        }
        return WallyStorySummary(
            mainFinding = "Qua ${pctEvidence.size} phiên có kế hoạch (≥ 15p), thực tế $dir kế hoạch $sign${acc.avgDeltaPercent}% — lệch rõ nhất ở $catName.",
            findingDetail = "Không gộp các phiên Ngủ/nghỉ hay kế hoạch quá ngắn vào độ lệch tập trung.",
            actionLabel = null,
            action = null,
            uncertaintyNote = acc.excludedNote,
            comparisonPeriodVi = "${pctEvidence.size} phiên tập trung có cả kế hoạch gốc (≥ 15p) và thực tế hoàn tất.",
            rationaleVi = "Phát hiện dựa trên chênh lệch trực tiếp giữa thời lượng dự kiến ban đầu và thời lượng thực tế đã xác nhận của từng phiên.",
            evidenceItems = items
        )
    }

    /**
     * Chọn tối đa 1 khung giờ trống tương lai trong ban ngày (06:00–22:00), ưu tiên giờ khớp lịch sử thực tế;
     * tuyệt đối không mặc định ngày trống hoàn toàn là rảnh và không gợi ý vào khoảng nghỉ 22:00–06:00.
     */
    private fun findBestFutureDaytimeSlot(
        goal: com.timewallet.mvp.domain.model.Goal,
        goalTitle: String,
        remainingMinutes: Long,
        historySlots: List<HistoricalSessionSlot>,
        records: List<LedgerRecord>,
        activeTimer: ActiveTimerSession?,
        pEnd: LocalDate,
        todayLocal: LocalDate,
        nowMinute: Int,
        zoneId: ZoneId,
        nowEpochMs: Long
    ): WallySuggestedSlot? {
        val suggestedDuration = if (historySlots.isNotEmpty()) {
            val sortedDurs = historySlots.map { it.durationMinutes }.sorted()
            val medianDur = sortedDurs[sortedDurs.size / 2]
            medianDur.coerceIn(15, remainingMinutes.toInt().coerceAtLeast(15).coerceAtMost(90))
        } else {
            remainingMinutes.toInt().coerceIn(20, 45)
        }

        val typicalWakeMin = estimateTypicalWakeMinute(records, todayLocal.minusDays(7), todayLocal, zoneId)
        val searchEndDate = minOf(pEnd, todayLocal.plusDays(6))
        if (searchEndDate.isBefore(todayLocal)) return null

        val candidateDates = generateSequence(todayLocal) { d ->
            val next = d.plusDays(1)
            if (next.isAfter(searchEndDate)) null else next
        }.toList()

        // Tier 1: Lịch sử có khung giờ quen thuộc -> ưu tiên đúng khung giờ đó trên ngày còn trống gần nhất (tránh hôm nay nếu giờ đã qua).
        if (historySlots.isNotEmpty()) {
            val groupedByBandStart = historySlots
                .groupBy { ((it.startMinute / 15) * 15) }
                .entries
                .sortedWith(
                    compareByDescending<Map.Entry<Int, List<HistoricalSessionSlot>>> { it.value.size }
                        .thenByDescending { entry -> entry.value.maxOf { it.date } }
                )

            for (entry in groupedByBandStart) {
                val targetStart = entry.value.map { it.startMinute }.sorted().let { it[it.size / 2] }
                val alignedDur = suggestedDuration
                if (targetStart < NIGHT_REST_END_MINUTE || targetStart + alignedDur > NIGHT_REST_START_MINUTE) continue

                for (d in candidateDates) {
                    val minAllowedStart = if (d == todayLocal) {
                        max(NIGHT_REST_END_MINUTE, roundUpMinutes(nowMinute + 5, 5))
                    } else {
                        NIGHT_REST_END_MINUTE
                    }
                    if (targetStart < minAllowedStart) continue

                    val dayModel = ScheduleGridEngine.buildDayScheduleModel(d, records, activeTimer, nowEpochMs, zoneId)
                    val targetEnd = targetStart + alignedDur
                    val fitsExact = dayModel.freeGaps.any { g ->
                        g.startMinute <= targetStart && g.endMinute >= targetEnd
                    }
                    if (fitsExact) {
                        val dayLabel = formatSlotDayLabel(d, todayLocal)
                        val startHm = TimeRange.formatHm(targetStart)
                        val endHm = TimeRange.formatHm(targetEnd)
                        val histCount = entry.value.size
                        val bandName = timeBandLabelVi(targetStart)
                        return WallySuggestedSlot(
                            dateLocal = d.toString(),
                            startMinute = targetStart,
                            endMinute = targetEnd,
                            durationMinutes = alignedDur,
                            categoryKey = resolveCategoryKey(goal, records),
                            tagId = if (goal.targetType == GoalTargetType.TAG) goal.targetKey else null,
                            title = goalTitle,
                            buttonLabelVi = "Xếp $dayLabel $startHm–$endHm",
                            slotReasonVi = "Khớp thói quen cũ: bạn từng hoàn thành $histCount $bandName (~${alignedDur}p quanh ${TimeRange.formatHm(targetStart)}); $dayLabel khung $startHm–$endHm đang trống và nằm trong ban ngày (06:00–22:00)."
                        )
                    }
                }
            }
        }

        // Tier 2: Không có lịch sử khung tập quen -> chỉ chọn khoảng trống ban ngày nằm giữa/sau lịch đã ghi (không giả định ngày trắng là rảnh).
        val minPreferredMorning = max(NIGHT_REST_END_MINUTE, typicalWakeMin ?: NIGHT_REST_END_MINUTE)
        for (d in candidateDates) {
            val dayModel = ScheduleGridEngine.buildDayScheduleModel(d, records, activeTimer, nowEpochMs, zoneId)
            val anchoredTodayOrRecurring = dayModel.timedBlocks.size >= 2 ||
                hasRecurringSameWeekdaySchedule(records, d, todayLocal, zoneId)
            if (!anchoredTodayOrRecurring) continue

            val minStartForDay = if (d == todayLocal) {
                max(minPreferredMorning, roundUpMinutes(nowMinute + 5, 15))
            } else {
                minPreferredMorning
            }
            val validDaytimeSubGaps = dayModel.freeGaps.mapNotNull { g ->
                val clippedStart = max(g.startMinute, minStartForDay)
                val snappedStart = roundUpMinutes(clippedStart, 15)
                val clippedEnd = min(g.endMinute, NIGHT_REST_START_MINUTE)
                if (clippedEnd - snappedStart >= suggestedDuration) {
                    val gapDur = clippedEnd - snappedStart
                    val isLunchOrPostWork = snappedStart in (11 * 60 + 30)..(13 * 60 + 30) || snappedStart in (17 * 60)..(20 * 60 + 30)
                    val score = (if (isLunchOrPostWork) 100 else 0) + min(gapDur, 120)
                    Triple(snappedStart, snappedStart + suggestedDuration, score)
                } else {
                    null
                }
            }.sortedWith(compareByDescending<Triple<Int, Int, Int>> { it.third }.thenBy { it.first })

            val chosen = validDaytimeSubGaps.firstOrNull()
            if (chosen != null) {
                val (s, e, _) = chosen
                val dayLabel = formatSlotDayLabel(d, todayLocal)
                val startHm = TimeRange.formatHm(s)
                val endHm = TimeRange.formatHm(e)
                return WallySuggestedSlot(
                    dateLocal = d.toString(),
                    startMinute = s,
                    endMinute = e,
                    durationMinutes = suggestedDuration,
                    categoryKey = resolveCategoryKey(goal, records),
                    tagId = if (goal.targetType == GoalTargetType.TAG) goal.targetKey else null,
                    title = goalTitle,
                    buttonLabelVi = "Xếp $dayLabel $startHm–$endHm",
                    slotReasonVi = "Chọn khung ban ngày $startHm–$endHm dựa trên lịch trình đã ghi trong ngày (tránh khoảng nghỉ 22:00–06:00 và không trùng lịch bận); do chưa có lịch sử buổi tập quen nên bạn kiểm tra lại trước khi xác nhận."
                )
            }
        }
        return null
    }

    private fun extractCompletedSlots(
        records: List<LedgerRecord>,
        from: LocalDate,
        to: LocalDate,
        nowEpochMs: Long,
        zoneId: ZoneId
    ): List<HistoricalSessionSlot> {
        if (to.isBefore(from)) return emptyList()
        return records.mapNotNull { r ->
            if (r.isCancelled || r.isNeedsReconciliation) return@mapNotNull null
            if (r.layer == TimeLayer.PLANNED || r.status.equals("PLANNED", ignoreCase = true)) return@mapNotNull null
            val s = r.startedAt ?: return@mapNotNull null
            val e = r.endedAt ?: return@mapNotNull null
            if (e <= s || s > nowEpochMs) return@mapNotNull null
            val startZ = Instant.ofEpochMilli(s).atZone(zoneId)
            val d = startZ.toLocalDate()
            if (d.isBefore(from) || d.isAfter(to)) return@mapNotNull null
            val startMin = startZ.hour * 60 + startZ.minute
            val endMin = ((e - d.atStartOfDay(zoneId).toInstant().toEpochMilli()) / 60_000L).toInt().coerceIn(startMin + 1, 1440)
            val durMins = ((min(e, nowEpochMs) - s + 30_000L) / 60_000L).toInt().coerceAtLeast(1)
            HistoricalSessionSlot(
                date = d,
                startMinute = startMin,
                endMinute = endMin,
                durationMinutes = durMins,
                title = r.title
            )
        }.sortedByDescending { it.date }
    }

    private fun estimateTypicalWakeMinute(
        records: List<LedgerRecord>,
        from: LocalDate,
        to: LocalDate,
        zoneId: ZoneId
    ): Int? {
        val fromMs = from.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val toMs = to.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val wakeMinutes = records.mapNotNull { r ->
            if (r.isCancelled || r.category != TimeCategory.SLEEP || r.layer == TimeLayer.PLANNED) return@mapNotNull null
            val e = r.endedAt ?: return@mapNotNull null
            if (e < fromMs || e > toMs) return@mapNotNull null
            val z = Instant.ofEpochMilli(e).atZone(zoneId)
            val minOfDay = z.hour * 60 + z.minute
            if (minOfDay in (5 * 60)..(10 * 60)) minOfDay else null
        }.sorted()
        return if (wakeMinutes.isEmpty()) null else wakeMinutes[wakeMinutes.size / 2]
    }

    private fun hasRecurringSameWeekdaySchedule(
        records: List<LedgerRecord>,
        targetDate: LocalDate,
        todayLocal: LocalDate,
        zoneId: ZoneId
    ): Boolean {
        val pastSameWeekday = targetDate.minusWeeks(1)
        if (!pastSameWeekday.isBefore(todayLocal)) return false
        val dayModel = ScheduleGridEngine.buildDayScheduleModel(pastSameWeekday, records, null, System.currentTimeMillis(), zoneId)
        return dayModel.timedBlocks.size >= 2
    }

    private fun countDaysWithTimedData(
        records: List<LedgerRecord>,
        from: LocalDate,
        to: LocalDate,
        cutoffMs: Long,
        zoneId: ZoneId
    ): Int {
        var count = 0
        var d = from
        while (!d.isAfter(to)) {
            val dStart = d.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val dEnd = min(cutoffMs, d.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli())
            val hasTimed = dEnd > dStart && records.any { r ->
                !r.isCancelled &&
                    !r.isNeedsReconciliation &&
                    r.layer != TimeLayer.PLANNED &&
                    !r.status.equals("PLANNED", ignoreCase = true) &&
                    r.startedAt != null &&
                    r.endedAt != null &&
                    r.startedAt < dEnd &&
                    r.endedAt > dStart
            }
            if (hasTimed) count++
            d = d.plusDays(1)
        }
        return count
    }

    private fun sumRangeActualSeconds(
        records: List<LedgerRecord>,
        activeTimer: ActiveTimerSession?,
        from: LocalDate,
        to: LocalDate,
        cutoffMs: Long,
        zoneId: ZoneId,
        categoryFilter: TimeCategory?
    ): Long {
        var total = 0L
        for (r in records) {
            if (categoryFilter != null && r.category != categoryFilter) continue
            total += LedgerAnalyticsCalculator.recordActualSecondsInDateRange(
                record = r,
                from = from,
                to = to,
                nowEpochMs = cutoffMs,
                zoneId = zoneId,
                confirmedFinalOnly = false
            )
        }
        if (activeTimer != null && activeTimer.startedAtEpochMs < cutoffMs) {
            val tCat = TimeCategory.fromKeyOrDefault(activeTimer.categoryKey)
            if (categoryFilter == null || categoryFilter == tCat) {
                val rangeStart = from.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val rangeEnd = min(cutoffMs, to.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli())
                val overlapStart = max(rangeStart, activeTimer.startedAtEpochMs)
                if (rangeEnd > overlapStart) {
                    total += (rangeEnd - overlapStart) / 1000L
                }
            }
        }
        return total
    }

    private fun resolveCategoryKey(goal: com.timewallet.mvp.domain.model.Goal, records: List<LedgerRecord>): String {
        if (goal.targetType == GoalTargetType.CATEGORY) return goal.targetKey
        val match = records.firstOrNull { !it.isCancelled && it.tagId == goal.targetKey }
        return match?.category?.key ?: TimeCategory.HEALTH.key
    }

    private fun resolveDefaultSlotTitle(
        goal: com.timewallet.mvp.domain.model.Goal,
        summaryName: String,
        tags: List<Tag>
    ): String = if (goal.targetType == GoalTargetType.TAG) {
        tags.firstOrNull { it.id == goal.targetKey }?.name ?: summaryName
    } else {
        summaryName
    }

    private fun buildHistoryClusterNote(history: List<HistoricalSessionSlot>): String? {
        if (history.isEmpty()) return null
        val sortedDurs = history.map { it.durationMinutes }.sorted()
        val typicalDur = sortedDurs[sortedDurs.size / 2]
        val avgStart = history.map { it.startMinute }.average().roundToInt()
        val band = timeBandLabelVi(avgStart)
        return if (history.size >= 2) {
            "Kỳ trước bạn hoàn thành ${history.size} $band (~${typicalDur}p quanh ${TimeRange.formatHm(avgStart)})."
        } else {
            "Kỳ trước bạn có 1 $band (~${typicalDur}p lúc ${TimeRange.formatHm(avgStart)})."
        }
    }

    private fun timeBandLabelVi(startMinute: Int): String = when {
        startMinute < 11 * 60 -> "buổi sáng"
        startMinute < 14 * 60 -> "buổi trưa"
        startMinute < 17 * 60 -> "buổi chiều"
        else -> "buổi tối"
    }

    private fun formatSlotDayLabel(date: LocalDate, todayLocal: LocalDate): String = when (date) {
        todayLocal -> "hôm nay"
        else -> weekdayNameVi(date.dayOfWeek)
    }

    private fun formatDateLabel(date: LocalDate, todayLocal: LocalDate): String = when (date) {
        todayLocal -> "Hôm nay"
        todayLocal.plusDays(1) -> "Ngày mai (${weekdayNameVi(date.dayOfWeek)})"
        else -> weekdayNameVi(date.dayOfWeek).replaceFirstChar { it.uppercaseChar() }
    }

    fun weekdayNameVi(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
        DayOfWeek.MONDAY -> "thứ Hai"
        DayOfWeek.TUESDAY -> "thứ Ba"
        DayOfWeek.WEDNESDAY -> "thứ Tư"
        DayOfWeek.THURSDAY -> "thứ Năm"
        DayOfWeek.FRIDAY -> "thứ Sáu"
        DayOfWeek.SATURDAY -> "thứ Bảy"
        DayOfWeek.SUNDAY -> "Chủ nhật"
    }

    private fun formatTimeSpan(record: LedgerRecord, zoneId: ZoneId): String {
        val s = record.startedAt ?: return record.date
        val e = record.endedAt ?: return record.date
        val sz = Instant.ofEpochMilli(s).atZone(zoneId)
        val ez = Instant.ofEpochMilli(e).atZone(zoneId)
        val startHm = TimeRange.formatHm(sz.hour * 60 + sz.minute)
        val endHm = TimeRange.formatHm(ez.hour * 60 + ez.minute)
        return "$startHm–$endHm"
    }

    private fun roundUpMinutes(minutes: Int, step: Int): Int {
        if (step <= 1) return minutes
        return ((minutes + step - 1) / step) * step
    }
}
