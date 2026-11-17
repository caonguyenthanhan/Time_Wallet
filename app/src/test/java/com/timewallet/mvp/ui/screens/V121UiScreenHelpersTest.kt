package com.timewallet.mvp.ui.screens

import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.DonutSlice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm tra các hàm thuần hỗ trợ trình bày UI của 5 thành phần mới:
 * cap tối đa 6 lát Donut, hit-test tọa độ lát cắt Donut, sắp xếp timeline tăng dần theo giờ, và tính vị trí thả kéo-thả.
 */
class V121UiScreenHelpersTest {

    @Test
    fun `capDonutSlices limits slices to 6 without duplicating OTHER and preserves total`() {
        val many = listOf(
            DonutSlice(TimeCategory.WORK, 600L, 0.30f),
            DonutSlice(TimeCategory.LEARN, 400L, 0.20f),
            DonutSlice(TimeCategory.HEALTH, 300L, 0.15f),
            DonutSlice(TimeCategory.LIFE, 200L, 0.10f),
            DonutSlice(TimeCategory.REST, 150L, 0.075f),
            DonutSlice(TimeCategory.SOCIAL, 150L, 0.075f),
            DonutSlice(TimeCategory.FUN, 100L, 0.05f),
            DonutSlice(TimeCategory.SLEEP, 100L, 0.05f)
        )
        val capped = capDonutSlices(many, maxSlices = 6)
        assertEquals(6, capped.size)
        assertEquals(TimeCategory.OTHER, capped.last().category)
        assertEquals(1, capped.count { it.category == TimeCategory.OTHER })
        assertEquals(2000L, capped.sumOf { it.seconds })
        assertEquals(1f, capped.sumOf { it.fraction.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun `hitTestDonutSliceIndex resolves quadrants accurately and clears on center hole`() {
        val slices = listOf(
            DonutSlice(TimeCategory.WORK, 1800L, 0.5f),   // 0° -> 180° (nửa phải từ 12h -> 6h)
            DonutSlice(TimeCategory.LEARN, 1800L, 0.5f)   // 180° -> 360° (nửa trái từ 6h -> 12h)
        )
        // Canvas 200x200, tâm (100,100), ring = 24px -> bán kính vòng ở ~88px
        assertEquals(0, hitTestDonutSliceIndex(slices, touchX = 185f, touchY = 100f, width = 200f, height = 200f, ringWidthPx = 24f))
        assertEquals(1, hitTestDonutSliceIndex(slices, touchX = 15f, touchY = 100f, width = 200f, height = 200f, ringWidthPx = 24f))
        // Chạm giữa lỗ donut -> null (bỏ chọn)
        assertNull(hitTestDonutSliceIndex(slices, touchX = 100f, touchY = 100f, width = 200f, height = 200f, ringWidthPx = 24f))
    }

    @Test
    fun `sortTimelineDayRecordsChronological orders timed ascending and untimed last`() {
        val recUntimed = LedgerRecord(
            id = "u1", title = "Đọc ghi chú", category = TimeCategory.LEARN, date = "2026-11-17",
            startedAt = null, endedAt = null, durationSeconds = 900L, layer = TimeLayer.REPORTED_UNTIMED,
            status = "CONFIRMED", source = "manual", createdAt = 100L, userConfirmedAt = 100L,
            timezone = "Asia/Ho_Chi_Minh", idempotencyKey = "k_u1"
        )
        val recLate = LedgerRecord(
            id = "t2", title = "Chạy bộ chiều", category = TimeCategory.HEALTH, date = "2026-11-17",
            startedAt = 2_000_000L, endedAt = 2_600_000L, durationSeconds = 600L, layer = TimeLayer.REPORTED_TIMED,
            status = "CONFIRMED", source = "manual", createdAt = 50L, userConfirmedAt = 50L,
            timezone = "Asia/Ho_Chi_Minh", idempotencyKey = "k_t2"
        )
        val recEarly = LedgerRecord(
            id = "t1", title = "Code buổi sáng", category = TimeCategory.WORK, date = "2026-11-17",
            startedAt = 1_000_000L, endedAt = 1_900_000L, durationSeconds = 900L, layer = TimeLayer.MEASURED,
            status = "CONFIRMED", source = "timer", createdAt = 300L, userConfirmedAt = 300L,
            timezone = "Asia/Ho_Chi_Minh", idempotencyKey = "k_t1"
        )
        val sorted = sortTimelineDayRecordsChronological(listOf(recLate, recUntimed, recEarly))
        assertEquals(listOf("t1", "t2", "u1"), sorted.map { it.id })
    }

    @Test
    fun `computeDragTargetIndex shifts across card thresholds and clamps within group bounds`() {
        val heights = listOf(200f, 200f, 200f, 200f)
        // Kéo thẻ 0 xuống vượt qua thẻ 1 -> index 1
        assertEquals(1, computeDragTargetIndex(0, dragOffsetY = 160f, itemHeightsPx = heights, spacingPx = 10f, minIndex = 0, maxIndex = 2))
        // Kéo thẻ 0 xuống mạnh -> vẫn bị khóa ở maxIndex = 2 (cuối nhóm ưu tiên)
        assertEquals(2, computeDragTargetIndex(0, dragOffsetY = 800f, itemHeightsPx = heights, spacingPx = 10f, minIndex = 0, maxIndex = 2))
        // Kéo thẻ 2 lên -> về index 0
        assertEquals(0, computeDragTargetIndex(2, dragOffsetY = -500f, itemHeightsPx = heights, spacingPx = 10f, minIndex = 0, maxIndex = 2))
        assertTrue(computeDragTargetIndex(2, dragOffsetY = -20f, itemHeightsPx = heights, spacingPx = 10f, minIndex = 0, maxIndex = 2) == 2)
    }

    @Test
    fun `visibleChatComposerText hides sent in-flight text while keeping cancel and edited text visible`() {
        val raw = "Ngủ từ 8h tối hôm qua đến 8h sáng hôm nay, 9h làm việc đến 20h"
        assertEquals("", visibleChatComposerText(raw, isAnalyzing = true, suppressedInFlightRaw = raw))
        assertEquals("hủy", visibleChatComposerText("hủy", isAnalyzing = true, suppressedInFlightRaw = raw))
        assertEquals(raw, visibleChatComposerText(raw, isAnalyzing = false, suppressedInFlightRaw = raw))
        assertEquals("$raw thêm", visibleChatComposerText("$raw thêm", isAnalyzing = true, suppressedInFlightRaw = raw))
    }
}
