package com.timewallet.mvp

import com.timewallet.mvp.domain.model.BudgetPeriod
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.BudgetPolicy
import com.timewallet.mvp.domain.usecase.CoachActionKind
import com.timewallet.mvp.domain.usecase.CoachDigestBuilder
import com.timewallet.mvp.domain.usecase.CoachGuard
import com.timewallet.mvp.domain.usecase.CoachStructuredCheck
import com.timewallet.mvp.domain.usecase.ConfirmationActionResult
import com.timewallet.mvp.domain.usecase.ConfirmationEngine
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.TagRules
import com.timewallet.mvp.domain.usecase.ValidationResult
import com.timewallet.mvp.data.ai.GeminiProposalClient
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
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
        assertTrue(main.contains("onTriggerAction = viewModel::triggerCoachAction"))

        val personal = readSrc("ui/screens/PersonalScreens.kt")
        assertTrue(personal.contains("Wally AI Coach"))
        assertTrue(personal.contains("ActionItemCard("))
        assertTrue(personal.contains("◆ ACTION ITEMS"))
        assertTrue(chat.contains("◆ BƯỚC TIẾP THEO"))
        assertTrue(chat.contains("text = \"✦\"") && personal.contains("✦ WALLY AI"))

        // Linh vật robot Wally mới (toàn thân >=96dp qua ic_wally.webp + avatar 2D >=32dp qua ic_wally_avatar_2d.xml)
        assertTrue(File("src/main/res/drawable-nodpi/ic_wally.webp").exists())
        assertTrue(File("src/main/res/drawable/ic_wally_avatar_2d.xml").exists())
        assertTrue(!File("src/main/res/drawable/ic_wally.xml").exists())
        assertTrue(uikit.contains("fun WallyMascotAvatar(") && uikit.contains("R.drawable.ic_wally") && uikit.contains("R.drawable.ic_wally_avatar_2d"))
        assertTrue(overview.contains("WallyMascotAvatar("))
        assertTrue(personal.contains("WallyMascotAvatar("))
        assertTrue(chat.contains("WallyMascotAvatar("))

        // Style Guide v3 VectorDrawable, Adaptive + Monochrome Launcher & Metaphor Separation
        val res = File("src/main/res")
        listOf(
            "mipmap-anydpi-v26/ic_launcher.xml",
            "mipmap-anydpi-v26/ic_launcher_round.xml",
            "drawable/ic_launcher_wally_background.xml",
            "drawable/ic_launcher_wally_foreground.xml",
            "drawable/ic_launcher_wally_monochrome.xml",
            "drawable/ic_action_eraser_default.xml",
            "drawable/ic_action_delete_error.xml",
            "drawable/ic_warning_default.xml",
            "drawable/ic_error_default.xml",
            "drawable/ic_nav_timer_pressed.xml",
            "drawable/ic_action_notify_default.xml"
        ).forEach { assertTrue("Thiếu $it", File(res, it).exists()) }
        listOf("overview", "timer", "wally", "journal", "analytics").forEach { nav ->
            assertTrue("Thiếu nav $nav default", File(res, "drawable/ic_nav_${nav}_default.xml").exists())
            assertTrue("Thiếu nav $nav selected", File(res, "drawable/ic_nav_${nav}_selected.xml").exists())
        }
        listOf("work", "learn", "move", "home", "rest", "connect", "fun", "sleep", "other").forEach { cat ->
            assertTrue("Thiếu category $cat", File(res, "drawable/ic_cat_${cat}_default.xml").exists())
        }
        val manifest = File("src/main/AndroidManifest.xml").readText(Charsets.UTF_8)
        assertTrue(manifest.contains("@mipmap/ic_launcher") && manifest.contains("@mipmap/ic_launcher_round"))
        val adaptiveXml = File(res, "mipmap-anydpi-v26/ic_launcher.xml").readText(Charsets.UTF_8)
        assertTrue(adaptiveXml.contains("<monochrome"))
        assertTrue(uikit.contains("AppTab.CHAT -> WalletGlyph.WALLY_BOT") && uikit.contains("WalletGlyph.ERROR"))
        assertTrue(chat.contains("WalletGlyph.ERASER") && File("src/main/java/com/timewallet/mvp/ui/screens/ManualEntrySheet.kt").readText(Charsets.UTF_8).contains("WalletGlyph.ERASER"))
        assertTrue(File("src/main/java/com/timewallet/mvp/ui/screens/ComponentsAndDialogs.kt").readText(Charsets.UTF_8).contains("WalletGlyph.DELETE"))
        assertTrue(File("src/main/java/com/timewallet/mvp/notifications/CompletionReminder.kt").readText(Charsets.UTF_8).contains("R.drawable.ic_action_notify_default"))
        assertTrue(File("../docs/brand/TimeWallet_Wally_Icon_Style_Guide_v3.svg").exists() && File("../docs/brand/wally-hero.webp").exists())
        assertTrue(File(res, "drawable-nodpi/ic_wally.webp").exists())
        assertFalse("Không giữ bản Wally_Vector.svg giản lược nữa", File("../docs/brand/Wally_Vector.svg").exists())
    }

    @Test
    fun `checkStructured keeps concise CoachBrief style and verifies every field plus action items`() {
        val runTag = TagRules.newTag("Chạy bộ", TagRules.nameKey("Chạy bộ"), TimeCategory.HEALTH, 1L, "t_run")
        val rec = LedgerRecord(
            id = "r1", source = "manual", layer = TimeLayer.REPORTED_UNTIMED, createdAt = nowMs, userConfirmedAt = nowMs,
            category = TimeCategory.HEALTH, title = "Chạy bộ", date = today.toString(), durationSeconds = 35L * 60L,
            startedAt = null, endedAt = null, timezone = zone.id, status = "CONFIRMED", idempotencyKey = "k_r1", tagId = runTag.id
        )
        val digest = CoachDigestBuilder.build(listOf(rec), listOf(runTag), emptyList(), 7, nowMs, zone)

        val structured = CoachGuard.checkStructured(
            rawStory = "Chạy bộ đã ghi nhận 35 phút trong kỳ.",
            rawHeadline = "Nhịp chạy bộ hôm nay: 35 phút.",
            rawHighlights = listOf("Đã ghi nhận 35 phút Chạy bộ."),
            rawWatchout = "Có dữ liệu 1/7 ngày.",
            rawActions = listOf(
                mapOf("title" to "Lên lịch thêm một buổi chạy 35 phút nếu hợp lịch", "kind" to "PLAN", "target" to "Chạy bộ", "minutes" to 35),
                mapOf("title" to "Bị số lạ 999 phút không có trong dữ liệu", "kind" to "PLAN", "target" to "Chạy bộ", "minutes" to 999)
            ),
            digest = digest,
            tags = listOf(runTag)
        )
        assertTrue(structured is CoachStructuredCheck.Ok)
        val ok = structured as CoachStructuredCheck.Ok
        assertEquals("Nhịp chạy bộ hôm nay: 35 phút.", ok.brief.headline)
        assertEquals(1, ok.brief.highlights.size)
        assertEquals("Có dữ liệu 1/7 ngày.", ok.brief.watchout)
        // Action item thứ 2 có số 999 phút bị CoachGuard chặn; chỉ giữ action item hợp lệ thứ 1
        assertEquals(1, ok.brief.actionItems.size)
        assertEquals(CoachActionKind.PLAN, ok.brief.actionItems[0].kind)
        assertEquals("t_run", ok.brief.actionItems[0].targetTagId)
        assertEquals(35, ok.brief.actionItems[0].durationMinutes)

        // Nếu headline hoặc watchout chứa lời phán xét -> từ chối toàn bộ
        val rejected = CoachGuard.checkStructured(
            rawStory = "Chạy bộ đã ghi nhận 35 phút.",
            rawHeadline = "Bạn hơi lười trong 6 ngày còn lại.",
            digest = digest,
            tags = listOf(runTag)
        )
        assertTrue(rejected is CoachStructuredCheck.Rejected)

        // Prompt giữ đủ ràng buộc ngắn gọn & schema actionItems
        val prompt = GeminiProposalClient.coachSystemPrompt
        assertTrue(prompt.contains("actionItems") && prompt.contains("headline") && prompt.contains("highlights"))
    }

    @Test
    fun `requestCoach parses structured brief and actionItems from OpenAI-compatible payload`() {
        val coachJson = JSONObject()
            .put("headline", "Hôm nay đã ghi nhận 35 phút Chạy bộ.")
            .put("highlights", JSONArray().put("Hoàn thành 35 phút theo mục tiêu đã ghi."))
            .put("watchout", "Có dữ liệu 1/7 ngày trong kỳ.")
            .put(
                "actionItems",
                JSONArray().put(
                    JSONObject()
                        .put("title", "Đặt lịch thêm 35 phút Chạy bộ nếu phù hợp")
                        .put("kind", "PLAN")
                        .put("target", "Chạy bộ")
                        .put("minutes", 35)
                )
            )
            .put("suggestions", JSONArray())
            .toString()

        val httpBody = JSONObject()
            .put(
                "choices",
                JSONArray().put(
                    JSONObject().put(
                        "message",
                        JSONObject().put("role", "assistant").put("content", coachJson)
                    )
                )
            )
            .toString()

        val outcome = runBlocking {
            GeminiProposalClient.requestCoach(
                digestText = "Hôm nay đã ghi nhận 35 phút Chạy bộ.",
                optInEnabled = true,
                apiKey = "sk-test-synthetic-1234",
                connectionFactory = { url -> AndroidLikeConnection(url, status = 200, body = httpBody) }
            )
        }
        assertEquals(null, outcome.fault)
        assertEquals("Hôm nay đã ghi nhận 35 phút Chạy bộ.", outcome.headline)
        assertEquals(1, outcome.highlights.size)
        assertEquals("Có dữ liệu 1/7 ngày trong kỳ.", outcome.watchout)
        assertEquals(1, outcome.actionItems.size)
        assertTrue(!outcome.story.isNullOrBlank())
    }
}
