package com.timewallet.mvp

import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.usecase.JournalDayTotal
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.ValidationResult
import com.timewallet.mvp.ui.AppTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * WALLY A13 (phần kiểm tra được trên JVM): tổng ngày của Nhật ký + kiểm tra TĨNH mã giao diện.
 * Đây KHÔNG thay thế kiểm tra hiển thị thật (320/360/390/412dp, font 1.3, bàn phím) — mục đó là NOT_TESTED.
 */
class WallyUiStaticTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val day = LocalDate.of(2026, 11, 13)
    private val nowMs = day.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

    private fun rec(cat: String, from: String?, to: String?, minutes: Int?, existing: List<LedgerRecord> = emptyList()): LedgerRecord {
        val r = if (from != null) LedgerValidator.parseTimeRangeToEpochMillis(day.toString(), from, to!!, zone) else null
        return (LedgerValidator.validateAndBuildRecord(
            source = "manual", rawTitle = "t", rawCategory = cat, dateLocal = day.toString(),
            inputDurationMinutes = minutes, startedAt = r?.first, endedAt = r?.second,
            existingRecords = existing, nowEpochMs = nowMs, zoneId = zone
        ) as ValidationResult.Valid).record
    }

    @Test
    fun `journal day total sums used records only and never planned time`() {
        val timed = rec("work", "09:00", "10:00", null)                       // 1h có giờ
        val untimed = rec("learn", null, null, 30, listOf(timed))              // 30p chưa gắn giờ
        val planned = rec("work", "20:00", "22:00", null, listOf(timed))       // dự kiến (sau 18:00)
        assertEquals(5400L, JournalDayTotal.seconds(listOf(timed, untimed, planned), nowMs))
        assertEquals(0L, JournalDayTotal.seconds(listOf(planned), nowMs))
        assertEquals(0L, JournalDayTotal.seconds(emptyList(), nowMs))
    }

    private fun src(name: String) = File("src/main/java/com/timewallet/mvp/ui/screens/$name").readText()

    @Test
    fun `app keeps exactly five tabs and the chat tab is now called Wally`() {
        assertEquals(5, AppTab.entries.size)
        assertEquals(listOf("Tổng quan", "Đồng hồ", "Wally", "Nhật ký", "Phân tích"), AppTab.entries.map { it.label })
    }

    @Test
    fun `dashboard order is hero then quick actions then donut then 24h axis`() {
        val s = src("OverviewScreen.kt")
        val order = listOf("VÍ THỜI GIAN HÔM NAY", "\"Hỏi Wally\"", "Phân bổ theo danh mục", "Trục 24 giờ hôm nay", "Hoạt động gần đây")
            .map { s.indexOf(it) }
        assertTrue("thiếu mục: $order", order.all { it >= 0 })
        assertEquals("thứ tự bố cục", order.sorted(), order)
        assertFalse("không còn khối CTA trùng lặp", s.contains("Ghi nhanh hoạt động"))
        assertFalse("không gọi phần chưa ghi nhận là thời gian rảnh", s.contains("rảnh", ignoreCase = true) && !s.contains("không phải giờ rảnh"))
    }

    @Test
    fun `primary actions have 48dp touch targets and accessibility labels`() {
        val components = src("ComponentsAndDialogs.kt")
        assertTrue(components.contains("this.contentDescription = contentDescription")) // nút thanh trên: nhãn thật sự được áp dụng
        assertTrue(components.contains("role = Role.Tab"))
        assertTrue(components.contains("heightIn(min = 48.dp)"))
        assertTrue(components.split("minimumInteractiveComponentSize()").size - 1 >= 8)
        val sheet = src("ManualEntrySheet.kt")
        // v1.1.0: chip dùng chung (ChoiceChip) nằm trong PersonalScreens.kt; form dùng nó cho mọi lựa chọn.
        assertTrue(sheet.contains("ChoiceChip(") && src("PersonalScreens.kt").contains("minimumInteractiveComponentSize()"))
        assertTrue(sheet.contains("imePadding()") && sheet.contains("navigationBarsPadding()"))
        assertTrue(File("src/main/java/com/timewallet/mvp/MainActivity.kt").readText().contains("contentDescription = \"Thêm giờ thủ công\""))
    }

    @Test
    fun `chart and clock text never wraps per character`() {
        val insights = src("InsightsScreen.kt")
        assertTrue(insights.contains("softWrap = false"))
        assertFalse("thanh biểu đồ không còn rộng cố định 28dp", insights.contains(".width(28.dp)"))
        assertTrue(src("TimerScreen.kt").contains("softWrap = false"))
    }

    @Test
    fun `donut is drawn only from real slices and the zero state is explicit`() {
        val s = src("OverviewScreen.kt")
        assertTrue(s.contains("summary.isEmpty"))
        assertTrue(s.contains("Chưa có giờ nào được ghi hôm nay."))
        assertTrue("legend không chỉ dựa vào màu: có emoji, tên, giờ và %", s.contains("slice.category.emoji") && s.contains("slice.percent"))
    }
}
