package com.timewallet.mvp

import com.timewallet.mvp.domain.usecase.WallyGreeting
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.11 UI POLISH — hợp đồng cấu trúc của giao diện mới (kiểm tra TĨNH nguồn; hiển thị thật được kiểm bằng ảnh chụp trên AVD).
 * Không thay đổi nghiệp vụ: các bất biến cũ (AI chỉ đề xuất, xác nhận mới ghi, hủy khi đang phân tích...) vẫn do các bộ test cũ giữ.
 */
class V1011UiPolishContractTest {

    private val ui = "src/main/java/com/timewallet/mvp/ui/screens/"
    private fun src(name: String) = File(ui + name).readText(Charsets.UTF_8)
    private fun main() = File("src/main/java/com/timewallet/mvp/MainActivity.kt").readText(Charsets.UTF_8)

    @Test
    fun `Wally has a send button, not the old Tach de xuat form button, and the cancel path stays reachable`() {
        val chat = src("ChatScreen.kt")
        assertFalse("nút cũ phải biến mất", chat.contains("Tách đề xuất"))
        assertTrue(chat.contains("contentDescription = \"Gửi cho Wally\""))
        // đường hủy khi đang phân tích vẫn nằm trong giao diện (nút dừng và nút Hủy trong bong bóng)
        assertTrue(chat.contains("onClick = onCancelAnalysis"))
        assertTrue(chat.contains("enabled = !isAnalyzing || GuardHealth.isCancelCommand(chatInputText)"))
        assertFalse(chat.contains("CancelCommandParser"))
    }

    @Test
    fun `Wally is a conversation with a pinned composer outside the shared scroll column`() {
        val m = main()
        assertTrue(m.contains("val lockOuterScroll = false"))
        assertTrue(m.contains("WallyAiOverlay"))
        assertTrue(m.contains("if (lockOuterScroll) Modifier else Modifier.verticalScroll(scrollState)"))
        // hội thoại tự cuộn bên trong và có ô nhập ghim dưới
        val chat = src("ChatScreen.kt")
        assertTrue(chat.contains(".verticalScroll(scroll)"))
        assertTrue(chat.contains("OutlinedTextField("))
        // khi gõ ở Wally, thanh tab nhường chỗ. v1.0.12 (P1-NAV): điều kiện này từng dựa vào `WindowInsets.isImeVisible`, cờ luôn `true`
        // trên Android 9 (không có bàn phím, ime inset = 0) nên Wally mất thanh tab; nay chỉ tin ime inset THẬT ở đáy (test hành vi: V1012NavigationContractTest).
        val code = m.lines().filterNot { it.trim().startsWith("//") }
        assertTrue(code.none { it.contains("isImeVisible") })
        assertTrue(m.contains("WindowInsets.ime.getBottom(LocalDensity.current)") && m.contains("shouldShowBottomBar(state.currentTab, imeBottomPx)"))
    }

    @Test
    fun `AI proposals still require confirmation - the card keeps its own pending wording`() {
        val chat = src("ChatScreen.kt")
        assertTrue(chat.contains("hoạt động đề xuất · chờ bạn xác nhận"))
        assertTrue(chat.contains("Chưa lưu vào sổ cho tới khi bạn bấm Xác nhận."))
        // đường xác nhận phải được nối thật ở MainActivity (không chỉ có tên tham số)
        assertTrue(main().contains("onConfirmAllProposals = viewModel::confirmAllProposals"))
        assertTrue(main().contains("onConfirmSingleProposal = viewModel::confirmSingleProposal"))
    }

    @Test
    fun `data management moved out of the Journal into the top menu dialog`() {
        val history = src("HistoryScreen.kt")
        listOf("Nạp dữ liệu mẫu", "Xuất JSON", "Xóa toàn bộ dữ liệu", "Room SQLite", "Quản lý dữ liệu").forEach {
            assertFalse("Nhật ký không còn chứa \"$it\"", history.contains(it))
        }
        val dialog = src("DataManagementDialog.kt")
        listOf("Xuất JSON", "Nạp dữ liệu mẫu", "Xóa toàn bộ bản ghi", "Xóa toàn bộ bản ghi?", "Ngân sách đã đặt được giữ lại").forEach {
            assertTrue("hộp thoại dữ liệu thiếu \"$it\"", dialog.contains(it))
        }
        // xóa toàn bộ vẫn cần xác nhận riêng
        assertTrue(dialog.contains("confirmClear"))
        assertTrue(main().contains("DataManagementDialog("))
        assertTrue(src("ComponentsAndDialogs.kt").contains("Dữ liệu & sao lưu"))
    }

    @Test
    fun `Timer puts the clock and the start button before the optional dice`() {
        val t = src("TimerScreen.kt")
        val clock = t.indexOf("text = formattedClock")
        val start = t.indexOf("Bắt đầu đo")
        val diceCall = t.indexOf("DiceSection(")
        assertTrue(clock in 0 until start && start in 0 until diceCall)
        // xúc xắc mở qua BottomSheet thay vì chiếm diện tích chính; giải thích thuật toán nằm trong InfoToggle
        assertTrue(t.contains("var showDiceSheet by remember"))
        assertTrue(t.contains("ModalBottomSheet("))
        assertTrue(t.contains("InfoToggle(label = \"Cách xúc xắc hoạt động\")"))
        assertFalse("không còn thuật ngữ kỹ thuật trên màn hình chính", t.contains("(MEASURED)") || t.contains("RPE"))
    }

    @Test
    fun `main screens no longer contain the jargon and explainer strings that v1_0_10 showed`() {
        val banned = listOf("Room SQLite", "(MEASURED)", "RPE", "Quá khứ đã qua vs Quỹ còn lại", "Tách bạch hoàn toàn", "Không tự suy diễn giờ")
        listOf("OverviewScreen.kt", "TimerScreen.kt", "ChatScreen.kt", "HistoryScreen.kt", "InsightsScreen.kt").forEach { f ->
            val s = src(f)
            banned.forEach { b -> assertFalse("$f chứa \"$b\"", s.contains(b)) }
        }
    }

    @Test
    fun `charts are visual first - donut and bars exist and analytics shows charts before budgets`() {
        val ins = src("InsightsScreen.kt")
        assertTrue(ins.contains("DonutWithLegend("))
        val bars = ins.indexOf("Phân bổ 24 giờ · 7 ngày")
        val donut = ins.indexOf("Chi tiết Donut 7 ngày")
        val budget = ins.indexOf("private fun BudgetsManagerSheetContent(")
        assertTrue("biểu đồ trong Thống kê đứng trước hộp thoại Ngân sách ở Công cụ", bars in 0 until budget && donut in bars until budget)
        assertTrue("nhãn cột dùng cỡ chữ có giới hạn và nhãn ngắn", ins.contains("cappedSp(10.5f)") && ins.contains("compactBarLabel("))
        assertTrue(ins.contains("softWrap = false"))
        // thời lượng chưa gắn giờ vẫn tách riêng, không cộng gộp
        assertTrue(ins.contains("Chưa gắn giờ"))
    }

    @Test
    fun `journal is a timeline with short non wrapping filters and an empty state call to action`() {
        val h = src("HistoryScreen.kt")
        assertTrue(h.contains(".timelineRail("))
        assertFalse("không dùng IntrinsicSize (làm hụt chiều cao khi chữ xuống dòng)", h.contains("IntrinsicSize.Min)"))
        assertTrue(h.contains("horizontalScroll("))
        assertTrue(h.contains("softWrap = false"))
        assertTrue(h.contains("Ghi hoạt động đầu tiên"))
    }

    @Test
    fun `bottom bar and glyph controls share one icon set and keep accessible roles`() {
        val c = src("ComponentsAndDialogs.kt")
        assertTrue(c.contains("GlyphIcon(glyph = tab.glyph()"))
        assertTrue(c.contains("role = Role.Tab"))
        assertTrue(c.contains("contentDescription = \"Tab \${tab.label}\""))
        val kit = src("UiKit.kt")
        assertTrue(kit.contains("fontScale > 1.15f"))      // chú giải donut tự xuống dưới khi chữ lớn/màn hẹp
        assertTrue(kit.contains("fun Modifier.walletClickable("))
        assertTrue(kit.contains("minimumInteractiveComponentSize()"))
    }

    @Test
    fun `the greeting never says good evening in the small hours`() {
        assertTrue(WallyGreeting.dashboardLine(8).contains("buổi sáng"))
        assertTrue(WallyGreeting.dashboardLine(15).contains("buổi chiều"))
        assertTrue(WallyGreeting.dashboardLine(21).contains("buổi tối"))
        (0..4).forEach { assertFalse("giờ $it", WallyGreeting.dashboardLine(it).contains("buổi tối")) }
    }

    @Test
    fun `the sent message bubble lives in MainActivity so it survives tab switches and a cancel command never overwrites it`() {
        val chat = src("ChatScreen.kt")
        assertTrue(chat.contains("lastUserMessage: String?") && chat.contains("onLastUserMessageChange"))
        assertFalse("không còn trạng thái cục bộ mất khi đổi tab", chat.contains("var lastUserMessage by remember"))
        // cả nút gửi lẫn nút Thử lại đều cập nhật bong bóng bằng cùng một điều kiện, và lệnh hủy không ghi đè
        val guard = "if (chatInputText.isNotBlank() && !GuardHealth.isCancelCommand(chatInputText)) {"
        assertTrue("điều kiện phải có ở nút gửi và nút Thử lại", chat.split(guard).size - 1 >= 2)
        assertTrue(chat.split("onLastUserMessageChange(chatInputText.trim())").size - 1 >= 2)
        assertTrue(main().contains("var lastChatUserMessage by rememberSaveable"))
        assertTrue(chat.contains("LaunchedEffect(hasReply) { if (!hasReply) onLastUserMessageChange(null) }"))
    }

    @Test
    fun `toasts appear near the bottom navigation bar and never cover top headers or tabs`() {
        val m = main()
        assertTrue(m.contains(".align(Alignment.BottomCenter)") && m.contains("toastBottomPad"))
        assertFalse(m.contains(".align(Alignment.TopCenter)"))
    }

    @Test
    fun `the status chip shows no host name, the analytics hint is collapsed and the timer keeps the chosen category in view`() {
        assertFalse(src("ChatScreen.kt").contains("aiPreferences.trustedHost"))
        val ins = src("InsightsScreen.kt")
        assertTrue(ins.contains("weeklyEfficiency.actionableInsight") && ins.contains("EfficiencyInfoSheetContent"))
        assertFalse(ins.contains("Nhận xét cố định từ dữ liệu mô phỏng"))
        assertFalse(ins.contains("Đặt giờ trên lịch"))
        assertTrue(src("TimerScreen.kt").contains("requester.bringIntoView()"))
        // sau khi lắc, nút hành động được cuộn vào vùng nhìn; vòng đồng hồ thu nhỏ trên màn thấp/chữ lớn
        assertTrue(src("TimerScreen.kt").contains("buttonsRequester.bringIntoView()") && src("TimerScreen.kt").contains("compactOrb"))
        // chỉ cuộn khi lượt lắc vừa kết thúc trong lần hiển thị này (không cuộn khi quay lại tab)
        assertTrue(src("TimerScreen.kt").contains("var wasRolling by remember { mutableStateOf(false) }"))
        // Wally: trên màn thấp (xoay ngang) ẩn đầu trang/gợi ý; câu hỏi mới đóng câu trả lời cũ
        val chatSrc = src("ChatScreen.kt")
        assertTrue(chatSrc.contains("val compactHeight = LocalConfiguration.current.screenHeightDp < 480") && chatSrc.contains("if (!compactHeight) {"))
        assertTrue(chatSrc.split("if (chatAnswer != null) onDismissAnswer()").size - 1 >= 2)
    }

    @Test
    fun `labels that can be wider than their slot wrap instead of being clipped on narrow screens`() {
        val ov = src("OverviewScreen.kt")
        // tiêu đề hoạt động gần đây và phân bổ theo danh mục dùng Component chung thay cho QuickActionTile cũ; không ép softWrap=false lên nội dung dài
        assertTrue(ov.contains("Phân bổ theo danh mục") && ov.contains("Hoạt động gần đây"))
        // chip tóm tắt của thẻ đề xuất nằm trong FlowRow
        val chat = src("ChatScreen.kt")
        val card = chat.substring(chat.indexOf("private fun CompactProposalBatchCard("))
        assertTrue(card.indexOf("FlowRow(") in 0 until card.indexOf("Có giờ: "))
    }
}
