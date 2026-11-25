package com.timewallet.mvp

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.BudgetPeriod
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.GoalGroupState
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.GoalVersionStatus
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.AiQueryValidator
import com.timewallet.mvp.domain.usecase.GoalDisplayMath
import com.timewallet.mvp.domain.usecase.GoalRules
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.LedgerQueryEngine
import com.timewallet.mvp.domain.usecase.TagRules
import com.timewallet.mvp.domain.usecase.WallyContextEngine
import com.timewallet.mvp.ui.Wally20Coordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * v2.2.4 — Đóng lỗi hiển thị hụt 1 phút ở GoalsDialog (`1h 31p + 3h 28p = 4h 59p` trên mục tiêu `5h`):
 * mọi bề mặt (`GoalsDialog`, `PersonalHubCard`, `Công việc › Mục tiêu`, `Thống kê`, `Chat` và `WallyContextEngine`)
 * đều dùng chung phép chuẩn hóa `GoalDisplayMath`.
 */
class V224GoalRoundingConsistencyTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 25)
    private val nowMs = ZonedDateTime.of(today, LocalTime.of(17, 40, 28), zone).toInstant().toEpochMilli()

    private val ieltsTag = Tag(
        id = "tag_ielts",
        name = "IELTS",
        nameKey = TagRules.nameKey("IELTS"),
        category = TimeCategory.LEARN,
        archivedAt = null,
        createdAt = nowMs - 86_400_000L,
        updatedAt = nowMs - 86_400_000L
    )

    private val ieltsMinGoal = Goal(
        id = "goal_ielts_5h",
        groupId = "grp_ielts_5h",
        targetType = GoalTargetType.TAG,
        targetKey = ieltsTag.id,
        kind = GoalKind.MIN,
        period = GoalPeriod.WEEK,
        targetSeconds = 5 * 3600L,
        isPriority = true,
        status = GoalVersionStatus.ACTIVE,
        validFrom = today.minusDays(2).toString(),
        validTo = null,
        createdAt = nowMs - 86_400_000L,
        updatedAt = nowMs - 86_400_000L
    )

    @Test
    fun `b06 regression - 1h 31m 28s on 5h goal yields 1h 31p used and 3h 29p remaining across every surface`() {
        val logged90m = LedgerRecord(
            id = "rec_90m",
            source = "manual",
            layer = TimeLayer.REPORTED_TIMED,
            createdAt = nowMs - 10_800_000L,
            userConfirmedAt = nowMs - 5_400_000L,
            category = TimeCategory.LEARN,
            title = "Luyện nghe IELTS",
            tagId = ieltsTag.id,
            date = today.toString(),
            durationSeconds = 90 * 60L,
            startedAt = nowMs - 10_800_000L,
            endedAt = nowMs - 5_400_000L,
            timezone = zone.id,
            status = "CONFIRMED",
            idempotencyKey = "k_90m"
        )
        // Đang đo live 88s (01:28) -> tổng 5488s (91m 28s), từng làm GoalsDialog báo 1h 31p + Còn thiếu 3h 28p.
        val live88s = ActiveTimerSession(
            title = "Live Timer QA",
            categoryKey = TimeCategory.LEARN.key,
            startedAtEpochMs = nowMs - 88_000L,
            startedElapsedRealtimeMs = 1_000L,
            timezone = zone.id,
            tagId = ieltsTag.id
        )
        val records = listOf(logged90m)

        // 1. GoalsDialog / PersonalHubCard (GoalDisplayMath.rowLabels)
        val unified = Wally20Coordinator.buildUnifiedGoalSummaries(
            goals = listOf(ieltsMinGoal),
            budgets = emptyList(),
            records = records,
            tagNameOf = { id -> if (id == ieltsTag.id) ieltsTag.name else null },
            today = today,
            nowEpochMs = nowMs,
            zone = zone,
            activeTimer = live88s
        )
        val summary = unified.single()
        assertEquals(5488L, summary.progress?.usedSeconds)
        val rowLabels = GoalDisplayMath.rowLabels(summary)
        assertEquals("≥ 5h/tuần", rowLabels.headerTargetText)
        assertEquals("1h 31p / 5h", rowLabels.progressText)
        assertEquals("Còn thiếu 3h 29p", rowLabels.statusText)

        // 2. Thống kê (LedgerAnalyticsCalculator.computeWeeklyEfficiency)
        val weeklyEff = LedgerAnalyticsCalculator.computeWeeklyEfficiency(
            endDateLocal = today.toString(),
            records = records,
            goals = listOf(ieltsMinGoal),
            budgets = emptyList(),
            tags = listOf(ieltsTag),
            zoneId = zone,
            nowEpochMs = nowMs,
            activeTimer = live88s
        )
        val statsItem = weeklyEff.goalItems.single()
        assertEquals("1h 31p / 5h", statsItem.progressText)
        assertTrue(
            "Thống kê phải nêu 1h 31p và 3h 29p: ${weeklyEff.actionableInsight}",
            weeklyEff.actionableInsight.contains("1h 31p") && weeklyEff.actionableInsight.contains("3h 29p")
        )

        // 3. Công việc › Mục tiêu (dùng GoalDisplayMath.from)
        val tasksTabMath = GoalDisplayMath.from(summary.progress!!.usedSeconds, summary.latest.targetSeconds)
        assertEquals("1h 31p / 5h", "${tasksTabMath.usedLabel} / ${tasksTabMath.targetLabel}")

        // 4. Chat (LedgerQueryEngine BUDGET)
        val intent = (AiQueryValidator.validate("BUDGET", today.toString(), today.toString(), null, null, null, today) as AiQueryValidator.Result.Ok).intent
        val chatAns = LedgerQueryEngine.execute(
            intent = intent,
            records = records,
            zoneId = zone,
            nowEpochMs = nowMs,
            activeTimer = live88s,
            budgets = emptyList(),
            tags = listOf(ieltsTag),
            goals = listOf(ieltsMinGoal)
        )
        val chatText = (listOf(chatAns.headline) + chatAns.lines).joinToString("\n")
        assertTrue("Chat phải có 1h 31p: $chatText", chatText.contains("1h 31p"))
        assertTrue("Chat phải có 3h 29p: $chatText", chatText.contains("3h 29p"))
        assertFalse("Chat không được hụt thành 3h 28p: $chatText", chatText.contains("3h 28p"))

        // 5. Ngữ cảnh AI (WallyContextEngine.computeGoalDeficits)
        val deficits = WallyContextEngine.computeGoalDeficits(
            goals = listOf(ieltsMinGoal),
            records = records,
            tagById = mapOf(ieltsTag.id to ieltsTag),
            today = today,
            focusCategory = TimeCategory.LEARN,
            nowEpochMs = nowMs,
            zone = zone,
            activeTimer = live88s
        )
        assertEquals(91, deficits.single().usedMinutes)
        assertEquals(209, deficits.single().remainingMinutes)
        assertEquals(300, deficits.single().targetMinutes)
    }

    @Test
    fun `boundary seconds never sum over or under target across MIN, MAX, and TimeBudgetProgress`() {
        // 5490s (91m 30s) -> used = 92m (1h 32p), remaining = 208m (3h 28p) -> 92 + 208 = 300m
        val halfBoundary = GoalDisplayMath.from(5490L, 18_000L)
        assertEquals("1h 32p", halfBoundary.usedLabel)
        assertEquals("3h 28p", halfBoundary.remainingLabel)
        assertEquals(300L, halfBoundary.usedMinutes + halfBoundary.remainingMinutes)

        // 5428s (90m 28s) -> used = 90m (90p), remaining = 210m (3h 30p) -> 90 + 210 = 300m
        val under90Boundary = GoalDisplayMath.from(5428L, 18_000L)
        assertEquals("90p", under90Boundary.usedLabel)
        assertEquals("3h 30p", under90Boundary.remainingLabel)
        assertEquals(300L, under90Boundary.usedMinutes + under90Boundary.remainingMinutes)

        // Gần đạt MIN (< 30s nữa là đủ 5h): không hiện "Còn thiếu 0p" khi !met
        val minNearlyMet = GoalRules.Summary(
            groupId = ieltsMinGoal.groupId,
            name = "IELTS",
            state = GoalGroupState.ACTIVE,
            latest = ieltsMinGoal,
            progress = GoalRules.progress(
                ieltsMinGoal,
                listOf(
                    LedgerRecord(
                        id = "almost",
                        source = "manual",
                        layer = TimeLayer.REPORTED_TIMED,
                        createdAt = nowMs - 18_000_000L,
                        userConfirmedAt = nowMs,
                        category = TimeCategory.LEARN,
                        title = "IELTS",
                        tagId = ieltsTag.id,
                        date = today.toString(),
                        durationSeconds = 17_985L,
                        startedAt = nowMs - 17_985_000L,
                        endedAt = nowMs,
                        timezone = zone.id,
                        status = "CONFIRMED",
                        idempotencyKey = "k_almost"
                    )
                ),
                today,
                nowMs,
                zone
            )
        )
        assertEquals("Còn thiếu < 1p", GoalDisplayMath.rowLabels(minNearlyMet).statusText)

        val pausedLabels = GoalDisplayMath.rowLabels(
            minNearlyMet.copy(state = GoalGroupState.PAUSED, progress = null)
        )
        assertEquals("≥ 5h/tuần", pausedLabels.headerTargetText)
        assertEquals("Đang tạm dừng", pausedLabels.statusText)

        // MAX trong giới hạn và vượt < 30s
        val funMaxGoal = ieltsMinGoal.copy(
            id = "goal_fun_max",
            groupId = "grp_fun_max",
            targetType = GoalTargetType.CATEGORY,
            targetKey = TimeCategory.FUN.key,
            kind = GoalKind.MAX
        )
        val funWithin = GoalRules.Summary(
            groupId = funMaxGoal.groupId,
            name = "Giải trí",
            state = GoalGroupState.ACTIVE,
            latest = funMaxGoal,
            progress = GoalRules.progress(
                funMaxGoal,
                emptyList(),
                today,
                nowMs,
                zone,
                activeTimer = ActiveTimerSession("Chơi", TimeCategory.FUN.key, nowMs - 5_488_000L, 1L, zone.id)
            )
        )
        val withinLabels = GoalDisplayMath.rowLabels(funWithin)
        assertEquals("1h 31p / 5h", withinLabels.progressText)
        assertEquals("Còn 3h 29p trong giới hạn", withinLabels.statusText)

        val funSlightOver = funWithin.copy(
            progress = GoalRules.progress(
                funMaxGoal,
                emptyList(),
                today,
                nowMs,
                zone,
                activeTimer = ActiveTimerSession("Chơi", TimeCategory.FUN.key, nowMs - 18_015_000L, 1L, zone.id)
            )
        )
        assertEquals("Vượt < 1p", GoalDisplayMath.rowLabels(funSlightOver).statusText)

        // TimeBudgetProgress cũng không bị hụt 1p khi có giây lẻ
        val budget = CategoryTimeBudget(TimeCategory.LEARN, BudgetPeriod.WEEKLY, 5 * 3600L, true, 0, nowMs)
        val bp = com.timewallet.mvp.domain.model.TimeBudgetProgress(
            budget = budget,
            periodStartDateLocal = today.toString(),
            periodEndDateLocal = today.toString(),
            usedSeconds = 5488L,
            targetSeconds = 18_000L,
            remainingSeconds = 18_000L - 5488L,
            exceededSeconds = 0L,
            deltaSeconds = -(18_000L - 5488L)
        )
        assertEquals("1h 31p", bp.usedFormatted)
        assertEquals("Còn 3h 29p", bp.combinedDifferenceLabelVi)
        assertEquals("Còn 3h 29p", bp.differenceLabelVi)
    }

    @Test
    fun `UI sources route GoalProgressRow and Goals tab through GoalDisplayMath without floor division`() {
        val personalSrc = File("src/main/java/com/timewallet/mvp/ui/screens/PersonalScreens.kt").readText()
        val rowStart = personalSrc.indexOf("fun GoalProgressRow(")
        val rowEnd = personalSrc.indexOf("fun PersonalHubCard(")
        assertTrue(rowStart > 0 && rowEnd > rowStart)
        val rowBody = personalSrc.substring(rowStart, rowEnd)
        assertTrue("GoalProgressRow phải dùng GoalDisplayMath.rowLabels", rowBody.contains("GoalDisplayMath.rowLabels(summary)"))
        assertFalse("GoalProgressRow không được chia sàn / 60", rowBody.contains("/ 60"))

        val tasksSrc = File("src/main/java/com/timewallet/mvp/ui/screens/TasksHabitsScreen.kt").readText()
        val goalsStart = tasksSrc.indexOf("TasksHubSection.GOALS ->")
        val goalsEnd = tasksSrc.indexOf("TasksHubSection.KANBAN ->")
        assertTrue(goalsStart > 0 && goalsEnd > goalsStart)
        val goalsBody = tasksSrc.substring(goalsStart, goalsEnd)
        assertTrue("Tab Mục tiêu phải dùng GoalDisplayMath.from", goalsBody.contains("GoalDisplayMath.from(used, goal.targetSeconds)"))
    }
}
