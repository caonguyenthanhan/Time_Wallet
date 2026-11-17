package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.GoalGroupState
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.GoalVersionStatus
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.OverlapResolution
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TimeCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * v1.1.0 (D) — AI Time Coach: ỨNG DỤNG tính số, AI kể chuyện.
 *
 * [CoachDigestBuilder] dựng khối dữ liệu xác định từ sổ cái + Tag + mục tiêu. Mọi con số mà AI được phép nhắc nằm trong
 * [CoachDigest.allowedMinutes] / [CoachDigest.allowedCounts] / [CoachDigest.allowedDates]; [CoachGuard] bác bỏ câu chuyện có số ngoài tập đó,
 * khẳng định đã ghi/lưu/đổi dữ liệu, phán xét, suy diễn nhân quả hoặc khẳng định "không làm" chỉ vì chưa có bản ghi.
 * [CoachDigest.facts] là phần số liệu do ứng dụng tự viết: luôn hiển thị, kể cả khi AI lỗi hoặc bị guard bác.
 */
data class CoachDigest(
    val text: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val totalDays: Int,
    val daysWithData: Int,
    val allowedMinutes: Set<Int>,
    val allowedCounts: Set<Int>,
    val allowedDates: Set<String>,
    /** Câu số liệu trung lập do ứng dụng viết (không qua AI). */
    val facts: List<String>,
    val hasAnyData: Boolean,
    val hasPlanOrStateHistory: Boolean = false
)

object CoachDigestBuilder {
    private val dm = DateTimeFormatter.ofPattern("dd/MM")
    const val MAX_TAG_LINES = 12
    const val MAX_GOAL_LINES = 8

    private fun mins(seconds: Long): Int = ((seconds + 30L) / 60L).toInt()

    /** @param days 1 = hôm nay; 7 = 7 ngày gần nhất (gồm hôm nay). */
    fun build(
        records: List<LedgerRecord>,
        tags: List<Tag>,
        goals: List<Goal>,
        days: Int,
        nowEpochMs: Long,
        zone: ZoneId
    ): CoachDigest {
        val n = days.coerceIn(1, 31)
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
        val from = today.minusDays((n - 1).toLong())
        val todayStr = today.toString()
        val tagById = tags.associateBy { it.id }

        // 1) Gom thời gian đã diễn ra theo (ngày, đối tượng), phân tách trạng thái xác nhận, kế hoạch, gián đoạn, nghỉ/song song.
        data class Key(val date: LocalDate, val label: String, val category: TimeCategory)
        val perKey = HashMap<Key, Long>()
        val dataDays = HashSet<LocalDate>()
        var untaggedSeconds = 0L
        var confirmedOnlyTotalSeconds = 0L
        var doingTotalSeconds = 0L
        var parallelDayOverlapSeconds = 0L

        val plannedItems = ArrayList<String>()
        val pendingConfirmItems = ArrayList<String>()
        val reconcileItems = ArrayList<String>()
        val cancelledItems = ArrayList<String>()
        val adjustedOrInterruptedItems = ArrayList<String>()
        val breakItems = ArrayList<String>()
        val extraAllowedMinutes = HashSet<Int>()
        val extraAllowedCounts = HashSet<Int>()

        val fromStartMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val todayEndMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        for (r in records) {
            val d = runCatching { LocalDate.parse(r.date) }.getOrNull() ?: continue
            val touchesRange = (!d.isBefore(from) && !d.isAfter(today)) ||
                (r.startedAt != null && r.endedAt != null && r.endedAt > fromStartMs && r.startedAt < todayEndMs)
            if (!touchesRange) continue
            val inOwnDay = !d.isBefore(from) && !d.isAfter(today)
            val tag = r.tagId?.let { tagById[it] }
            val objLabel = tag?.name ?: r.title
            val conf = r.resolveConfirmationStateAt(nowEpochMs, todayStr)
            val plannedMin = mins(r.plannedDurationSeconds ?: r.durationSeconds)

            if (r.isNeedsReconciliation) {
                if (!inOwnDay) continue
                val m = mins(r.durationSeconds); extraAllowedMinutes += m
                val kindVi = if (r.overlapResolution == OverlapResolution.BREAK_REJECTED) "cần làm rõ (không phải nghỉ)" else "chờ đối soát chồng lấn"
                reconcileItems += "- $objLabel (${r.category.displayName}, ${d.format(dm)}): $m phút [$kindVi — CHƯA TÍNH VÀO THỰC TẾ]"
                continue
            }

            when (conf) {
                ConfirmationState.CANCELLED -> {
                    if (!inOwnDay) continue
                    extraAllowedMinutes += plannedMin
                    cancelledItems += "- $objLabel (${r.category.displayName}, ${d.format(dm)}): dự kiến $plannedMin phút [ĐÃ HỦY / KHÔNG DIỄN RA]"
                    continue
                }
                ConfirmationState.PLANNED -> {
                    if (!inOwnDay) continue
                    extraAllowedMinutes += plannedMin
                    plannedItems += "- $objLabel (${r.category.displayName}, ${d.format(dm)}): $plannedMin phút [KẾ HOẠCH DỰ KIẾN — chưa thực hiện]"
                    continue
                }
                ConfirmationState.PENDING_CONFIRMATION -> {
                    val displayDay = if (inOwnDay) d else Instant.ofEpochMilli(r.endedAt ?: fromStartMs).atZone(zone).toLocalDate()
                    if (!displayDay.isBefore(from) && !displayDay.isAfter(today)) {
                        val elapsedM = mins(r.actualElapsedSecondsAt(nowEpochMs, todayStr))
                        extraAllowedMinutes += elapsedM; extraAllowedMinutes += plannedMin
                        pendingConfirmItems += "- $objLabel (${r.category.displayName}, ${displayDay.format(dm)}): dự kiến $plannedMin phút (đã qua phần khai báo $elapsedM phút) [CHỜ XÁC NHẬN — CHƯA TÍNH HOÀN THÀNH]"
                    }
                    continue
                }
                ConfirmationState.ADJUSTED -> if (inOwnDay) {
                    val actualM = mins(r.durationSeconds)
                    extraAllowedMinutes += actualM; extraAllowedMinutes += plannedMin
                    adjustedOrInterruptedItems += "- $objLabel (${d.format(dm)}): kế hoạch $plannedMin phút -> điều chỉnh thực tế $actualM phút [ĐÃ ĐIỀU CHỈNH]"
                }
                ConfirmationState.INTERRUPTED -> if (inOwnDay) {
                    val actualM = mins(r.durationSeconds)
                    extraAllowedMinutes += actualM; extraAllowedMinutes += plannedMin
                    adjustedOrInterruptedItems += "- $objLabel (${d.format(dm)}): kế hoạch $plannedMin phút -> gián đoạn/dừng sớm ở $actualM phút [GIÁN ĐOẠN]"
                }
                else -> Unit
            }

            if (r.overlapResolution == OverlapResolution.BREAK_CONFIRMED && r.category != TimeCategory.WORK && inOwnDay) {
                val bm = mins(r.durationSeconds); extraAllowedMinutes += bm
                breakItems += "- $objLabel (${r.category.displayName}, ${d.format(dm)}): $bm phút [KHOẢNG NGHỈ XEN GIỮA CÓ CHỦ ĐÍCH]"
            }

            val label = tag?.name ?: ("(chưa gắn Tag) " + r.category.displayName)
            var sliceDay = from
            while (!sliceDay.isAfter(today)) {
                val daySec = LedgerAnalyticsCalculator.recordActualSecondsInDateRange(
                    record = r,
                    from = sliceDay,
                    to = sliceDay,
                    nowEpochMs = nowEpochMs,
                    zoneId = zone,
                    confirmedFinalOnly = false
                )
                if (daySec > 0L) {
                    if (conf.isConfirmedFinalActual) confirmedOnlyTotalSeconds += daySec
                    else if (conf == ConfirmationState.DOING) doingTotalSeconds += daySec
                    dataDays += sliceDay
                    if (tag == null) untaggedSeconds += daySec
                    val k = Key(sliceDay, label, r.category)
                    perKey[k] = (perKey[k] ?: 0L) + daySec
                }
                sliceDay = sliceDay.plusDays(1)
            }
        }

        // Tính tổng chồng lấn song song theo từng ngày trong kỳ (không cộng trùng quỹ ngày).
        var curDay = from
        var dedupTimedSecondsPeriod = 0L
        while (!curDay.isAfter(today)) {
            val st = LedgerAnalyticsCalculator.computeDayStats(curDay.toString(), records, zone, nowEpochMs)
            dedupTimedSecondsPeriod += st.timedSeconds
            parallelDayOverlapSeconds += st.parallelOverlapSeconds
            curDay = curDay.plusDays(1)
        }

        val allowedMinutes = HashSet<Int>(extraAllowedMinutes)
        val allowedCounts = HashSet<Int>(extraAllowedCounts)
        val allowedDates = HashSet<String>()
        allowedCounts += n
        allowedCounts += dataDays.size
        allowedCounts += (n - dataDays.size).coerceAtLeast(0)
        allowedCounts += plannedItems.size
        allowedCounts += pendingConfirmItems.size
        allowedCounts += reconcileItems.size
        allowedCounts += cancelledItems.size
        if (confirmedOnlyTotalSeconds > 0L) allowedMinutes += mins(confirmedOnlyTotalSeconds)
        if (doingTotalSeconds > 0L) allowedMinutes += mins(doingTotalSeconds)
        if (parallelDayOverlapSeconds > 0L) {
            allowedMinutes += mins(parallelDayOverlapSeconds)
            allowedMinutes += mins(dedupTimedSecondsPeriod)
        }
        var d0 = from
        while (!d0.isAfter(today)) { allowedDates += d0.format(dm); d0 = d0.plusDays(1) }

        val facts = ArrayList<String>()
        val sb = StringBuilder()
        sb.append("[DỮ LIỆU ĐÃ ĐƯỢC ỨNG DỤNG TÍNH — chỉ được nhắc các con số có trong khối này]\n")
        sb.append("Kỳ: ${from.format(dm)} → ${today.format(dm)} ($n ngày); có dữ liệu ${dataDays.size}/$n ngày. Hôm nay là ${today.format(dm)}.\n")
        val coverage = when {
            dataDays.isEmpty() -> "Chưa có bản ghi nào trong kỳ này."
            dataDays.size < 3 -> "Dữ liệu còn mỏng (dưới 3 ngày có ghi nhận): nhận xét cần thận trọng."
            dataDays.size < n -> "Có ngày chưa ghi nhận: nhận xét chỉ dựa trên các ngày đã có dữ liệu."
            else -> "Mọi ngày trong kỳ đều có ghi nhận."
        }
        sb.append("Độ đầy đủ: $coverage\n")
        if (untaggedSeconds > 0) {
            val m = mins(untaggedSeconds); allowedMinutes += m
            sb.append("Thời gian chưa gắn Tag trong kỳ: $m phút (không phân biệt được hoạt động cụ thể).\n")
        }

        // 2) Mục tiêu đang áp dụng.
        val groups = goals.groupBy { it.groupId }
        val goalLines = ArrayList<String>()
        for ((_, versions) in groups) {
            if (GoalRules.groupState(versions) != GoalGroupState.ACTIVE) continue
            val open = GoalRules.latest(versions) ?: continue
            if (open.status != GoalVersionStatus.ACTIVE) continue
            val name = GoalRules.displayName(open) { id -> tagById[id]?.name }
            val kindVi = if (open.kind == GoalKind.MIN) "tối thiểu" else "tối đa"
            val perVi = if (open.period == GoalPeriod.DAY) "ngày" else "tuần"
            val tgtMin = mins(open.targetSeconds); allowedMinutes += tgtMin
            val line = StringBuilder("- $name: $kindVi $tgtMin phút/$perVi.")
            if (open.period == GoalPeriod.DAY) {
                val series = GoalRules.dailySeries(versions, records, from, today, nowEpochMs, zone)
                if (series.isNotEmpty()) {
                    val met = series.count { it.met }
                    allowedCounts += met; allowedCounts += series.size
                    line.append(" Theo ngày (phút): ")
                    line.append(series.joinToString("; ") { p ->
                        val m = mins(p.usedSeconds); allowedMinutes += m; allowedDates += p.date.format(dm)
                        "${p.date.format(dm)}=$m"
                    })
                    line.append(". ")
                    line.append(if (open.kind == GoalKind.MIN) "Đạt $met/${series.size} ngày." else "Trong giới hạn $met/${series.size} ngày.")
                }
                val used = GoalRules.progress(open, records, today, nowEpochMs, zone)
                val confSec = GoalRules.confirmedUsedSeconds(open.targetType, open.targetKey, records, today, today, nowEpochMs, zone)
                val u = mins(used.usedSeconds); val rem = mins(used.remainingSeconds); val over = mins(used.overSeconds); val cu = mins(confSec)
                val minMetConfirmed = confSec >= open.targetSeconds
                allowedMinutes += u; allowedMinutes += rem; allowedMinutes += over; allowedMinutes += cu
                line.append(" Hôm nay: $u phút")
                if (cu != u) line.append(" (đã chốt xác nhận: $cu phút)")
                if (open.kind == GoalKind.MIN) line.append(if (minMetConfirmed) "; đã đạt." else "; còn thiếu $rem phút.")
                else line.append(if (used.met) "; còn trong giới hạn (còn $rem phút)." else "; vượt giới hạn $over phút.")
                facts += if (open.kind == GoalKind.MIN) {
                    if (minMetConfirmed) "$name hôm nay: ${TimeRange.formatDuration(u)} — đã đạt mục tiêu tối thiểu ${TimeRange.formatDuration(tgtMin)}."
                    else if (u == 0) "$name hôm nay: chưa ghi nhận — mục tiêu tối thiểu ${TimeRange.formatDuration(tgtMin)}."
                    else "$name hôm nay: ${TimeRange.formatDuration(u)} / ${TimeRange.formatDuration(tgtMin)} (đã xác nhận ${TimeRange.formatDuration(cu)}) — chưa chốt đạt mục tiêu."
                } else {
                    if (used.met) "$name hôm nay: ${TimeRange.formatDuration(u)} / giới hạn ${TimeRange.formatDuration(tgtMin)} — còn trong giới hạn."
                    else "$name hôm nay: ${TimeRange.formatDuration(u)} / giới hạn ${TimeRange.formatDuration(tgtMin)} — vượt ${TimeRange.formatDuration(over)}."
                }
            } else {
                val used = GoalRules.progress(open, records, today, nowEpochMs, zone)
                val (pStart, pEnd) = GoalRules.periodBounds(GoalPeriod.WEEK, today)
                val confSec = GoalRules.confirmedUsedSeconds(open.targetType, open.targetKey, records, pStart, pEnd, nowEpochMs, zone)
                val u = mins(used.usedSeconds); val rem = mins(used.remainingSeconds); val over = mins(used.overSeconds); val cu = mins(confSec)
                val minMetConfirmed = confSec >= open.targetSeconds
                allowedMinutes += u; allowedMinutes += rem; allowedMinutes += over; allowedMinutes += cu
                allowedDates += LocalDate.parse(used.periodStart).format(dm); allowedDates += LocalDate.parse(used.periodEnd).format(dm)
                line.append(" Tuần này (${LocalDate.parse(used.periodStart).format(dm)}–${LocalDate.parse(used.periodEnd).format(dm)}) đến hiện tại: $u phút")
                if (cu != u) line.append(" (đã chốt xác nhận: $cu phút)")
                line.append(if (open.kind == GoalKind.MIN) (if (minMetConfirmed) "; đã đạt." else "; còn thiếu $rem phút.") else (if (used.met) "; còn trong giới hạn (còn $rem phút)." else "; vượt giới hạn $over phút."))
                facts += if (open.kind == GoalKind.MIN) "$name tuần này: ${TimeRange.formatDuration(u)} / ${TimeRange.formatDuration(tgtMin)}" + (if (minMetConfirmed) " — đã đạt." else " — chưa đạt mục tiêu xác nhận.")
                else "$name tuần này: ${TimeRange.formatDuration(u)} / giới hạn ${TimeRange.formatDuration(tgtMin)}" + (if (used.met) " — còn trong giới hạn." else " — vượt ${TimeRange.formatDuration(over)}.")
            }
            goalLines += line.toString()
        }
        if (goalLines.isEmpty()) sb.append("Mục tiêu: người dùng chưa đặt mục tiêu nào.\n")
        else {
            sb.append("Mục tiêu đang áp dụng:\n")
            goalLines.take(MAX_GOAL_LINES).forEach { sb.append(it).append('\n') }
        }

        // 3) Hoạt động theo Tag/nhãn trong kỳ và hôm nay.
        val totals = perKey.entries.groupBy { it.key.label }.mapValues { (_, es) -> es.sumOf { it.value } to es.first().key.category }
        if (totals.isEmpty()) sb.append("Hoạt động trong kỳ: chưa có.\n")
        else {
            sb.append("Hoạt động trong kỳ (phút, theo Tag; danh mục trong ngoặc):\n")
            totals.entries.sortedByDescending { it.value.first }.take(MAX_TAG_LINES).forEach { (label, v) ->
                val m = mins(v.first); allowedMinutes += m
                sb.append("- $label (${v.second.displayName}): $m\n")
            }
        }
        if (n > 1 && totals.isNotEmpty()) {
            // Theo ngày cho từng Tag: để so sánh nhiều ngày (xu hướng tăng/giảm) mà AI không phải tự tính.
            sb.append("Theo ngày (phút; chỉ các ngày có ghi nhận):\n")
            totals.entries.sortedByDescending { it.value.first }.take(MAX_TAG_LINES).forEach { (label, _) ->
                val perDay = perKey.entries.filter { it.key.label == label }.sortedBy { it.key.date }
                sb.append("- $label: ").append(perDay.joinToString("; ") { e ->
                    val m = mins(e.value); allowedMinutes += m; "${e.key.date.format(dm)}=$m"
                }).append('\n')
            }
        }
        val todayTotals = perKey.entries.filter { it.key.date == today }.groupBy { it.key.label }.mapValues { (_, es) -> es.sumOf { it.value } to es.first().key.category }
        if (n > 1 && todayTotals.isNotEmpty()) {
            sb.append("Hôm nay (phút): ")
            sb.append(todayTotals.entries.sortedByDescending { it.value.first }.take(MAX_TAG_LINES).joinToString("; ") { (label, v) ->
                val m = mins(v.first); allowedMinutes += m; "$label=$m"
            }).append('\n')
        }
        if (parallelDayOverlapSeconds > 0L) {
            val dedupMin = mins(dedupTimedSecondsPeriod)
            val overlapMin = mins(parallelDayOverlapSeconds)
            allowedMinutes += dedupMin; allowedMinutes += overlapMin
            sb.append("Hoạt động song song: tổng quỹ thực tế đã khử trùng là $dedupMin phút (phần chạy song song $overlapMin phút không cộng trùng vào quỹ ngày).\n")
            facts += "Thời gian song song trong kỳ: ${TimeRange.formatDuration(overlapMin)} (tổng quỹ ngày thực tế không cộng trùng)."
        }
        if (adjustedOrInterruptedItems.isNotEmpty()) {
            sb.append("Điều chỉnh / Gián đoạn so với kế hoạch:\n")
            adjustedOrInterruptedItems.take(8).forEach { sb.append(it).append('\n') }
        }
        if (breakItems.isNotEmpty()) {
            sb.append("Khoảng nghỉ xen giữa đã xác nhận:\n")
            breakItems.take(8).forEach { sb.append(it).append('\n') }
        }
        if (pendingConfirmItems.isNotEmpty()) {
            sb.append("Chờ xác nhận (CHƯA TÍNH HOÀN THÀNH — tuyệt đối không xem là đã xảy ra xong):\n")
            pendingConfirmItems.take(8).forEach { sb.append(it).append('\n') }
            facts += "Có ${pendingConfirmItems.size} hoạt động đang chờ xác nhận kết thúc (chưa tính hoàn thành)."
        }
        if (reconcileItems.isNotEmpty()) {
            sb.append("Cần đối soát chồng lấn (CHƯA XÁC NHẬN — không tính vào kết quả thực tế):\n")
            reconcileItems.take(8).forEach { sb.append(it).append('\n') }
            facts += "Có ${reconcileItems.size} mục chồng lấn cần đối soát (chưa tính vào thực tế)."
        }
        if (plannedItems.isNotEmpty()) {
            sb.append("Kế hoạch dự kiến (chưa bắt đầu):\n")
            plannedItems.take(8).forEach { sb.append(it).append('\n') }
        }
        if (cancelledItems.isNotEmpty()) {
            sb.append("Kế hoạch đã hủy (không diễn ra):\n")
            cancelledItems.take(8).forEach { sb.append(it).append('\n') }
        }

        if (todayTotals.isNotEmpty()) {
            facts += "Hôm nay đã ghi nhận: " + todayTotals.entries.sortedByDescending { it.value.first }.take(6).joinToString(", ") { (label, v) -> "$label ${TimeRange.formatDuration(mins(v.first))}" } + "."
        }
        if (n > 1) {
            facts += "Có dữ liệu ${dataDays.size}/$n ngày trong kỳ ${from.format(dm)}–${today.format(dm)}."
        }
        val hasPlanHistory = plannedItems.isNotEmpty() || pendingConfirmItems.isNotEmpty() || reconcileItems.isNotEmpty() || cancelledItems.isNotEmpty()
        if (dataDays.isEmpty()) facts += "Chưa có bản ghi thực tế nào trong kỳ để phân tích."
        return CoachDigest(
            text = sb.toString().trimEnd(),
            fromDate = from, toDate = today, totalDays = n, daysWithData = dataDays.size,
            allowedMinutes = allowedMinutes, allowedCounts = allowedCounts, allowedDates = allowedDates,
            facts = facts, hasAnyData = dataDays.isNotEmpty(), hasPlanOrStateHistory = hasPlanHistory
        )
    }
}

/** Kết quả kiểm tra lời kể của AI. */
sealed class CoachCheck {
    object Ok : CoachCheck()
    data class Rejected(val reason: String) : CoachCheck()
}

enum class CoachActionKind(val storageKey: String, val ctaLabelVi: String) {
    PLAN("PLAN", "Lên lịch"),
    TIMER("TIMER", "Bấm giờ"),
    TAG("TAG", "Gắn Tag"),
    REFLECT("REFLECT", "Nhìn lại")
}

/** Việc gợi ý tiếp theo để người dùng tự quyết (chỉ mở form/màn hình phù hợp, KHÔNG tự ghi DB). */
data class CoachActionItem(
    val title: String,
    val kind: CoachActionKind = CoachActionKind.PLAN,
    val targetCategoryKey: String? = null,
    val targetTagId: String? = null,
    val targetLabel: String? = null,
    val durationMinutes: Int? = null
)

/** Đầu ra Wally AI Coach dạng có cấu trúc, ngắn gọn và dễ quét mắt. */
data class CoachBrief(
    val headline: String,
    val highlights: List<String> = emptyList(),
    val watchout: String? = null,
    val actionItems: List<CoachActionItem> = emptyList()
)

sealed class CoachStructuredCheck {
    data class Ok(val story: String, val brief: CoachBrief) : CoachStructuredCheck()
    data class Rejected(val reason: String) : CoachStructuredCheck()
}

/** Đề xuất mục tiêu do AI gợi ý — chỉ là THẺ ĐỀ XUẤT chờ người dùng xác nhận, không bao giờ tự tạo. */
data class CoachSuggestion(
    val targetType: GoalTargetType,
    val targetKey: String,
    val targetLabel: String,
    val kind: GoalKind,
    val period: GoalPeriod,
    val targetSeconds: Long
)

object CoachGuard {
    const val MAX_STORY_CHARS = 900

    private val judgement = Rx.ascii("""(?<![a-z])(lang phi|vo ich|luoi|that bai|te hai|dang xau ho|khong nen choi|vo bo|sa doa|nghien)(?![a-z])""")
    private val causal = Rx.ascii("""(?<![a-z])(nguyen nhan|gay ra|dan den|chinh vi|la do |boi vi ban|ket qua cua viec)""")
    // Đoán lý do/cảm xúc/ý định của người dùng (kể cả khi có "có vẻ"): dữ liệu thời gian không nói điều đó.
    private val motive = Rx.ascii("""(?<![a-z])(thu gian|giai tri cua ban|xa stress|giai toa|ban (dang )?(cam thay|met|ap luc|chan|buon|vui)|de nghi ngoi|sau nhung khoi|sau khi (tap trung|lam viec|hoc))""")
    private val negativeClaim = Rx.ascii("""(?<![a-z])(ban (da )?khong (he )?(chay|tap|doc|hoc|lam|danh|thuc hien|dung)|khong he (chay|tap|doc|hoc|lam|danh))""")
    private val falseGoalMetWithoutActual = Rx.ascii("""(?<![a-z])(da dat muc tieu|hoan thanh muc tieu|da hoan thanh du)""")

    // Coach KHÔNG dùng ReplyGuard.claimsStateChange: "đã ghi nhận 175 phút" là MÔ TẢ dữ liệu người dùng (hợp lệ); chỉ chặn việc chính Wally nói mình đã/sẽ làm thay đổi.
    // Dùng chữ có dấu (không bỏ dấu): "đặt" (đặt mục tiêu) khác "đạt" (đạt mục tiêu).
    private val ownChange = listOf(
        Rx.uni("""(?<![\p{L}])(mình|wally|tôi)\s+(đã|vừa|sẽ)\s+(ghi|lưu|tạo|đặt|cập nhật|xóa|sửa|thêm|thay đổi|điều chỉnh|hủy)(?![\p{L}])"""),
        Rx.uni("""(?<![\p{L}])(đã|vừa)\s+(tạo|đặt|lưu|cập nhật|xóa|sửa|thêm|thay đổi|điều chỉnh|hủy)\s+(cho bạn|giúp bạn|mục tiêu|bản ghi|dữ liệu|kế hoạch|nhắc nhở|vào sổ)"""),
        Rx.uni("""(?<![\p{L}])(đã ghi|đã lưu)\s+(vào|thêm|giúp|cho bạn)"""),
        Rx.uni("""không cần xác nhận|tự động (tạo|đặt|lưu|thêm|cập nhật|xóa|sửa)""")
    )

    /** True nếu lời kể nói chính Wally đã/sẽ ghi, lưu, tạo, đặt, sửa, xóa dữ liệu hoặc mục tiêu. */
    fun claimsOwnChange(text: String): Boolean {
        val t = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC).lowercase()
        return ownChange.any { it.containsMatchIn(t) }
    }

    // "10–20 phút" ⇒ cả hai đầu đều là thời lượng: tách thành "10 phút 20 phút" để mỗi số được đối chiếu với dữ liệu.
    private val durationRange = Rx.uni("""(\d+(?:[.,]\d+)?)\s*[-–]\s*(\d+(?:[.,]\d+)?)\s*(giờ|tiếng|phút|h(?![\p{L}])|p(?![\p{L}]))""", RegexOption.IGNORE_CASE)

    // "h" chỉ là đơn vị khi không dính chữ sau nó ("11 hiện" KHÔNG phải 11 giờ); ngày dạng dd/MM bị tách ra trước khi tìm số đếm.
    private val duration = Rx.uni("""(\d+(?:[.,]\d+)?)\s*(?:giờ|tiếng|h(?![\p{L}]))(?:\s*(\d{1,2})\s*(?:phút|p(?![\p{L}]))?)?|(\d+)\s*(?:phút|p(?![\p{L}]))""", RegexOption.IGNORE_CASE)
    private val dateToken = Rx.uni("""\d{1,2}/\d{1,2}""")
    private val anyNumber = Rx.uni("""\d+""")

    private fun fold(s: String): String = TagRules.nameKey(s)

    /** Số phút của một nhóm khớp [duration] (giờ[ phút] hoặc phút). */
    private fun minutesOf(m: MatchResult): Int {
        val g = m.groupValues
        if (g[3].isNotEmpty()) return g[3].toInt()
        val h = g[1].replace(',', '.').toDoubleOrNull() ?: return -1
        val extra = g[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
        return Math.round(h * 60).toInt() + extra
    }

    fun check(story: String?, digest: CoachDigest): CoachCheck {
        val text = story?.trim().orEmpty()
        if (text.isEmpty()) return CoachCheck.Rejected("Lời kể trống.")
        if (text.length > MAX_STORY_CHARS) return CoachCheck.Rejected("Lời kể quá dài.")
        if (claimsOwnChange(text)) return CoachCheck.Rejected("Khẳng định đã ghi/lưu/đổi dữ liệu.")
        val f = fold(text)
        if (judgement.containsMatchIn(f)) return CoachCheck.Rejected("Có lời phán xét.")
        if (causal.containsMatchIn(f)) return CoachCheck.Rejected("Khẳng định quan hệ nhân quả.")
        if (motive.containsMatchIn(f)) return CoachCheck.Rejected("Đoán lý do/cảm xúc của người dùng.")
        if (negativeClaim.containsMatchIn(f)) return CoachCheck.Rejected("Khẳng định người dùng không làm vì chưa có bản ghi.")
        if (!digest.hasAnyData && falseGoalMetWithoutActual.containsMatchIn(f)) {
            return CoachCheck.Rejected("Không thể kết luận đã đạt mục tiêu khi chưa có thời gian thực tế được xác nhận.")
        }
        // Số: mọi thời lượng phải thuộc tập phút của dữ liệu; ngày/đếm phải thuộc tập tương ứng; không có số lạ.
        var rest = durationRange.replace(text) { m -> "${m.groupValues[1]} ${m.groupValues[3]} ${m.groupValues[2]} ${m.groupValues[3]}" }
        val expanded = rest
        for (m in duration.findAll(expanded).toList().reversed()) {
            val v = minutesOf(m)
            if (v < 0 || v !in digest.allowedMinutes) return CoachCheck.Rejected("Số liệu thời lượng ngoài dữ liệu.")
            rest = rest.replaceRange(m.range, " ")
        }
        for (m in dateToken.findAll(rest).toList().reversed()) {
            val parts = m.value.split('/')
            val norm = "%02d/%02d".format(parts[0].toInt(), parts[1].toInt())
            if (norm !in digest.allowedDates && !(parts[0].toInt() in digest.allowedCounts && parts[1].toInt() in digest.allowedCounts)) {
                return CoachCheck.Rejected("Ngày/phân số ngoài dữ liệu.")
            }
            rest = rest.replaceRange(m.range, " ")
        }
        for (m in anyNumber.findAll(rest)) {
            val v = m.value.toIntOrNull() ?: return CoachCheck.Rejected("Số không đọc được.")
            // Số trần chỉ hợp lệ khi chính là một số đếm hoặc một số phút CÓ trong dữ liệu ("đi từ 10 lên 20, 30 rồi 40 phút").
            if (v !in digest.allowedCounts && v !in digest.allowedMinutes) return CoachCheck.Rejected("Số liệu ngoài dữ liệu.")
        }
        return CoachCheck.Ok
    }

    /** Lọc đề xuất mục tiêu của AI: đúng Tag/Category có thật, mức hợp lệ, không trùng mục tiêu đang chạy; tối đa 2. */
    fun validSuggestions(
        raw: List<Map<String, Any?>>,
        tags: List<Tag>,
        goals: List<Goal>
    ): List<CoachSuggestion> {
        val activeGoals = goals.groupBy { it.groupId }.values
            .filter { GoalRules.groupState(it) == GoalGroupState.ACTIVE }.mapNotNull { GoalRules.latest(it) }
        val out = ArrayList<CoachSuggestion>()
        for (s in raw) {
            if (out.size >= 2) break
            val type = (s["targetType"] as? String)?.trim()?.lowercase()
            val target = (s["target"] as? String)?.trim().orEmpty()
            val kind = GoalKind.entries.firstOrNull { it.storageKey == (s["kind"] as? String)?.trim()?.uppercase() } ?: continue
            val period = GoalPeriod.entries.firstOrNull { it.storageKey == (s["period"] as? String)?.trim()?.uppercase() } ?: continue
            val minutes = (s["minutes"] as? Number)?.toInt() ?: continue
            val seconds = minutes * 60L
            if (GoalRules.checkTarget(period, seconds) is GoalRules.Check.Invalid) continue
            val (tType, key, label) = when (type) {
                "tag" -> {
                    val t = tags.firstOrNull { !it.isArchived && it.nameKey == TagRules.nameKey(target) } ?: continue
                    Triple(GoalTargetType.TAG, t.id, t.name)
                }
                "category" -> {
                    val c = TimeCategory.fromKeyOrNull(target.lowercase())
                        ?: TimeCategory.entries.firstOrNull { TagRules.nameKey(it.displayName) == TagRules.nameKey(target) }
                        ?: continue
                    Triple(GoalTargetType.CATEGORY, c.key, c.displayName)
                }
                else -> continue
            }
            if (activeGoals.any { it.targetType == tType && it.targetKey == key && it.kind == kind && it.period == period }) continue
            out += CoachSuggestion(tType, key, label, kind, period, seconds)
        }
        return out
    }

    /** Kiểm chứng các action item do AI gợi ý: tiêu đề qua [check], đối chiếu Tag/Category có thật, phút thuộc [CoachDigest.allowedMinutes]. */
    fun validActionItems(
        raw: List<Map<String, Any?>>,
        digest: CoachDigest,
        tags: List<Tag>
    ): List<CoachActionItem> {
        val out = ArrayList<CoachActionItem>()
        for (item in raw) {
            if (out.size >= 2) break
            val title = (item["title"] as? String)?.trim().orEmpty()
            if (title.isEmpty() || title.length > 120) continue
            if (check(title, digest) !is CoachCheck.Ok) continue
            val rawKind = (item["kind"] as? String)?.trim()?.uppercase()
            val kind = CoachActionKind.entries.firstOrNull { it.storageKey == rawKind } ?: CoachActionKind.PLAN
            val target = (item["target"] as? String)?.trim().orEmpty()
            var catKey: String? = null
            var tagId: String? = null
            var label: String? = null
            if (target.isNotEmpty()) {
                val matchedTag = tags.firstOrNull { !it.isArchived && it.nameKey == TagRules.nameKey(target) }
                if (matchedTag != null) {
                    tagId = matchedTag.id
                    catKey = matchedTag.category.key
                    label = matchedTag.name
                } else {
                    val matchedCat = TimeCategory.fromKeyOrNull(target.lowercase())
                        ?: TimeCategory.entries.firstOrNull { TagRules.nameKey(it.displayName) == TagRules.nameKey(target) }
                    if (matchedCat != null) {
                        catKey = matchedCat.key
                        label = matchedCat.displayName
                    }
                }
            }
            val rawMins = (item["minutes"] as? Number)?.toInt()
            val verifiedMins = rawMins?.takeIf { it in 5..480 && it in digest.allowedMinutes }
            out += CoachActionItem(
                title = title,
                kind = kind,
                targetCategoryKey = catKey,
                targetTagId = tagId,
                targetLabel = label,
                durationMinutes = verifiedMins
            )
        }
        return out
    }

    /** Tách câu đơn giản an toàn trên ICU/JVM mà không cắt nhầm số thập phân. */
    private fun splitSentencesPlain(text: String): List<String> {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return emptyList()
        val sentences = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < cleaned.length) {
            val ch = cleaned[i]
            cur.append(ch)
            val isTerminator = ch == '.' || ch == '!' || ch == '?' || ch == '\n'
            val nextIsBoundary = i == cleaned.lastIndex || cleaned[i + 1].isWhitespace()
            val prevIsDigit = ch == '.' && i > 0 && cleaned[i - 1].isDigit() && i < cleaned.lastIndex && cleaned[i + 1].isDigit()
            if (isTerminator && nextIsBoundary && !prevIsDigit) {
                val s = cur.toString().trim()
                if (s.isNotEmpty()) sentences += s
                cur.setLength(0)
            }
            i++
        }
        val tail = cur.toString().trim()
        if (tail.isNotEmpty()) sentences += tail
        return sentences
    }

    /** Kiểm chứng toàn bộ đầu ra có cấu trúc của Coach (kể cả fallback từ [rawStory]). Mọi phần đều phải qua [check]. */
    fun checkStructured(
        rawStory: String?,
        rawHeadline: String? = null,
        rawHighlights: List<String> = emptyList(),
        rawWatchout: String? = null,
        rawActions: List<Map<String, Any?>> = emptyList(),
        digest: CoachDigest,
        tags: List<Tag>
    ): CoachStructuredCheck {
        val headlineClean = rawHeadline?.trim()?.takeIf { it.isNotEmpty() }
        val highlightsClean = rawHighlights.mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }.take(3)
        val watchoutClean = rawWatchout?.trim()?.takeIf { it.isNotEmpty() }
        val storyClean = rawStory?.trim()?.takeIf { it.isNotEmpty() }

        val canonicalStory = storyClean ?: buildList {
            if (headlineClean != null) add(headlineClean)
            addAll(highlightsClean)
            if (watchoutClean != null) add(watchoutClean)
        }.joinToString(" ").trim()

        if (canonicalStory.isEmpty()) return CoachStructuredCheck.Rejected("Lời kể trống.")
        when (val c = check(canonicalStory, digest)) {
            is CoachCheck.Rejected -> return CoachStructuredCheck.Rejected(c.reason)
            CoachCheck.Ok -> Unit
        }
        if (headlineClean != null) {
            val hc = check(headlineClean, digest)
            if (hc is CoachCheck.Rejected) return CoachStructuredCheck.Rejected(hc.reason)
        }
        for (h in highlightsClean) {
            val hCheck = check(h, digest)
            if (hCheck is CoachCheck.Rejected) return CoachStructuredCheck.Rejected(hCheck.reason)
        }
        if (watchoutClean != null) {
            val wc = check(watchoutClean, digest)
            if (wc is CoachCheck.Rejected) return CoachStructuredCheck.Rejected(wc.reason)
        }

        val fallbackSentences = splitSentencesPlain(canonicalStory)
        val finalHeadline = headlineClean ?: fallbackSentences.firstOrNull() ?: canonicalStory.take(120)
        val finalHighlights = if (highlightsClean.isNotEmpty()) {
            highlightsClean
        } else {
            fallbackSentences.drop(1).take(2)
        }
        val finalWatchout = watchoutClean ?: if (highlightsClean.isEmpty()) fallbackSentences.drop(3).firstOrNull() else null
        val verifiedActions = validActionItems(rawActions, digest, tags)

        return CoachStructuredCheck.Ok(
            story = canonicalStory,
            brief = CoachBrief(
                headline = finalHeadline,
                highlights = finalHighlights,
                watchout = finalWatchout,
                actionItems = verifiedActions
            )
        )
    }
}
