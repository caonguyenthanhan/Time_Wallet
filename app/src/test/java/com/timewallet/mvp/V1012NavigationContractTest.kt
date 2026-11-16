package com.timewallet.mvp

import com.timewallet.mvp.ui.AppTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.0.12 P1-NAV — Android 9 (LDPlayer) mất thanh tab khi mở Wally dù không có bàn phím.
 *
 * Bằng chứng đo trên APK chẩn đoán (WallyQA, Android 9, `out/wally_evidence_v1012/nav_probe_ldq_android9_v1011_diag.json`):
 *   tab=OVERVIEW isImeVisible=true imeBottomPx=0 navBottomPx=0 systemBarsBottomPx=0 showBar=true
 *   tab=CHAT     isImeVisible=true imeBottomPx=0 ... showBar=false      (và mInputShown=false: không có bàn phím)
 * `WindowInsets.isImeVisible` đứng yên ở `true` khi hệ thống không chuyển inset cho Compose; phần inset thật (ime bottom) là 0.
 * Quyết định ẩn thanh tab nay chỉ dựa trên inset thật.
 */
class V1012NavigationContractTest {

    @Test
    fun `without a real keyboard inset the tab bar is shown on every tab - the Android 9 case`() {
        AppTab.entries.forEach { assertTrue("${it.name} phải có thanh tab", shouldShowBottomBar(it, imeBottomPx = 0)) }
    }

    @Test
    fun `with a real keyboard the bar yields only on Wally`() {
        assertFalse(shouldShowBottomBar(AppTab.CHAT, imeBottomPx = 640))
        assertFalse("kể cả 1px: cờ chỉ dựa trên inset thật", shouldShowBottomBar(AppTab.CHAT, imeBottomPx = 1))
        listOf(AppTab.OVERVIEW, AppTab.TIMER, AppTab.HISTORY, AppTab.INSIGHTS).forEach {
            assertTrue("${it.name}: bàn phím không ẩn thanh tab", shouldShowBottomBar(it, imeBottomPx = 640))
        }
    }

    @Test
    fun `closing the keyboard brings the bar back`() {
        val sequence = listOf(0, 120, 640, 640, 300, 0).map { shouldShowBottomBar(AppTab.CHAT, it) }
        assertEquals(listOf(true, false, false, false, false, true), sequence)
    }

    @Test
    fun `MainActivity no longer reads the unreliable IME visibility flag`() {
        val m = File("src/main/java/com/timewallet/mvp/MainActivity.kt").readText()
        val offending = m.lines().map { it.trim() }
            .filterNot { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }
            .filter { it.contains("isImeVisible") }
        assertTrue("không dùng isImeVisible trong mã: $offending", offending.isEmpty())
        assertTrue(m.contains("WindowInsets.ime.getBottom(LocalDensity.current)"))
        assertTrue(m.contains("shouldShowBottomBar(state.currentTab, imeBottomPx)"))
    }
}
