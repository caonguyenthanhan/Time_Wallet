package com.timewallet.mvp

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.13 F4 — hợp đồng nối dây của giao diện (kiểm tra TĨNH nguồn, cùng kiểu V1011UiPolishContractTest; hành vi thật: V1013DoubleTapCancelTest).
 * ViewModel/Compose không dựng được trên JVM, nên chỉ kiểm chứng rằng các điểm nối tồn tại và đúng chỗ.
 */
class V1013DoubleTapContractTest {

    private fun text(path: String) = File(path).readText(Charsets.UTF_8)
    private val chat get() = text("src/main/java/com/timewallet/mvp/ui/screens/ChatScreen.kt")

    @Test
    fun `both cancel buttons are disabled until the arming delay has passed`() {
        val c = chat
        // ô "đã lên đạn" bắt đầu lại ở mỗi lượt gửi và chỉ bật sau CancelArming.DEFAULT_ARM_MS
        assertTrue(c.contains("var cancelArmed by remember { mutableStateOf(false) }"))
        assertTrue(c.contains("LaunchedEffect(isAnalyzing)"))
        assertTrue(c.contains("delay(CancelArming.DEFAULT_ARM_MS)"))
        assertTrue(c.indexOf("cancelArmed = false") in 0 until c.indexOf("delay(CancelArming.DEFAULT_ARM_MS)"))
        // nút dừng đặt tại chỗ nút Gửi: hiện nhưng khóa trong cửa sổ
        val stop = c.substring(c.indexOf("contentDescription = \"Hủy yêu cầu đang gửi\"").let { c.lastIndexOf("Button(", it) }, c.indexOf("contentDescription = \"Hủy yêu cầu đang gửi\""))
        assertTrue("nút dừng có enabled = cancelArmed", stop.contains("onClick = onCancelAnalysis") && stop.contains("enabled = cancelArmed"))
        assertTrue("nút dừng khóa vẫn hiện (màu khóa riêng)", stop.contains("disabledContainerColor"))
        // nút "Hủy" trong bong bóng cũng khóa cùng cửa sổ (không có nút im lặng không phản hồi)
        assertTrue(c.contains("TextButton(onClick = onCancelAnalysis, enabled = cancelArmed"))
        assertEquals("chỉ hai nút Hủy phân tích trong màn Wally", 2, Regex("onClick = onCancelAnalysis").findAll(c).count())
    }

    @Test
    fun `the Send button keeps its own behaviour and the typed cancel command still works while analysing`() {
        val c = chat
        assertTrue(c.contains("enabled = !isAnalyzing || GuardHealth.isCancelCommand(chatInputText)"))
        assertTrue(c.contains("if (isAnalyzing && !GuardHealth.isCancelCommand(chatInputText))"))
    }

    @Test
    fun `the ViewModel hands the runner the real arming delay and the runner gates cancel before touching the owner`() {
        val vm = text("src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt")
        assertTrue(vm.contains("cancelArming = CancelArming()"))
        val runner = text("src/main/java/com/timewallet/mvp/ui/WallyChatRunner.kt")
        val cancelFn = runner.substring(runner.indexOf("fun cancel(): Boolean {"), runner.indexOf("fun cancelStaleJob()"))
        assertTrue(cancelFn.indexOf("cancelArming.isArmed()") in 0 until cancelFn.indexOf("owner.cancel()"))
        // chỉ lượt được nhận mới đặt lại cửa sổ (lần bấm trùng return false trước đó)
        val submitFn = runner.substring(runner.indexOf("fun submit("), runner.indexOf("fun cancel(): Boolean {"))
        assertTrue(submitFn.indexOf("owner.tryBegin() ?: return false") in 0 until submitFn.indexOf("cancelArming.onSendStarted()"))
    }

    @Test
    fun `closing the AI settings dialog cancels a running connection test so a late result cannot leak into the next session`() {
        val vm = text("src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt")
        assertTrue(vm.contains("aiTestJob = viewModelScope.launch(safeHandler)"))
        val toggle = vm.substring(vm.indexOf("fun toggleAiSettingsDialog("), vm.indexOf("private fun aiDiagnosticsReport()"))
        assertTrue(toggle.contains("aiTestJob?.cancel()"))
    }
}
