package com.timewallet.mvp

import com.timewallet.mvp.ui.screens.cappedSpValue
import com.timewallet.mvp.ui.screens.compactBarLabel
import com.timewallet.mvp.ui.screens.reflectionQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.0.11 — các hàm thuần của lớp trình bày (kiểm tra hành vi thật, không chỉ so khớp chuỗi nguồn). */
class V1011UiLogicTest {

    @Test
    fun `bar labels stay short enough for a narrow column and never lie about zero`() {
        assertEquals("–", compactBarLabel(0L))
        assertEquals("–", compactBarLabel(-5L))
        assertEquals("1p", compactBarLabel(1L))          // dưới 1 phút vẫn không hiện 0
        assertEquals("45p", compactBarLabel(45 * 60L))
        assertEquals("2h", compactBarLabel(2 * 3600L))
        assertEquals("2h15", compactBarLabel(2 * 3600L + 15 * 60L))
        assertEquals("9h59", compactBarLabel(9 * 3600L + 59 * 60L))
        assertEquals("2h05", compactBarLabel(2 * 3600L + 5 * 60L))   // phút luôn 2 chữ số: không bao giờ "2h5" (dễ đọc nhầm 2h50)
        assertEquals("1h01", compactBarLabel(3600L + 60L))
        // làm tròn đến phút gần nhất như formatHoursMinutesFromSeconds (không cắt bớt)
        assertEquals("2h05", compactBarLabel(2 * 3600L + 4 * 60L + 45L))
        assertEquals("1p", compactBarLabel(29L))
        assertEquals("1p", compactBarLabel(59L))
        assertEquals("10h", compactBarLabel(9 * 3600L + 59 * 60L + 40L))   // làm tròn lên qua mốc 10 giờ thì bỏ phút
        assertEquals("10h", compactBarLabel(10 * 3600L + 30 * 60L)) // từ 10 giờ bỏ phút để vừa cột hẹp
        // không nhãn nào dài quá 4 ký tự với dữ liệu trong ngày (<= 24h)
        (0..24 * 60 step 7).forEach { assertTrue("${it}p", compactBarLabel(it * 60L).length <= 4) }
    }

    @Test
    fun `the reflection hint shows only the question when collapsed and falls back to the full text`() {
        val full = "Đối chiếu ngân sách thực tế của bạn: Công việc (5h/tuần): đã ghi nhận 1h, còn 4h. Gợi ý phản tư: Mức phân bổ hiện tại đã sát chưa?"
        assertEquals("Mức phân bổ hiện tại đã sát chưa?", reflectionQuestion(full))
        val noMarker = "Chưa có ngân sách nào để đối chiếu."
        assertEquals(noMarker, reflectionQuestion(noMarker))
        // chưa đặt ngân sách: chỉ giữ câu cuối, không lặp số liệu rồi cắt giữa câu
        val noBudget = "Trong 7 ngày qua, bạn đã ghi nhận 2h 15 thực tế có mốc giờ và 0h 00 chưa gắn giờ (tách riêng). Bạn có thể tự đặt Ngân sách thời gian theo danh mục nếu muốn đối chiếu mục tiêu cá nhân."
        assertEquals("Bạn có thể tự đặt Ngân sách thời gian theo danh mục nếu muốn đối chiếu mục tiêu cá nhân.", reflectionQuestion(noBudget))
        assertEquals("", reflectionQuestion(""))
        // dấu hiệu có mà không có câu hỏi phía sau -> dùng nguyên văn, không trả chuỗi rỗng
        assertEquals("Gợi ý phản tư:", reflectionQuestion("Gợi ý phản tư:"))
    }

    @Test
    fun `capped font scaling grows small labels with the system font but never beyond the cap`() {
        fun rendered(base: Float, fs: Float, cap: Float) = cappedSpValue(base, fs, cap) * fs   // cỡ hiển thị thật = sp * fontScale
        assertEquals(11f, rendered(11f, 1.0f, 1.15f), 0.001f)
        assertEquals(11f * 1.1f, rendered(11f, 1.1f, 1.15f), 0.001f)   // dưới ngưỡng: theo cỡ chữ hệ thống
        assertEquals(11f * 1.15f, rendered(11f, 1.3f, 1.15f), 0.001f)  // vượt ngưỡng: dừng ở 1.15x
        assertEquals(11f * 1.1f, rendered(11f, 2.0f, 1.1f), 0.001f)    // nhãn tab dùng ngưỡng 1.1
        assertEquals(12f, cappedSpValue(12f, 0f), 0.001f)              // dữ liệu lỗi không gây chia cho 0
        // nhỏ hơn 1.0 thì giữ đúng tỷ lệ người dùng chọn
        assertEquals(10f * 0.85f, rendered(10f, 0.85f, 1.15f), 0.001f)
    }
}
