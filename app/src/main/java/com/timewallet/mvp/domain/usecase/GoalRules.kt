package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.GoalGroupState
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.GoalVersionStatus
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeCategory
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/** Tiến độ của một mục tiêu trong một kỳ (ngày hoặc tuần T2–CN). */
data class GoalProgress(
    val goal: Goal,
    val periodStart: String,
    val periodEnd: String,
    val usedSeconds: Long,
    val targetSeconds: Long
) {
    val remainingSeconds: Long get() = (targetSeconds - usedSeconds).coerceAtLeast(0L)
    val overSeconds: Long get() = (usedSeconds - targetSeconds).coerceAtLeast(0L)
    /** MIN: đã đạt khi used >= target. MAX: còn trong giới hạn khi used <= target. */
    val met: Boolean get() = if (goal.kind == GoalKind.MIN) usedSeconds >= targetSeconds else usedSeconds <= targetSeconds
    val fraction: Float get() = if (targetSeconds <= 0) 0f else (usedSeconds.toDouble() / targetSeconds).toFloat().coerceIn(0f, 1f)
}

/**
 * v1.1.0 (C): quy tắc mục tiêu — thuần. Nguyên tắc:
 * - Mục tiêu theo Tag chỉ đếm bản ghi có đúng `tagId` ("Gym" KHÔNG làm tăng tiến độ "Chạy bộ"); theo Category đếm mọi bản ghi của Category.
 * - Chỉ tính thời gian đã diễn ra (PLANNED không tính, ACTIVE chỉ phần đã trôi), cùng định nghĩa với Ngân sách và thống kê.
 * - Sửa/tạm dừng/tiếp tục/kết thúc KHÔNG sửa quá khứ: đóng phiên bản đang mở và mở phiên bản mới (xem [Goal]).
 */
object GoalRules {
    const val MAX_DAY_SECONDS = 24 * 3600L
    const val MAX_WEEK_SECONDS = 7 * 24 * 3600L

    sealed class Check {
        object Ok : Check()
        data class Invalid(val message: String) : Check()
    }

    fun checkTarget(period: GoalPeriod, targetSeconds: Long): Check = when {
        targetSeconds < 60L -> Check.Invalid("Mục tiêu tối thiểu 1 phút.")
        period == GoalPeriod.DAY && targetSeconds > MAX_DAY_SECONDS -> Check.Invalid("Mục tiêu theo ngày không vượt quá 24 giờ.")
        period == GoalPeriod.WEEK && targetSeconds > MAX_WEEK_SECONDS -> Check.Invalid("Mục tiêu theo tuần không vượt quá 168 giờ.")
        else -> Check.Ok
    }

    fun create(
        targetType: GoalTargetType,
        targetKey: String,
        kind: GoalKind,
        period: GoalPeriod,
        targetSeconds: Long,
        today: LocalDate,
        nowEpochMs: Long,
        id: String = "goal_${UUID.randomUUID()}",
        groupId: String = "grp_${UUID.randomUUID()}"
    ): Goal = Goal(
        id = id, groupId = groupId, targetType = targetType, targetKey = targetKey, kind = kind, period = period, targetSeconds = targetSeconds,
        status = GoalVersionStatus.ACTIVE, validFrom = today.toString(), validTo = null, createdAt = nowEpochMs, updatedAt = nowEpochMs
    )

    /** Phiên bản mới nhất của nhóm (theo validFrom rồi createdAt). */
    fun latest(versions: List<Goal>): Goal? = versions.maxWithOrNull(compareBy<Goal>({ it.validFrom }, { it.createdAt }))

    fun groupState(versions: List<Goal>): GoalGroupState {
        val last = latest(versions) ?: return GoalGroupState.ENDED
        return when {
            last.validTo != null -> GoalGroupState.ENDED
            last.status == GoalVersionStatus.PAUSED -> GoalGroupState.PAUSED
            else -> GoalGroupState.ACTIVE
        }
    }

    /** Phiên bản có hiệu lực ngày [date] (kể cả PAUSED); null nếu không có. */
    fun versionOn(versions: List<Goal>, date: LocalDate): Goal? = versions.filter {
        val from = LocalDate.parse(it.validFrom)
        val to = it.validTo?.let(LocalDate::parse)
        !date.isBefore(from) && (to == null || !date.isAfter(to))
    }.maxWithOrNull(compareBy<Goal>({ it.validFrom }, { it.createdAt }))

    /**
     * Áp một thay đổi lên phiên bản đang mở [open] vào ngày [today]. Trả về danh sách dòng cần UPSERT.
     * Phiên bản bắt đầu hôm nay chưa từng được đánh giá cho ngày nào trong quá khứ nên được sửa tại chỗ;
     * ngược lại: đóng dòng cũ (validTo = hôm qua) và thêm dòng mới từ hôm nay.
     */
    fun change(
        open: Goal,
        today: LocalDate,
        nowEpochMs: Long,
        newTargetSeconds: Long = open.targetSeconds,
        newKind: GoalKind = open.kind,
        newPeriod: GoalPeriod = open.period,
        newStatus: GoalVersionStatus = open.status,
        newId: String = "goal_${UUID.randomUUID()}"
    ): List<Goal> {
        require(open.validTo == null) { "chỉ thay đổi phiên bản đang mở" }
        val from = LocalDate.parse(open.validFrom)
        val updated = open.copy(targetSeconds = newTargetSeconds, kind = newKind, period = newPeriod, status = newStatus, updatedAt = nowEpochMs)
        if (!from.isBefore(today)) return listOf(updated)
        val closed = open.copy(validTo = today.minusDays(1).toString(), updatedAt = nowEpochMs)
        return listOf(closed, updated.copy(id = newId, validFrom = today.toString(), createdAt = nowEpochMs))
    }

    /** Kết thúc mục tiêu: đóng phiên bản mở; dữ liệu quá khứ giữ nguyên. Kết thúc ngay ngày tạo ⇒ validTo = hôm nay (không để khoảng âm). */
    fun end(open: Goal, today: LocalDate, nowEpochMs: Long): Goal {
        require(open.validTo == null) { "mục tiêu đã kết thúc" }
        val from = LocalDate.parse(open.validFrom)
        val to = if (from.isBefore(today)) today.minusDays(1) else from
        return open.copy(validTo = to.toString(), updatedAt = nowEpochMs)
    }

    fun periodBounds(period: GoalPeriod, date: LocalDate): Pair<LocalDate, LocalDate> = when (period) {
        GoalPeriod.DAY -> date to date
        GoalPeriod.WEEK -> {
            val mon = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            mon to mon.plusDays(6)
        }
    }

    fun qualifies(goal: Goal, r: LedgerRecord): Boolean = when (goal.targetType) {
        GoalTargetType.TAG -> r.tagId != null && r.tagId == goal.targetKey
        GoalTargetType.CATEGORY -> r.category.key == goal.targetKey
    }

    /** Giây đã diễn ra của các bản ghi đạt điều kiện trong khoảng từ ngày `from` đến ngày `to` (ngày địa phương của bản ghi). */
    fun usedSeconds(
        goalTargetType: GoalTargetType,
        goalTargetKey: String,
        records: List<LedgerRecord>,
        from: LocalDate,
        to: LocalDate,
        nowEpochMs: Long,
        zone: ZoneId
    ): Long {
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate().toString()
        var sum = 0L
        for (r in records) {
            val d = runCatching { LocalDate.parse(r.date) }.getOrNull() ?: continue
            if (d.isBefore(from) || d.isAfter(to)) continue
            val ok = if (goalTargetType == GoalTargetType.TAG) r.tagId != null && r.tagId == goalTargetKey else r.category.key == goalTargetKey
            if (!ok) continue
            sum += r.actualElapsedSecondsAt(nowEpochMs, today)
        }
        return sum
    }

    /** Tiến độ của [goal] trong kỳ chứa [date]. */
    fun progress(goal: Goal, records: List<LedgerRecord>, date: LocalDate, nowEpochMs: Long, zone: ZoneId): GoalProgress {
        val (from, to) = periodBounds(goal.period, date)
        val used = usedSeconds(goal.targetType, goal.targetKey, records, from, to, nowEpochMs, zone)
        return GoalProgress(goal, from.toString(), to.toString(), used, goal.targetSeconds)
    }

    /** Dòng thời gian từng ngày cho mục tiêu theo NGÀY: ngày nào có phiên bản ACTIVE hiệu lực thì tính theo đúng mục tiêu lúc đó. */
    data class DayPoint(val date: LocalDate, val goal: Goal, val usedSeconds: Long) {
        val met: Boolean get() = if (goal.kind == GoalKind.MIN) usedSeconds >= goal.targetSeconds else usedSeconds <= goal.targetSeconds
    }

    fun dailySeries(versions: List<Goal>, records: List<LedgerRecord>, from: LocalDate, to: LocalDate, nowEpochMs: Long, zone: ZoneId): List<DayPoint> {
        val out = ArrayList<DayPoint>()
        var d = from
        while (!d.isAfter(to)) {
            val v = versionOn(versions, d)
            if (v != null && v.status == GoalVersionStatus.ACTIVE && v.period == GoalPeriod.DAY) {
                out += DayPoint(d, v, usedSeconds(v.targetType, v.targetKey, records, d, d, nowEpochMs, zone))
            }
            d = d.plusDays(1)
        }
        return out
    }

    /** Dòng tóm tắt một mục tiêu cho giao diện (tiến độ chỉ tính cho mục tiêu ACTIVE). */
    data class Summary(val groupId: String, val name: String, val state: GoalGroupState, val latest: Goal, val progress: GoalProgress?)

    fun summaries(
        goals: List<Goal>,
        records: List<LedgerRecord>,
        tagNameOf: (String) -> String?,
        today: LocalDate,
        nowEpochMs: Long,
        zone: ZoneId,
        includeEnded: Boolean = false
    ): List<Summary> = goals.groupBy { it.groupId }.mapNotNull { (gid, versions) ->
        val last = latest(versions) ?: return@mapNotNull null
        val st = groupState(versions)
        if (st == GoalGroupState.ENDED && !includeEnded) return@mapNotNull null
        Summary(gid, displayName(last, tagNameOf), st, last, if (st == GoalGroupState.ACTIVE) progress(last, records, today, nowEpochMs, zone) else null)
    }.sortedWith(compareBy<Summary>({ it.state.ordinal }, { it.name.lowercase() }))

    fun displayName(goal: Goal, tagNameOf: (String) -> String?): String = when (goal.targetType) {
        GoalTargetType.TAG -> tagNameOf(goal.targetKey) ?: "Tag đã xóa"
        GoalTargetType.CATEGORY -> TimeCategory.fromKeyOrDefault(goal.targetKey).displayName
    }
}
