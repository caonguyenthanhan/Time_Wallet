package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.AiReadPermissions
import com.timewallet.mvp.domain.model.TimeCategory
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class ContextRouteMode {
    NONE,
    ADAPTIVE
}

enum class DayTimeWindow(
    val labelVi: String,
    val startMinute: Int,
    val endMinute: Int
) {
    MORNING("Buổi sáng", 0, 12 * 60),
    AFTERNOON("Buổi chiều", 12 * 60, 18 * 60),
    EVENING("Buổi tối", 18 * 60, 24 * 60),
    ANY("Trong ngày", 0, 24 * 60)
}

data class ContextRouteDecision(
    val mode: ContextRouteMode,
    val targetDate: LocalDate? = null,
    val dayWindow: DayTimeWindow = DayTimeWindow.ANY,
    val focusCategory: TimeCategory? = null,
    val includeGoals: Boolean = false,
    val includeScheduleGaps: Boolean = false,
    val includeTasks: Boolean = false,
    val includeHabits: Boolean = false,
    val includeCoachSummary: Boolean = false,
    val includeContinuity: Boolean = false,
    val reason: String
)

/**
 * Bộ định tuyến ngữ cảnh thích ứng hoàn toàn cục bộ (Phương án A — Adaptive Context).
 *
 * Kết hợp 4 tín hiệu xác định (không gọi thêm AI):
 * 1. Lệnh hủy / hỏi trạng thái giao dịch / câu ghi nhanh rõ ràng -> [ContextRouteMode.NONE].
 * 2. Truy vấn số liệu thuần túy không mang ý định tư vấn/lập kế hoạch -> [ContextRouteMode.NONE].
 * 3. Ý định tư vấn, kiểm tra mục tiêu còn thiếu, tìm lịch trống/khả thi -> [ContextRouteMode.ADAPTIVE].
 * 4. Câu hỏi nối tiếp ("Vậy tối mai thì sao?") khi [ConversationState] còn TTL & hợp lệ quyền -> [ContextRouteMode.ADAPTIVE].
 */
object AdaptiveContextRouter {

    private val vi = Locale("vi", "VN")

    private val advisoryIntentRegex = Rx.uni(
        """(?<![\p{L}\p{N}])(?:nên\s+(?:làm|học|đọc|tập|chạy|xếp|ưu\s*tiên|chọn|bắt\s+đầu)|làm\s+gì|học\s+gì|tập\s+gì|đọc\s+gì|gợi\s*ý|đề\s*xuất|tư\s*vấn|xếp\s*lịch|sắp\s*xếp|bố\s*trí|chèn\s+vào|rảnh(?:\s+lúc\s+nào|\s+không|\s+giờ\s+nào)?|trống(?:\s+lúc\s+nào|\s+giờ\s+nào|\s+không)?|khả\s*thi|hợp\s*lý|đủ\s+giờ|kịp\s+không|còn\s+thiếu(?:\s+bao\s+nhiêu)?|vào\s+lúc\s+nào|khung\s+giờ\s+nào|lúc\s+nào\s+(?:tốt|hợp|tiện|rảnh))(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )

    private val followUpPromptRegex = Rx.uni(
        """^(?:vậy|thế|thế\s+còn|còn|nhưng|nếu\s+vậy|nếu\s+thế|rồi)(?:\s|$)|thì\s+sao\b|sao\s+nữa\b|tiếp\s+theo\s+thì\b""",
        RegexOption.IGNORE_CASE
    )

    private val scheduleCueRegex = Rx.uni(
        """(?<![\p{L}\p{N}])(?:lịch|rảnh|trống|khung\s+giờ|slot|giờ\s+nào|lúc\s+nào|xếp|chèn|tối\s+nay|tối\s+mai|sáng\s+mai|chiều\s+mai|ngày\s+mai|hôm\s+nay|buổi\s+(?:sáng|chiều|tối)|cuối\s+tuần|khả\s*thi|kịp)(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )

    private val goalCueRegex = Rx.uni(
        """(?<![\p{L}\p{N}])(?:mục\s*tiêu|thiếu|đạt\s+chưa|tiến\s*độ|hạn\s*mức|ngân\s*sách|ưu\s*tiên|cân\s*bằng|coach|nhận\s*xét|tóm\s*tắt)(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )

    private val taskCueRegex = Rx.uni(
        """(?<![\p{L}\p{N}])(?:việc|task|inbox|công\s*việc|deadline|hạn|làm\s+gì|làm\s+nào|cần\s+làm)(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )

    private val habitCueRegex = Rx.uni(
        """(?<![\p{L}\p{N}])(?:thói\s*quen|habit|chuỗi|streak|duy\s*trì)(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )

    private val tomorrowRegex = Rx.uni("""(?<![\p{L}\p{N}])(?:ngày\s+)?mai(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
    private val dayAfterRegex = Rx.uni("""(?<![\p{L}\p{N}])(?:ngày\s+kia|ngày\s+mốt)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
    private val todayRegex = Rx.uni("""(?<![\p{L}\p{N}])(?:hôm\s+nay|tối\s+nay|chiều\s+nay|sáng\s+nay|trưa\s+nay|đêm\s+nay)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)

    fun decide(
        userText: String,
        conversationState: ConversationState?,
        perms: AiReadPermissions,
        today: LocalDate,
        nowTime: LocalTime,
        nowEpochMs: Long
    ): ContextRouteDecision {
        val raw = userText.trim()
        if (raw.isEmpty()) {
            return ContextRouteDecision(ContextRouteMode.NONE, reason = "empty")
        }
        if (!perms.readLedger && !perms.readGoals && !perms.readTasks && !perms.readHabits) {
            return ContextRouteDecision(ContextRouteMode.NONE, reason = "all_ai_permissions_disabled")
        }
        if (CancelCommandParser.parse(raw) != null || WallyTurnPolicy.isStatusQuestion(raw)) {
            return ContextRouteDecision(ContextRouteMode.NONE, reason = "local_control_or_status")
        }

        val normalized = normalize(raw)
        val restored = VietnameseChatParser.restoreUnaccented(normalized)

        val hasAdvisory = advisoryIntentRegex.containsMatchIn(restored) || advisoryIntentRegex.containsMatchIn(normalized)
        val validContinuity = conversationState?.takeIf { it.isValidFor(nowEpochMs, perms) }
        val looksFollowUp = followUpPromptRegex.containsMatchIn(restored) || followUpPromptRegex.containsMatchIn(normalized)
        val isPastPeriodOnly = Rx.uni("""(?<![\p{L}\p{N}])(?:hôm\s+qua|hôm\s+kia|tuần\s+trước|tháng\s+trước|\d{1,3}\s*ngày\s*(?:qua|trước))(?![\p{L}\p{N}])""").containsMatchIn(restored)
        val hasTopicContinuity = validContinuity != null && validContinuity.isAdvisoryTopic && !isPastPeriodOnly

        // Nếu không phải tư vấn và không phải nối tiếp một chủ đề tư vấn đang mở:
        // các câu ghi nhanh ("Đọc sách 30 phút") và hỏi số liệu đơn ("Hôm nay học bao nhiêu?") giữ payload tối giản.
        val parsedLog = VietnameseChatParser.parse(raw)
        val hasDurationStatement = parsedLog.proposals.isNotEmpty() || VietnameseChatParser.allDurationMinutes(restored).isNotEmpty()
        val isPlainMetricQuestion = isPureMetricQueryWithoutAdvice(restored, today)

        if (!hasAdvisory && !(looksFollowUp && hasTopicContinuity)) {
            val reason = when {
                isPlainMetricQuestion -> "pure_metric_query"
                hasDurationStatement -> "simple_duration_log"
                else -> "plain_chat"
            }
            return ContextRouteDecision(ContextRouteMode.NONE, reason = reason)
        }

        // Nếu có duration statement lẫn câu hỏi nhưng thực tế không có nhu cầu xếp lịch/tư vấn -> vẫn NONE.
        if (hasDurationStatement && !hasAdvisory && !(looksFollowUp && hasTopicContinuity)) {
            return ContextRouteDecision(ContextRouteMode.NONE, reason = "explicit_activity_entry")
        }

        val targetDate = resolveTargetDate(restored, today, validContinuity?.targetDate, nowTime)
        val window = resolveWindow(restored, validContinuity?.dayWindow)
        val category = detectCategory(restored) ?: if (looksFollowUp) validContinuity?.focusCategory else null

        val wantSchedule = scheduleCueRegex.containsMatchIn(restored) || hasAdvisory || looksFollowUp
        val wantGoals = goalCueRegex.containsMatchIn(restored) || hasAdvisory || category != null
        val wantTasks = taskCueRegex.containsMatchIn(restored) || category == TimeCategory.WORK || hasAdvisory
        val wantHabits = habitCueRegex.containsMatchIn(restored) || category == TimeCategory.HEALTH || (hasAdvisory && perms.readHabits)

        val includeGoals = perms.readGoals && wantGoals
        val includeScheduleGaps = perms.readLedger && wantSchedule
        val includeTasks = perms.readTasks && wantTasks
        val includeHabits = perms.readHabits && wantHabits
        val includeCoachSummary = validContinuity?.lastCoachSummary != null
        val includeContinuity = validContinuity != null && (looksFollowUp || validContinuity.hasAdvisoryTopic)

        // Nếu người dùng tắt đúng các quyền cần thiết cho câu hỏi này thì vẫn gắn adaptive marker nhẹ hoặc NONE theo quyền.
        return ContextRouteDecision(
            mode = ContextRouteMode.ADAPTIVE,
            targetDate = targetDate,
            dayWindow = window,
            focusCategory = category,
            includeGoals = includeGoals,
            includeScheduleGaps = includeScheduleGaps,
            includeTasks = includeTasks,
            includeHabits = includeHabits,
            includeCoachSummary = includeCoachSummary,
            includeContinuity = includeContinuity,
            reason = if (looksFollowUp && hasTopicContinuity) "continuity_followup" else "advisory_or_planning"
        )
    }

    private fun isPureMetricQueryWithoutAdvice(restored: String, today: LocalDate): Boolean {
        if (advisoryIntentRegex.containsMatchIn(restored)) return false
        return QueryIntentParser.parse(restored, today) is ChatRoute.Query
    }

    internal fun resolveTargetDate(
        restored: String,
        today: LocalDate,
        continuityDate: LocalDate?,
        nowTime: LocalTime
    ): LocalDate = when {
        dayAfterRegex.containsMatchIn(restored) -> today.plusDays(2)
        tomorrowRegex.containsMatchIn(restored) -> today.plusDays(1)
        todayRegex.containsMatchIn(restored) -> today
        restored.contains("cuối tuần") -> {
            val sat = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            if (sat.isBefore(today)) today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)) else sat
        }
        else -> continuityDate ?: today
    }

    internal fun resolveWindow(
        restored: String,
        continuityWindow: DayTimeWindow?
    ): DayTimeWindow = when {
        Rx.uni("""(?<![\p{L}\p{N}])(?:sáng|buổi\s+sáng)(?![\p{L}\p{N}])""").containsMatchIn(restored) -> DayTimeWindow.MORNING
        Rx.uni("""(?<![\p{L}\p{N}])(?:trưa|chiều|buổi\s+chiều)(?![\p{L}\p{N}])""").containsMatchIn(restored) -> DayTimeWindow.AFTERNOON
        Rx.uni("""(?<![\p{L}\p{N}])(?:tối|đêm|khuya|buổi\s+tối)(?![\p{L}\p{N}])""").containsMatchIn(restored) -> DayTimeWindow.EVENING
        else -> continuityWindow ?: DayTimeWindow.ANY
    }

    internal fun detectCategory(restored: String): TimeCategory? = when {
        Rx.uni("""(?<![\p{L}\p{N}])(?:học|ôn|đọc\s+sách|nghiên\s+cứu|ngoại\s+ngữ|lập\s+trình|code\s+ai)(?![\p{L}\p{N}])""").containsMatchIn(restored) -> TimeCategory.LEARN
        Rx.uni("""(?<![\p{L}\p{N}])(?:làm\s+việc|công\s*việc|deep\s+work|dự\s+án|báo\s+cáo|họp)(?![\p{L}\p{N}])""").containsMatchIn(restored) -> TimeCategory.WORK
        Rx.uni("""(?<![\p{L}\p{N}])(?:tập|chạy\s+bộ|gym|thể\s+dục|sức\s*khỏe|vận\s*động)(?![\p{L}\p{N}])""").containsMatchIn(restored) -> TimeCategory.HEALTH
        Rx.uni("""(?<![\p{L}\p{N}])(?:nghỉ|thư\s+giãn|ngủ)(?![\p{L}\p{N}])""").containsMatchIn(restored) -> TimeCategory.REST
        Rx.uni("""(?<![\p{L}\p{N}])(?:giải\s+trí|xem\s+phim|game|chơi)(?![\p{L}\p{N}])""").containsMatchIn(restored) -> TimeCategory.FUN
        else -> null
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.trim(), Normalizer.Form.NFC)
            .lowercase(vi)
            .replace(Rx.ascii("""\s+"""), " ")
}
