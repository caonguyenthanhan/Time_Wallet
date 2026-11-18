package com.timewallet.mvp

import com.timewallet.mvp.data.local.BackupParseResult
import com.timewallet.mvp.data.local.BatchConfirm
import com.timewallet.mvp.data.local.ConfirmResult
import com.timewallet.mvp.data.local.LedgerJsonBackupParser
import com.timewallet.mvp.data.local.LedgerJsonExporter
import com.timewallet.mvp.data.local.ManualEntryDraft
import com.timewallet.mvp.data.local.SavedProposalDraft
import com.timewallet.mvp.data.local.TimerAndSettingsStore
import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.EfficiencyTier
import com.timewallet.mvp.domain.usecase.FocusPinKind
import com.timewallet.mvp.domain.usecase.FocusPinPolicy
import com.timewallet.mvp.domain.usecase.GoalRules
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.ManualEntry
import com.timewallet.mvp.domain.usecase.ScheduleBlockVisualState
import com.timewallet.mvp.domain.usecase.ScheduleGridBlock
import com.timewallet.mvp.domain.usecase.ScheduleGridEngine
import com.timewallet.mvp.domain.usecase.SelectedTimeSlot
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Bộ kiểm thử hồi quy cho Wave 1–3 tối ưu UX (v1.3.0):
 * - Chọn nhiều khối giờ trống trên Lịch trình (gộp liền kề cùng ngày, tách rời/khác ngày không bắc cầu).
 * - Đồ thị 24 giờ 4 tầng hiệu quả (Mục tiêu / Phi mục tiêu / Lãng phí / Chưa rõ) với phân loại do người dùng quyết định.
 * - Lưu bền vững cờ `isMarkedWaste` qua JSON backup và draft prefs, cùng cờ `hasSeenAppIntro`.
 * - Hợp đồng giao diện tĩnh cho Chat (Copy/Edit/Revert/✓✎✕), Mở rộng (Thống kê|Công cụ), Hồ sơ & Intro.
 */
class V130UxOptimizationTest {

    private val zone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today: LocalDate = LocalDate.of(2026, 11, 18)

    private fun msAt(date: LocalDate, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(date.year, date.month, date.dayOfMonth, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `ScheduleGridEngine multi-slot merges contiguous same-day gaps and keeps disjoint or cross-day separate`() {
        val occupied = listOf(
            ScheduleGridBlock(
                recordId = "occ_1",
                record = null,
                isActiveTimer = false,
                dateLocal = today.toString(),
                startMinute = 9 * 60,
                endMinute = 9 * 60 + 25,
                totalDurationMinutes = 25,
                title = "Họp nhanh",
                category = TimeCategory.WORK,
                visualState = ScheduleBlockVisualState.COMPLETED
            ),
            ScheduleGridBlock(
                recordId = "occ_2",
                record = null,
                isActiveTimer = false,
                dateLocal = today.toString(),
                startMinute = 11 * 60,
                endMinute = 11 * 60 + 55,
                totalDurationMinutes = 55,
                title = "Khối kín giờ",
                category = TimeCategory.WORK,
                visualState = ScheduleBlockVisualState.COMPLETED
            )
        )

        // 09:25..10:00 (35 phút) -> chọn phần trống lớn nhất trong khung 09h
        val slot9 = ScheduleGridEngine.computeFreeSlotInHour(today.toString(), 9, occupied)
        assertEquals(SelectedTimeSlot(today.toString(), 9 * 60 + 25, 10 * 60), slot9)
        // 11:55..12:00 chỉ còn 5 phút < minSlotMinutes=15 -> trả về null
        val slot11 = ScheduleGridEngine.computeFreeSlotInHour(today.toString(), 11, occupied)
        assertEquals(null, slot11)

        // Toggle thêm rồi chạm lần 2 phải gỡ bỏ đúng khung
        val s10 = SelectedTimeSlot(today.toString(), 10 * 60, 11 * 60)
        val s13 = SelectedTimeSlot(today.toString(), 13 * 60, 14 * 60)
        val tomorrow = today.plusDays(1).toString()
        val sTom0 = SelectedTimeSlot(tomorrow, 0, 60)
        var picked = emptyList<SelectedTimeSlot>()
        picked = ScheduleGridEngine.toggleSelectedSlot(picked, slot9!!)
        picked = ScheduleGridEngine.toggleSelectedSlot(picked, s10)
        picked = ScheduleGridEngine.toggleSelectedSlot(picked, s13)
        picked = ScheduleGridEngine.toggleSelectedSlot(picked, sTom0)
        assertEquals(4, picked.size)

        // Gộp: (09:25..10:00 + 10:00..11:00) gộp thành 09:25..11:00; 13:00..14:00 tách rời; ngày mai 00:00..01:00 tách theo ngày
        val merged = ScheduleGridEngine.mergeSelectedEmptySlots(picked)
        assertEquals(3, merged.size)
        assertEquals(today.toString(), merged[0].dateLocal)
        assertEquals(9 * 60 + 25, merged[0].startMinute)
        assertEquals(11 * 60, merged[0].endMinute)
        assertEquals(today.toString(), merged[1].dateLocal)
        assertEquals(13 * 60, merged[1].startMinute)
        assertEquals(14 * 60, merged[1].endMinute)
        assertEquals(tomorrow, merged[2].dateLocal)
        assertEquals(0, merged[2].startMinute)
        assertEquals(60, merged[2].endMinute)

        // Toggle lần 2 trên s13 -> bỏ s13 khỏi danh sách
        val removed = ScheduleGridEngine.toggleSelectedSlot(picked, s13)
        assertEquals(3, removed.size)
        assertFalse(removed.contains(s13))
    }

    @Test
    fun `validateMultiSlot and BatchConfirm persist separate independent records without inventing bridge time`() {
        val merged = ScheduleGridEngine.mergeSelectedEmptySlots(
            listOf(
                SelectedTimeSlot(today.toString(), 14 * 60, 15 * 60),
                SelectedTimeSlot(today.toString(), 15 * 60, 16 * 60),
                SelectedTimeSlot(today.toString(), 19 * 60, 20 * 60)
            )
        )
        assertEquals(2, merged.size)

        val validated = ManualEntry.validateMultiSlot(
            slots = merged,
            categoryKey = TimeCategory.LEARN.key,
            tagId = null,
            tagName = "Ôn thi",
            note = "Luyện đề",
            today = today,
            saveAsPlanned = true,
            isMarkedWaste = false
        ).getOrThrow()
        assertEquals(2, validated.size)
        assertEquals("14:00", validated[0].startHm)
        assertEquals("16:00", validated[0].endHm)
        assertEquals("19:00", validated[1].startHm)
        assertEquals("20:00", validated[1].endHm)

        val inputs = ManualEntry.toMultiSlotBatchInputs(validated, sessionId = "ms_sess_1")
        val dao = InMemoryLedgerDao()
        val nowMs = msAt(today, 8, 0)
        val (result, saved) = runBlocking {
            val r = BatchConfirm.run(
                dao = dao,
                inputs = inputs,
                activeTimer = null,
                readActiveTimer = { null },
                nowEpochMs = nowMs,
                zone = zone,
                readNowMs = { nowMs }
            )
            r to dao.getAllRecordsSnapshot().sortedBy { it.startedAt }
        }
        assertTrue(result is ConfirmResult.Committed)
        assertEquals(2, saved.size)
        assertEquals(7200L, saved[0].durationSeconds)
        assertEquals(3600L, saved[1].durationSeconds)
        assertEquals("PLANNED", saved[0].status)
        assertEquals("Luyện đề", saved[0].title)
    }

    @Test
    fun `computeWeeklyEfficiency enforces 24h sum invariant, NEUTRAL default for rest-entertainment, and WASTE overlap priority`() {
        val targetDay = today.minusDays(1)
        val nowMs = msAt(today, 22, 0)
        val goalWork = GoalRules.create(
            targetType = GoalTargetType.CATEGORY,
            targetKey = TimeCategory.WORK.key,
            kind = GoalKind.MIN,
            period = GoalPeriod.DAY,
            targetSeconds = 2 * 3600L,
            today = targetDay,
            nowEpochMs = msAt(targetDay, 7, 0),
            id = "g_work"
        )

        val records = listOf(
            // 09:00..11:00 WORK (GOAL = 2h = 7200s), nhưng 10:30..11:00 trùng với bản ghi được user đánh dấu lãng phí -> ưu tiên WASTE
            LedgerRecord(
                id = "rec_work",
                source = "manual",
                layer = TimeLayer.REPORTED_TIMED,
                createdAt = msAt(targetDay, 11, 5),
                userConfirmedAt = msAt(targetDay, 11, 5),
                category = TimeCategory.WORK,
                title = "Làm dự án",
                date = targetDay.toString(),
                durationSeconds = 7200L,
                startedAt = msAt(targetDay, 9, 0),
                endedAt = msAt(targetDay, 11, 0),
                timezone = zone.id,
                status = "CONFIRMED",
                idempotencyKey = "k_work"
            ),
            // 10:30..11:00 ENTERTAINMENT với isMarkedWaste = true -> WASTE (1800s)
            LedgerRecord(
                id = "rec_waste",
                source = "manual",
                layer = TimeLayer.REPORTED_TIMED,
                createdAt = msAt(targetDay, 11, 5),
                userConfirmedAt = msAt(targetDay, 11, 5),
                category = TimeCategory.FUN,
                title = "Lướt mạng",
                date = targetDay.toString(),
                durationSeconds = 1800L,
                startedAt = msAt(targetDay, 10, 30),
                endedAt = msAt(targetDay, 11, 0),
                timezone = zone.id,
                status = "CONFIRMED",
                idempotencyKey = "k_waste",
                isMarkedWaste = true
            ),
            // 20:00..21:00 ENTERTAINMENT KHÔNG đánh dấu lãng phí -> phải xếp vào NEUTRAL (Phi mục tiêu = 3600s)
            LedgerRecord(
                id = "rec_chill",
                source = "manual",
                layer = TimeLayer.REPORTED_TIMED,
                createdAt = msAt(targetDay, 21, 5),
                userConfirmedAt = msAt(targetDay, 21, 5),
                category = TimeCategory.FUN,
                title = "Xem phim thư giãn",
                date = targetDay.toString(),
                durationSeconds = 3600L,
                startedAt = msAt(targetDay, 20, 0),
                endedAt = msAt(targetDay, 21, 0),
                timezone = zone.id,
                status = "CONFIRMED",
                idempotencyKey = "k_chill",
                isMarkedWaste = false
            ),
            // 00:00..07:00 SLEEP -> NEUTRAL (7h = 25200s)
            LedgerRecord(
                id = "rec_sleep",
                source = "manual",
                layer = TimeLayer.REPORTED_TIMED,
                createdAt = msAt(targetDay, 7, 10),
                userConfirmedAt = msAt(targetDay, 7, 10),
                category = TimeCategory.SLEEP,
                title = "Ngủ đêm",
                date = targetDay.toString(),
                durationSeconds = 7 * 3600L,
                startedAt = msAt(targetDay, 0, 0),
                endedAt = msAt(targetDay, 7, 0),
                timezone = zone.id,
                status = "CONFIRMED",
                idempotencyKey = "k_sleep",
                isMarkedWaste = false
            ),
            // Chưa gắn giờ: 45 phút REST -> KHÔNG được cộng vào cột 24h! Phải nằm ở untimedSeconds.
            LedgerRecord(
                id = "rec_untimed",
                source = "manual",
                layer = TimeLayer.REPORTED_UNTIMED,
                createdAt = msAt(targetDay, 18, 0),
                userConfirmedAt = msAt(targetDay, 18, 0),
                category = TimeCategory.REST,
                title = "Nghỉ trưa chưa gắn giờ",
                date = targetDay.toString(),
                durationSeconds = 2700L,
                startedAt = null,
                endedAt = null,
                timezone = zone.id,
                status = "CONFIRMED",
                idempotencyKey = "k_untimed",
                isMarkedWaste = false
            )
        )

        val summary = LedgerAnalyticsCalculator.computeWeeklyEfficiency(
            endDateLocal = today.toString(),
            records = records,
            goals = listOf(goalWork),
            zoneId = zone,
            nowEpochMs = nowMs
        )

        assertEquals(7, summary.days.size)
        summary.days.forEach { d ->
            assertEquals(
                "Tổng 4 tầng phải đúng bằng calendarDaySeconds trên ngày ${d.dateLocal}",
                d.calendarDaySeconds,
                d.goalSeconds + d.neutralSeconds + d.wasteSeconds + d.unknownSeconds
            )
        }

        val dayBreakdown = summary.days.first { it.dateLocal == targetDay.toString() }
        // GOAL = 09:00..10:30 = 5400s (không bị tính trùng 10:30..11:00)
        assertEquals(5400L, dayBreakdown.goalSeconds)
        // WASTE = 10:30..11:00 = 1800s
        assertEquals(1800L, dayBreakdown.wasteSeconds)
        // NEUTRAL = SLEEP 7h (25200s) + ENTERTAINMENT không gắn cờ waste 1h (3600s) = 28800s
        assertEquals(28800L, dayBreakdown.neutralSeconds)
        // UNTIMED tách riêng 2700s
        assertEquals(2700L, dayBreakdown.untimedSeconds)
        assertEquals(2700L, summary.totalUntimedSeconds)
        assertFalse(summary.actionableInsight.contains("mô phỏng", ignoreCase = true))
    }

    @Test
    fun `JSON export and backup parser preserve isMarkedWaste and default legacy JSON without it to false`() {
        val recWaste = LedgerRecord(
            id = "w1",
            source = "manual",
            layer = TimeLayer.REPORTED_UNTIMED,
            createdAt = 1000L,
            userConfirmedAt = 1000L,
            category = TimeCategory.FUN,
            title = "Cuộn video khuya",
            date = today.toString(),
            durationSeconds = 1800L,
            startedAt = null,
            endedAt = null,
            timezone = zone.id,
            status = "CONFIRMED",
            idempotencyKey = "k_w1",
            isMarkedWaste = true
        )
        val exported = LedgerJsonExporter.exportToJsonString(records = listOf(recWaste), activeTimer = null)
        assertTrue(exported.contains("\"isMarkedWaste\": true"))

        val parsed = LedgerJsonBackupParser.parse(exported, nowEpochMs = msAt(today, 23, 0))
        assertTrue(parsed is BackupParseResult.Valid)
        val restored = (parsed as BackupParseResult.Valid).payload.records.single()
        assertTrue(restored.isMarkedWaste)

        // JSON cũ không có trường isMarkedWaste phải parse hợp lệ với giá trị mặc định false
        val obj = JSONObject(exported)
        obj.getJSONArray("items").getJSONObject(0).remove("isMarkedWaste")
        val parsedLegacy = LedgerJsonBackupParser.parse(obj.toString(), nowEpochMs = msAt(today, 23, 0))
        assertTrue(parsedLegacy is BackupParseResult.Valid)
        assertFalse((parsedLegacy as BackupParseResult.Valid).payload.records.single().isMarkedWaste)
    }

    @Test
    fun `TimerAndSettingsStore persists appIntro flag and isMarkedWaste on manual and proposal drafts`() {
        val prefs = InMemorySharedPreferences()
        val store = TimerAndSettingsStore(prefs)
        assertFalse(store.hasSeenAppIntro())

        store.setAppIntroSeen(true)
        assertTrue(TimerAndSettingsStore(prefs).hasSeenAppIntro())

        store.saveManualEntryDraft(
            ManualEntryDraft(
                sessionId = "s_waste",
                categoryKey = TimeCategory.FUN.key,
                minutesText = "30",
                dateLocal = today.toString(),
                note = "Mạng xã hội",
                isMarkedWaste = true
            ),
            immediate = true
        )
        store.saveChatProposalsDraft(
            listOf(
                SavedProposalDraft(
                    id = "p_waste",
                    rawInput = "Lướt video 30 phút",
                    title = "Lướt video",
                    categoryKey = TimeCategory.FUN.key,
                    durationMinutesText = "30",
                    dateLocal = today.toString(),
                    startLocal = "",
                    endLocal = "",
                    hasInitialTimeEvidence = false,
                    isFuturePlanned = false,
                    idempotencyKey = "k_p_waste",
                    isMarkedWaste = true
                )
            ),
            immediate = true
        )

        val reloaded = TimerAndSettingsStore(prefs)
        assertEquals(true, reloaded.loadManualEntryDraft()?.isMarkedWaste)
        assertEquals(true, reloaded.loadChatProposalsDraft().single().isMarkedWaste)
    }

    @Test
    fun `v130 UX screens include required interactions, scientific rationale and no leftover mock strings`() {
        val chat = File("src/main/java/com/timewallet/mvp/ui/screens/ChatScreen.kt").readText()
        listOf("Copy", "Revert changes", "Phân tích lại", "Đã nhận dự kiến", "Đã nhận có giờ", "Xác nhận đề xuất này", "Bỏ đề xuất này")
            .forEach { assertTrue("ChatScreen thiếu \"$it\"", chat.contains(it)) }

        val insights = File("src/main/java/com/timewallet/mvp/ui/screens/InsightsScreen.kt").readText()
        listOf("Thống kê", "Công cụ", "Phân bổ 24 giờ · 7 ngày", "Tiến độ mục tiêu", "AASM", "Albulescu", "Sonnentag")
            .forEach { assertTrue("InsightsScreen thiếu \"$it\"", insights.contains(it)) }
        assertFalse(insights.contains("Nhận xét cố định từ dữ liệu mô phỏng"))
        assertFalse(insights.contains("Đặt giờ trên lịch"))
        assertFalse("tra cứu khoa học là chỉ thị nghiên cứu, không dùng làm tag mẫu", insights.contains("tra cứu khoa học", ignoreCase = true))

        val profile = File("src/main/java/com/timewallet/mvp/ui/screens/ProfileSettingsScreen.kt").readText()
        assertTrue(profile.contains("fun ProfileSettingsScreen(") && profile.contains("fun AppIntroDialog("))
        assertTrue(profile.contains("Bỏ qua") && profile.contains("AASM"))

        val schedule = File("src/main/java/com/timewallet/mvp/ui/screens/ScheduleCalendarView.kt").readText()
        assertTrue(schedule.contains("SelectedSlotsActionBar") && schedule.contains("ModalBottomSheet"))

        val notif = File("src/main/java/com/timewallet/mvp/notifications/CompletionReminder.kt").readText()
        assertTrue(notif.contains("FocusPinNotificationManager") && notif.contains("ACTION_UNPIN_FOCUS") && notif.contains(".setOngoing(true)"))
        val timer = File("src/main/java/com/timewallet/mvp/ui/screens/TimerScreen.kt").readText()
        assertTrue(timer.contains("Ghim thông báo tập trung") && profile.contains("Ghim thông báo tập trung") && insights.contains("Ghim thông báo tập trung"))
    }

    @Test
    fun `FocusPinPolicy prioritizes active timer, current plan, then last week - yesterday - recent 3 days`() {
        val nowMs = msAt(today, 16, 30)
        val lastWeekDay = today.minusDays(7)
        val yesterday = today.minusDays(1)
        val twoDaysAgo = today.minusDays(2)
        val threeDaysAgo = today.minusDays(3)

        fun timedRec(
            id: String,
            d: LocalDate,
            h1: Int,
            m1: Int,
            h2: Int,
            m2: Int,
            cat: TimeCategory,
            title: String,
            layer: TimeLayer = TimeLayer.MEASURED,
            status: String = "CONFIRMED",
            conf: ConfirmationState? = ConfirmationState.COMPLETED,
            waste: Boolean = false
        ) = LedgerRecord(
            id = id,
            source = "manual",
            layer = layer,
            createdAt = msAt(d, h1, m1),
            userConfirmedAt = msAt(d, h2, m2),
            category = cat,
            title = title,
            date = d.toString(),
            durationSeconds = (msAt(d, h2, m2) - msAt(d, h1, m1)) / 1000L,
            startedAt = msAt(d, h1, m1),
            endedAt = msAt(d, h2, m2),
            timezone = zone.id,
            status = status,
            idempotencyKey = "k_$id",
            confirmationStateRaw = conf?.name,
            isMarkedWaste = waste
        )

        val currentPlan = timedRec(
            id = "p_now",
            d = today,
            h1 = 16,
            m1 = 0,
            h2 = 17,
            m2 = 15,
            cat = TimeCategory.WORK,
            title = "Deep Work kiến trúc",
            layer = TimeLayer.PLANNED,
            status = "PLANNED",
            conf = ConfirmationState.PLANNED
        )
        val recLastWeek = timedRec("lw", lastWeekDay, 16, 0, 17, 0, TimeCategory.LEARN, "Học thuật toán")
        val recYesterday = timedRec("yd", yesterday, 16, 15, 17, 0, TimeCategory.WORK, "Review code tối")
        val rec2Days = timedRec("d2a", twoDaysAgo, 16, 10, 16, 55, TimeCategory.HEALTH, "Chạy bộ chiều")
        val rec3Days = timedRec("d3a", threeDaysAgo, 16, 20, 17, 5, TimeCategory.HEALTH, "Chạy bộ chiều")
        val recWaste = timedRec("wastef", lastWeekDay, 16, 25, 16, 55, TimeCategory.FUN, "Cuộn video vô thức", waste = true)

        // 1. ActiveTimer thắng mọi thứ
        val timer = ActiveTimerSession(
            title = "Đọc tài liệu thiết kế",
            categoryKey = TimeCategory.LEARN.key,
            startedAtEpochMs = msAt(today, 16, 10),
            startedElapsedRealtimeMs = 1000L,
            timezone = zone.id
        )
        val activeState = FocusPinPolicy.resolve(listOf(currentPlan, recLastWeek), timer, nowMs, zone)
        assertEquals(FocusPinKind.ACTIVE_TIMER, activeState.kind)
        assertTrue(activeState.headline.contains("Đọc tài liệu thiết kế"))

        // 2. Có kế hoạch giờ hiện tại (không có timer) -> hiển thị việc theo kế hoạch + nextTransition = endedAt
        val planState = FocusPinPolicy.resolve(listOf(currentPlan, recLastWeek, recYesterday), null, nowMs, zone)
        assertEquals(FocusPinKind.CURRENT_PLANNED, planState.kind)
        assertTrue(planState.headline.contains("Deep Work kiến trúc"))
        assertEquals(msAt(today, 17, 15), planState.nextTransitionEpochMs)

        // 3. Không có kế hoạch giờ này -> ưu tiên Giờ này tuần trước (và bỏ qua bản ghi isMarkedWaste)
        val lastWeekState = FocusPinPolicy.resolve(listOf(recLastWeek, recYesterday, rec2Days, rec3Days, recWaste), null, nowMs, zone)
        assertEquals(FocusPinKind.SUGGESTION_LAST_WEEK, lastWeekState.kind)
        assertEquals("Học thuật toán", lastWeekState.suggestedTitle)

        // 4. Không có tuần trước -> ưu tiên Giờ này hôm qua
        val yesterdayState = FocusPinPolicy.resolve(listOf(recYesterday, rec2Days, rec3Days, recWaste), null, nowMs, zone)
        assertEquals(FocusPinKind.SUGGESTION_YESTERDAY, yesterdayState.kind)
        assertEquals("Review code tối", yesterdayState.suggestedTitle)

        // 5. Không có tuần trước & hôm qua -> gợi ý từ 3 ngày gần đây (gộp tần suất 2/3 ngày)
        val recent3State = FocusPinPolicy.resolve(listOf(rec2Days, rec3Days, recWaste), null, nowMs, zone)
        assertEquals(FocusPinKind.SUGGESTION_RECENT_3_DAYS, recent3State.kind)
        assertEquals("Chạy bộ chiều", recent3State.suggestedTitle)
        assertTrue(recent3State.bodyText.contains("2/3 ngày"))

        // 6. Không có lịch sử phù hợp (bỏ qua waste & untimed) -> EMPTY_NO_HISTORY
        val emptyState = FocusPinPolicy.resolve(listOf(recWaste), null, nowMs, zone)
        assertEquals(FocusPinKind.EMPTY_NO_HISTORY, emptyState.kind)
    }

    @Test
    fun `TimerAndSettingsStore persists focusPinEnabled flag across instances`() {
        val prefs = InMemorySharedPreferences()
        val store = TimerAndSettingsStore(prefs)
        assertFalse(store.isFocusPinEnabled())
        store.setFocusPinEnabled(true, immediate = true)
        assertTrue(TimerAndSettingsStore(prefs).isFocusPinEnabled())
        store.setFocusPinEnabled(false, immediate = true)
        assertFalse(TimerAndSettingsStore(prefs).isFocusPinEnabled())
    }
}
