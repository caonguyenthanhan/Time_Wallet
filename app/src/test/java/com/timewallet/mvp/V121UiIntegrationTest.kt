package com.timewallet.mvp

import com.timewallet.mvp.domain.model.BudgetPeriod
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.BudgetPolicy
import com.timewallet.mvp.domain.usecase.ConfirmationActionResult
import com.timewallet.mvp.domain.usecase.ConfirmationEngine
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.ValidationResult
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm chứng tích hợp 5 thành phần giao diện gốc Jetpack Compose trong Time Wallet:
 * Timeline dọc, Chat bong bóng + typing indicator, Donut 2D tương tác, FAB + SpeedDial + BackTop > 400px,
 * và Drag & Drop sắp xếp ngân sách / chỉnh nhanh thời lượng bản ghi Dự kiến.
 */
class V121UiIntegrationTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 17)
    private val nowMs = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

    private fun readSrc(relPath: String): String =
        File("src/main/java/com/timewallet/mvp/$relPath").readText(Charsets.UTF_8)

    @Test
    fun `shouldShowBackToTop only activates strictly past 400 px scroll`() {
        assertFalse(shouldShowBackToTop(0))
        assertFalse(shouldShowBackToTop(250))
        assertFalse(shouldShowBackToTop(400))
        assertTrue(shouldShowBackToTop(401))
        assertTrue(shouldShowBackToTop(1200))
    }

    @Test
    fun `resizePlannedRecord adjusts PLANNED duration cleanly and rejects recorded entries`() {
        val futureDate = today.plusDays(1).toString()
        val futureRange = LedgerValidator.parseTimeRangeToEpochMillis(futureDate, "09:00", "10:00", zone)!!
        val planned = (LedgerValidator.validateAndBuildRecord(
            id = "plan_1",
            source = "manual",
            rawTitle = "Ôn tập kiến trúc",
            rawCategory = "learn",
            dateLocal = futureDate,
            inputDurationMinutes = null,
            startedAt = futureRange.first,
            endedAt = futureRange.second,
            existingRecords = emptyList(),
            nowEpochMs = nowMs,
            zoneId = zone,
            convertFutureToPlanned = true
        ) as ValidationResult.Valid).record
        assertEquals(TimeLayer.PLANNED, planned.resolveLayerAt(nowMs))

        val grown = ConfirmationEngine.resizePlannedRecord(planned, deltaMinutes = 15, nowEpochMs = nowMs, zoneId = zone)
        assertTrue(grown is ConfirmationActionResult.Applied)
        val grownRecord = (grown as ConfirmationActionResult.Applied).updatedRecords.single()
        assertEquals(75L * 60L, grownRecord.durationSeconds)
        assertEquals(TimeLayer.PLANNED, grownRecord.resolveLayerAt(nowMs))
        assertEquals(planned.startedAt!! + 75L * 60_000L, grownRecord.endedAt)

        // Không được phép giảm quá giới hạn tối thiểu (< 5 phút)
        val tooSmall = ConfirmationEngine.resizePlannedRecord(grownRecord, deltaMinutes = -75, nowEpochMs = nowMs, zoneId = zone)
        assertTrue(tooSmall is ConfirmationActionResult.Invalid)

        // Hoạt động đã ghi nhận trong quá khứ TUYỆT ĐỐI không được sửa qua tay cầm resize nhanh
        val pastRange = LedgerValidator.parseTimeRangeToEpochMillis(today.toString(), "08:00", "09:00", zone)!!
        val recorded = (LedgerValidator.validateAndBuildRecord(
            id = "rec_1",
            source = "manual",
            rawTitle = "Họp sáng",
            rawCategory = "work",
            dateLocal = today.toString(),
            inputDurationMinutes = null,
            startedAt = pastRange.first,
            endedAt = pastRange.second,
            existingRecords = emptyList(),
            nowEpochMs = nowMs,
            zoneId = zone
        ) as ValidationResult.Valid).record.copy(confirmationStateRaw = ConfirmationState.COMPLETED.name)
        val rejected = ConfirmationEngine.resizePlannedRecord(recorded, deltaMinutes = 15, nowEpochMs = nowMs, zoneId = zone)
        assertTrue(rejected is ConfirmationActionResult.Invalid)
    }

    @Test
    fun `reorderBudgetsPreservingGroup updates priorityOrder within group and blocks cross-group swaps`() {
        val b1 = CategoryTimeBudget(TimeCategory.WORK, BudgetPeriod.WEEKLY, 3600L, isPriority = true, priorityOrder = 0, updatedAt = 10L)
        val b2 = CategoryTimeBudget(TimeCategory.LEARN, BudgetPeriod.WEEKLY, 7200L, isPriority = true, priorityOrder = 1, updatedAt = 10L)
        val b3 = CategoryTimeBudget(TimeCategory.HEALTH, BudgetPeriod.WEEKLY, 1800L, isPriority = false, priorityOrder = 2, updatedAt = 10L)

        val swapped = BudgetPolicy.reorderBudgetsPreservingGroup(listOf(b1, b2, b3), "learn", "work", nowMs = 99L)
        assertEquals(3, swapped.size)
        assertEquals(TimeCategory.LEARN, swapped[0].category)
        assertEquals(0, swapped[0].priorityOrder)
        assertEquals(TimeCategory.WORK, swapped[1].category)
        assertEquals(1, swapped[1].priorityOrder)

        // Không trộn chéo giữa nhóm ưu tiên và nhóm thường
        val crossGroup = BudgetPolicy.reorderBudgetsPreservingGroup(listOf(b1, b2, b3), "work", "health", nowMs = 99L)
        assertTrue(crossGroup.isEmpty())
    }

    @Test
    fun `all five UI components are natively wired into Android screens with proper accessibility`() {
        val history = readSrc("ui/screens/HistoryScreen.kt")
        assertTrue(history.contains(".timelineRail("))
        assertTrue(history.contains("sortTimelineDayRecordsChronological("))
        assertTrue(history.contains("onResizePlannedRecord"))
        assertTrue(history.contains("isInProgress = isInProgress"))

        val chat = readSrc("ui/screens/ChatScreen.kt")
        assertTrue(chat.contains("liveRegion = LiveRegionMode.Polite"))
        assertTrue(chat.contains("WallyTypingDots()"))
        assertTrue(chat.contains("0.75f"))
        assertTrue(chat.contains("bottomStart = 5.dp"))
        assertTrue(chat.contains("bottomEnd = 5.dp"))
        assertTrue(chat.contains("Wally đã xem ✓✓"))

        val uikit = readSrc("ui/screens/UiKit.kt")
        assertTrue(uikit.contains("capDonutSlices(slices, maxSlices = 6)"))
        assertTrue(uikit.contains("hitTestDonutSliceIndex("))
        assertTrue(uikit.contains("animateFloatAsState"))

        val overview = readSrc("ui/screens/OverviewScreen.kt")
        assertTrue(overview.contains("selectedIndex = selectedSliceIndex"))
        assertTrue(overview.contains("onSelectSlice = { selectedSliceIndex = it }"))

        val insights = readSrc("ui/screens/InsightsScreen.kt")
        assertTrue(insights.contains("selectedIndex = selectedSliceIndex"))
        assertTrue(insights.contains("detectDragGestures("))
        assertTrue(insights.contains("BudgetInsertionLine()"))
        assertTrue(insights.contains("↑ Lên") && insights.contains("↓ Xuống"))
        assertFalse(insights.contains("aria-grabbed"))

        val main = readSrc("MainActivity.kt")
        assertTrue(main.contains("FloatingActionGroup("))
        assertTrue(main.contains("SpeedDialActionItem("))
        assertTrue(main.contains("shouldShowBackToTop(scrollState.value)"))
        assertTrue(main.contains("Hỏi nhanh Wally"))
        assertTrue(main.contains("Ghi giờ thủ công") && main.contains("Bắt đầu đồng hồ") && main.contains("Nhìn lại ngày hôm nay"))
    }
}
