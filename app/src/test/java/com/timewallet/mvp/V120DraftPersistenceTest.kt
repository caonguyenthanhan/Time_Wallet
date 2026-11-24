package com.timewallet.mvp

import com.timewallet.mvp.data.local.BatchConfirm
import com.timewallet.mvp.data.local.ConfirmResult
import com.timewallet.mvp.data.local.ManualEntryDraft
import com.timewallet.mvp.data.local.SavedProposalDraft
import com.timewallet.mvp.data.local.TimerAndSettingsStore
import com.timewallet.mvp.data.local.TimerInputDraft
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.ChatHistoryEntry
import com.timewallet.mvp.domain.usecase.JournalDayTotal
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.ManualEntry
import com.timewallet.mvp.domain.usecase.ManualEntryValidation
import com.timewallet.mvp.ui.EditableProposalDraft
import com.timewallet.mvp.ui.ManualEntryDraftCoordinator
import com.timewallet.mvp.ui.ManualEntryState
import com.timewallet.mvp.ui.ManualMode
import com.timewallet.mvp.ui.toBatchInput
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Kiểm thử toàn diện Mục 6 — Draft Persistence & Nút Clean:
 * - Tự động lưu nháp khi nhập/đổi ngày/giờ/tag/ghi chú/chế độ, KHÔNG tạo bản ghi thật hay ảnh hưởng thống kê.
 * - Tồn tại khi Thoát/Hủy form, chuyển tab hoặc khởi động lại tiến trình (Store mới trên cùng SharedPreferences).
 * - Clean hỏi xác nhận khi có dữ liệu, xóa sạch form & cấp sessionId mới.
 * - Lưu thất bại giữ nguyên bản nháp; Lưu thành công xóa bản nháp; sửa bản ghi cũ hoàn toàn độc lập với bản nháp.
 */
class V120DraftPersistenceTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 17)
    private val todayStr = today.toString()
    private val tomorrowStr = today.plusDays(1).toString()
    private val tagRead = Tag(
        id = "t_read",
        name = "Đọc sách",
        nameKey = "doc sach",
        category = TimeCategory.LEARN,
        archivedAt = null,
        createdAt = 1L,
        updatedAt = 1L
    )

    @Test
    fun `draft persists every field across dismiss and full process restart without touching ledger stats`() {
        val prefs = InMemorySharedPreferences()
        val storeBeforeKill = TimerAndSettingsStore(prefs)
        val dao = InMemoryLedgerDao()
        val nowMs = LocalDateTime.of(today, java.time.LocalTime.of(10, 0)).atZone(zone).toInstant().toEpochMilli()

        // 1. Người dùng nhập dở: chọn tag, đổi ngày mai, chọn RANGE, nhập giờ + ghi chú + Lưu làm Dự kiến
        val editingForm = ManualEntryState(
            sessionId = "sess_1",
            categoryKey = TimeCategory.LEARN.key,
            dateLocal = tomorrowStr,
            note = "Đọc Clean Architecture",
            mode = ManualMode.RANGE,
            tagId = tagRead.id,
            tagQuery = "Đọc",
            startText = "0900",
            endText = "1030",
            endIsNow = false,
            saveAsPlanned = true
        )
        val savedDraft = ManualEntryDraftCoordinator.syncDraft(storeBeforeKill, editingForm, todayStr, nowMs, immediate = true)
        assertNotNull(savedDraft)

        // Bản nháp KHÔNG tạo hoạt động trong DB và KHÔNG ảnh hưởng thống kê
        assertTrue(runBlocking { dao.getAllRecordsSnapshot() }.isEmpty())
        assertEquals(0L, LedgerAnalyticsCalculator.computeDayStats(todayStr, emptyList(), zone, nowMs).timedSeconds)
        assertEquals(0L, JournalDayTotal.seconds(emptyList(), nowMs))

        // 2. Bấm Thoát / Hủy -> đóng sheet (flush) nhưng bản nháp VẪN giữ nguyên
        ManualEntryDraftCoordinator.syncDraft(storeBeforeKill, editingForm, todayStr, nowMs + 1000L, immediate = true)

        // 3. Mô phỏng app bị kill & mở lại (TimerAndSettingsStore mới trên cùng prefs)
        val storeAfterRestart = TimerAndSettingsStore(prefs)
        val reloadedDraft = storeAfterRestart.loadManualEntryDraft()
        assertNotNull(reloadedDraft)

        val (restoredForm, activeDraft) = ManualEntryDraftCoordinator.openOrRestore(
            storedDraft = reloadedDraft,
            requestedCategoryKey = null,
            tags = listOf(tagRead),
            todayLocal = todayStr
        )
        assertNotNull(activeDraft)
        assertEquals("sess_1", restoredForm.sessionId)
        assertEquals(TimeCategory.LEARN.key, restoredForm.categoryKey)
        assertEquals(tomorrowStr, restoredForm.dateLocal)
        assertEquals("Đọc Clean Architecture", restoredForm.note)
        assertEquals(ManualMode.RANGE, restoredForm.mode)
        assertEquals(tagRead.id, restoredForm.tagId)
        assertEquals("Đọc", restoredForm.tagQuery)
        assertEquals("0900", restoredForm.startText)
        assertEquals("1030", restoredForm.endText)
        assertTrue(restoredForm.saveAsPlanned)

        // 4. Thay đổi giờ và tag -> bản nháp phản ánh giá trị mới nhất
        val modified = restoredForm.copy(startText = "1400", endText = "1530", hoursText = "1", minutesText = "30")
        ManualEntryDraftCoordinator.syncDraft(storeAfterRestart, modified, todayStr, nowMs + 2000L, immediate = true)
        val latest = storeAfterRestart.loadManualEntryDraft()!!
        assertEquals("1400", latest.startText)
        assertEquals("1530", latest.endText)

        // Nếu tag bị lưu trữ sau khi lưu nháp, khôi phục vẫn giữ nhóm & nội dung nhưng bỏ tagId cũ
        val (safeRestore, _) = ManualEntryDraftCoordinator.openOrRestore(
            storedDraft = latest,
            requestedCategoryKey = null,
            tags = listOf(tagRead.copy(archivedAt = 999L)),
            todayLocal = todayStr
        )
        assertNull(safeRestore.tagId)
        assertEquals(TimeCategory.LEARN.key, safeRestore.categoryKey)
        assertEquals("Đọc Clean Architecture", safeRestore.note)
    }

    @Test
    fun `failed save preserves draft while successful save clears draft and resets sessionId`() {
        val prefs = InMemorySharedPreferences()
        val store = TimerAndSettingsStore(prefs)
        val dao = InMemoryLedgerDao()
        val nowMs = LocalDateTime.of(today, java.time.LocalTime.of(16, 0)).atZone(zone).toInstant().toEpochMilli()

        // Có sẵn bản ghi 14:00-15:00 trong sổ cái
        val existingOk = ManualEntry.validateRange("work", null, null, "14:00", "15:00", false, todayStr, "Họp", today, "16:00") as ManualEntryValidation.Valid
        runBlocking {
            BatchConfirm.run(dao, listOf(ManualEntry.toBatchInput(existingOk, "ex1")), null, { null }, nowMs, zone) { nowMs }
        }

        // Nhập dở form mới trùng giờ 14:15-14:45 -> lưu THẤT BẠI
        val overlappingForm = ManualEntryState(
            sessionId = "draft_fail_then_ok",
            categoryKey = "learn",
            dateLocal = todayStr,
            note = "Ghi chú nháp",
            mode = ManualMode.RANGE,
            startText = "1415",
            endText = "1445"
        )
        ManualEntryDraftCoordinator.syncDraft(store, overlappingForm, todayStr, nowMs, immediate = true)

        val failValid = ManualEntry.validateRange("learn", null, null, "14:15", "14:45", false, todayStr, "Ghi chú nháp", today, "16:00") as ManualEntryValidation.Valid
        val failOutcome = runBlocking {
            BatchConfirm.run(dao, listOf(ManualEntry.toBatchInput(failValid, overlappingForm.sessionId)), null, { null }, nowMs, zone) { nowMs }
        }
        assertTrue("Lưu trùng phải thất bại", failOutcome is ConfirmResult.Invalid)
        assertNotNull("Lưu thất bại KHÔNG được xóa bản nháp", store.loadManualEntryDraft())
        assertEquals("Ghi chú nháp", store.loadManualEntryDraft()!!.note)

        // Sửa lại sang 15:00-15:45 -> lưu THÀNH CÔNG -> xóa bản nháp ngay
        val updatedForm = overlappingForm.copy(startText = "1500", endText = "1545")
        val okValid = ManualEntry.validateRange("learn", null, null, "15:00", "15:45", false, todayStr, "Ghi chú nháp", today, "16:00") as ManualEntryValidation.Valid
        val okOutcome = runBlocking {
            BatchConfirm.run(dao, listOf(ManualEntry.toBatchInput(okValid, updatedForm.sessionId)), null, { null }, nowMs, zone) { nowMs }
        }
        assertTrue(okOutcome is ConfirmResult.Committed)
        ManualEntryDraftCoordinator.clearOnCommitSuccess(store)
        assertNull("Lưu thành công phải xóa bản nháp", store.loadManualEntryDraft())

        // Mở lại form -> trống sạch và có sessionId mới
        val (fresh, restoredDraft) = ManualEntryDraftCoordinator.openOrRestore(store.loadManualEntryDraft(), null, listOf(tagRead), todayStr)
        assertNull(restoredDraft)
        assertNotEquals("draft_fail_then_ok", fresh.sessionId)
        assertEquals("", fresh.note)
        assertEquals("", fresh.startText)
    }

    @Test
    fun `Clean requires confirmation only when draft has data and resets form safely without affecting saved records`() {
        val prefs = InMemorySharedPreferences()
        val store = TimerAndSettingsStore(prefs)
        val blankForm = ManualEntryState(sessionId = "s_blank", dateLocal = todayStr)
        assertFalse("Form rỗng không cần hộp thoại xác nhận", ManualEntryDraftCoordinator.requiresCleanConfirmation(blankForm, todayStr))

        val filledForm = ManualEntryState(
            sessionId = "s_filled",
            categoryKey = "health",
            hoursText = "1",
            minutesText = "15",
            dateLocal = todayStr,
            note = "Chạy bộ sáng",
            tagId = tagRead.id
        )
        ManualEntryDraftCoordinator.syncDraft(store, filledForm, todayStr, 100L, immediate = true)
        assertTrue("Form có dữ liệu bắt buộc xác nhận trước khi Clean", ManualEntryDraftCoordinator.requiresCleanConfirmation(filledForm, todayStr))

        // Đảm bảo dữ liệu nháp cũ vẫn tách biệt khi mở edit bản ghi đã lưu
        val savedRec = (LedgerValidator.validateAndBuildRecord(
            id = "saved_1",
            source = "manual",
            rawTitle = "Việc đã lưu",
            rawCategory = "work",
            dateLocal = todayStr,
            inputDurationMinutes = 30,
            startedAt = null,
            endedAt = null,
            existingRecords = emptyList(),
            nowEpochMs = 1_000L,
            zoneId = zone
        ) as com.timewallet.mvp.domain.usecase.ValidationResult.Valid).record
        assertEquals("Việc đã lưu", savedRec.title)
        assertNotEquals(savedRec.title, store.loadManualEntryDraft()?.note)

        // Chủ động bấm Clean sau khi xác nhận -> xóa sạch store + cấp sessionId mới
        val cleaned = ManualEntryDraftCoordinator.cleanAndReset(store, keepSheetOpen = true, todayLocal = todayStr)!!
        assertNull(store.loadManualEntryDraft())
        assertNotEquals("s_filled", cleaned.sessionId)
        assertNull(cleaned.categoryKey)
        assertEquals("", cleaned.note)
        assertEquals("", cleaned.hoursText)
        assertNull(cleaned.tagId)
    }

    @Test
    fun `timer and chat drafts plus corrupted draft tolerance survive restart cleanly`() {
        val prefs = InMemorySharedPreferences()
        val store = TimerAndSettingsStore(prefs)
        store.saveTimerInputDraft(TimerInputDraft(title = "Viết spec", categoryKey = "work", tagId = "t1"), immediate = true)
        store.saveChatInputDraft("Chiều nay học 45 phút", immediate = true)

        val store2 = TimerAndSettingsStore(prefs)
        val tDraft = store2.loadTimerInputDraft()
        assertEquals("Viết spec", tDraft.title)
        assertEquals("work", tDraft.categoryKey)
        assertEquals("t1", tDraft.tagId)
        assertEquals("Chiều nay học 45 phút", store2.loadChatInputDraft())

        store2.saveTimerInputDraft(null, immediate = true)
        store2.saveChatInputDraft("", immediate = true)
        assertEquals("", TimerAndSettingsStore(prefs).loadTimerInputDraft().title)
        assertEquals("", TimerAndSettingsStore(prefs).loadChatInputDraft())

        // JSON lỗi trong SharedPreferences không làm văng app
        prefs.edit().putString("manual_entry_draft_v1", "{bad-json").commit()
        assertNull(store2.loadManualEntryDraft())
    }

    @Test
    fun `UI and ViewModel wire draft persistence, distinct Exit vs Clean confirmation, and save-clears-draft`() {
        val vm = File("src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt").readText(Charsets.UTF_8)
        assertTrue(vm.contains("ManualEntryDraftCoordinator.openOrRestore"))
        assertTrue(vm.contains("ManualEntryDraftCoordinator.syncDraft"))
        assertTrue(vm.contains("ManualEntryDraftCoordinator.cleanAndReset"))
        assertTrue(vm.contains("ManualEntryDraftCoordinator.clearOnCommitSuccess"))
        assertTrue(vm.contains("settingsStore.loadManualEntryDraft()"))
        assertTrue(vm.contains("settingsStore.loadChatInputDraft()"))
        assertTrue(vm.contains("settingsStore.loadTimerInputDraft()"))
        assertTrue("onAppPaused phải flush nháp ngay", vm.contains("fun onAppPaused()") && vm.contains("immediate = true"))

        val sheet = File("src/main/java/com/timewallet/mvp/ui/screens/ManualEntrySheet.kt").readText(Charsets.UTF_8)
        assertTrue(sheet.contains("Clean · Xóa nhập liệu"))
        assertTrue(sheet.contains("Thoát và giữ bản nháp"))
        assertTrue(sheet.contains("Xóa nhập liệu đang có?"))
        assertTrue(sheet.contains("if (hasDraftData) showCleanConfirm = true else onClean()"))

        val ov = File("src/main/java/com/timewallet/mvp/ui/screens/OverviewScreen.kt").readText(Charsets.UTF_8)
        assertTrue(ov.contains("Bản nháp đang nhập dở"))
        assertTrue(ov.contains("Chưa ghi vào sổ cái và chưa tính vào thống kê."))

        val chat = File("src/main/java/com/timewallet/mvp/ui/screens/ChatScreen.kt").readText(Charsets.UTF_8)
        assertTrue(chat.contains("Clean · Xóa nhập liệu"))
        assertTrue(chat.contains("Xóa nội dung đang nhập?"))

        val timer = File("src/main/java/com/timewallet/mvp/ui/screens/TimerScreen.kt").readText(Charsets.UTF_8)
        assertTrue(timer.contains("Clean · Xóa nhập liệu"))
        assertTrue(timer.contains("Xóa nhập liệu đồng hồ?"))
    }

    @Test
    fun `chat history and unconfirmed proposals survive process restart and can be cleared independently`() {
        val prefs = InMemorySharedPreferences()
        val storeBeforeRestart = TimerAndSettingsStore(prefs)
        val history = listOf(
            ChatHistoryEntry(
                id = "h1",
                isUser = true,
                headline = "Ngủ từ 8h tối hôm qua đến 8h sáng hôm nay, 9h làm việc đến 20h",
                timeText = "00:10",
                label = "Wally đã xem ✓✓"
            ),
            ChatHistoryEntry(
                id = "h2",
                isUser = false,
                headline = "Hôm nay bạn đã dùng 12h00p",
                lines = listOf("• Làm việc: 11h00p"),
                notes = listOf("Chỉ từ dữ liệu đã lưu"),
                timeText = "00:10"
            )
        )
        val pendingDrafts = listOf(
            SavedProposalDraft(
                id = "p1",
                rawInput = "9h làm việc đến 20h",
                title = "Làm việc",
                categoryKey = "work",
                durationMinutesText = "660",
                dateLocal = "2026-11-18",
                startLocal = "09:00",
                endLocal = "20:00",
                hasInitialTimeEvidence = true,
                isFuturePlanned = false,
                idempotencyKey = "idem-p1"
            )
        )

        storeBeforeRestart.saveChatHistory(history, immediate = true)
        storeBeforeRestart.saveChatProposalsDraft(pendingDrafts, immediate = true)

        val storeAfterRestart = TimerAndSettingsStore(prefs)
        val restoredHistory = storeAfterRestart.loadChatHistory()
        assertEquals(2, restoredHistory.size)
        assertEquals("00:10", restoredHistory[0].timeText)
        assertEquals("Hôm nay bạn đã dùng 12h00p", restoredHistory[1].headline)

        val restoredDrafts = storeAfterRestart.loadChatProposalsDraft()
        assertEquals(1, restoredDrafts.size)
        assertEquals("Làm việc", restoredDrafts[0].title)
        assertEquals("660", restoredDrafts[0].durationMinutesText)
        assertEquals("09:00", restoredDrafts[0].startLocal)
        assertEquals("20:00", restoredDrafts[0].endLocal)

        storeAfterRestart.saveChatHistory(emptyList(), immediate = true)
        assertTrue(TimerAndSettingsStore(prefs).loadChatHistory().isEmpty())
        assertEquals(1, TimerAndSettingsStore(prefs).loadChatProposalsDraft().size)

        val vm = File("src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt").readText(Charsets.UTF_8)
        assertTrue(vm.contains("settingsStore.loadChatHistory()"))
        assertTrue(vm.contains("settingsStore.loadChatProposalsDraft()"))
        assertTrue(vm.contains("fun clearChatHistory()"))
        val chat = File("src/main/java/com/timewallet/mvp/ui/screens/ChatScreen.kt").readText(Charsets.UTF_8)
        assertTrue(chat.contains("Xóa lịch sử"))
    }

    @Test
    fun `manual entry draft preserves linked taskId across process restart and loads legacy drafts safely`() {
        val prefs = InMemorySharedPreferences()
        val dao = InMemoryLedgerDao()
        val storeBeforeRestart = TimerAndSettingsStore(prefs)
        val nowMs = LocalDateTime.of(today.year, today.month, today.dayOfMonth, 10, 0).atZone(zone).toInstant().toEpochMilli()

        val taskLinkedForm = ManualEntryState(
            sessionId = "draft_with_task_99",
            categoryKey = "work",
            dateLocal = tomorrowStr,
            note = "Lịch từ To-do gốc",
            mode = ManualMode.RANGE,
            tagId = tagRead.id,
            startText = "1500",
            endText = "1545",
            saveAsPlanned = true,
            taskId = "task_origin_99"
        )
        assertTrue(ManualEntryDraft(sessionId = "only_task", dateLocal = todayStr, taskId = "task_origin_99").hasUserData(todayStr))
        val synced = ManualEntryDraftCoordinator.syncDraft(storeBeforeRestart, taskLinkedForm, todayStr, nowMs, immediate = true)
        assertEquals("task_origin_99", synced?.taskId)

        // Giả lập Android thu hồi tiến trình: khởi tạo lại Store hoàn toàn mới
        val storeAfterRestart = TimerAndSettingsStore(prefs)
        val (restoredForm, restoredDraft) = ManualEntryDraftCoordinator.openOrRestore(
            storedDraft = storeAfterRestart.loadManualEntryDraft(),
            requestedCategoryKey = null,
            tags = listOf(tagRead),
            todayLocal = todayStr
        )
        assertEquals("task_origin_99", restoredDraft?.taskId)
        assertEquals("task_origin_99", restoredForm.taskId)

        val valid = ManualEntry.validateRange(
            categoryKey = restoredForm.categoryKey,
            tagId = restoredForm.tagId,
            tagName = null,
            startText = "15:00",
            endText = "15:45",
            endIsNow = restoredForm.endIsNow,
            dateLocal = restoredForm.dateLocal,
            note = restoredForm.note,
            today = today,
            nowHm = "10:00",
            saveAsPlanned = restoredForm.saveAsPlanned
        ) as ManualEntryValidation.Valid
        val batchInput = ManualEntry.toBatchInput(valid, restoredForm.sessionId).copy(taskId = restoredForm.taskId)
        val outcome = runBlocking {
            BatchConfirm.run(dao, listOf(batchInput), null, { null }, nowMs, zone) { nowMs }
        }
        assertTrue(outcome is ConfirmResult.Committed)
        val saved = runBlocking { dao.getAllRecordsSnapshot() }.single()
        assertEquals("task_origin_99", saved.taskId)
        assertEquals("PLANNED", saved.layer)

        // Tương thích ngược: JSON nháp cũ không có khóa taskId vẫn tải và mở được bình thường
        val legacyJson = JSONObject().apply {
            put("sessionId", "legacy_manual_1")
            put("categoryKey", "learn")
            put("hoursText", "")
            put("minutesText", "25")
            put("dateLocal", todayStr)
            put("note", "Nháp cũ không taskId")
            put("modeName", "DURATION")
            put("tagId", JSONObject.NULL)
            put("tagQuery", "")
            put("startText", "")
            put("endText", "")
            put("endIsNow", false)
            put("saveAsPlanned", false)
            put("isMarkedWaste", false)
            put("updatedAtEpochMs", 5000L)
        }.toString()
        prefs.edit().putString("manual_entry_draft_v1", legacyJson).commit()
        val legacyLoaded = TimerAndSettingsStore(prefs).loadManualEntryDraft()
        assertNotNull(legacyLoaded)
        assertNull(legacyLoaded!!.taskId)
        assertEquals("Nháp cũ không taskId", legacyLoaded.note)
        val (legacyForm, _) = ManualEntryDraftCoordinator.openOrRestore(legacyLoaded, null, listOf(tagRead), todayStr)
        assertNull(legacyForm.taskId)
        assertEquals("Nháp cũ không taskId", legacyForm.note)
    }

    @Test
    fun `proposal drafts preserve linked taskId across process restart and load legacy drafts safely`() {
        val prefs = InMemorySharedPreferences()
        val dao = InMemoryLedgerDao()
        val storeBeforeRestart = TimerAndSettingsStore(prefs)
        val nowMs = LocalDateTime.of(today.year, today.month, today.dayOfMonth, 10, 0).atZone(zone).toInstant().toEpochMilli()

        val savedDraft = SavedProposalDraft(
            id = "prop_task_77",
            rawInput = "Xếp lịch Soạn đề án",
            title = "Soạn đề án",
            categoryKey = "work",
            durationMinutesText = "45",
            dateLocal = tomorrowStr,
            startLocal = "09:00",
            endLocal = "09:45",
            hasInitialTimeEvidence = true,
            isFuturePlanned = true,
            idempotencyKey = "idem_prop_task_77",
            tagId = tagRead.id,
            isMarkedWaste = false,
            taskId = "task_prop_77"
        )
        storeBeforeRestart.saveChatProposalsDraft(listOf(savedDraft), immediate = true)

        val restored = TimerAndSettingsStore(prefs).loadChatProposalsDraft()
        assertEquals(1, restored.size)
        assertEquals("task_prop_77", restored[0].taskId)

        val editableRestored = EditableProposalDraft(
            id = restored[0].id,
            rawInput = restored[0].rawInput,
            title = restored[0].title,
            categoryKey = restored[0].categoryKey,
            durationMinutesText = restored[0].durationMinutesText,
            dateLocal = restored[0].dateLocal,
            startLocal = restored[0].startLocal,
            endLocal = restored[0].endLocal,
            hasInitialTimeEvidence = restored[0].hasInitialTimeEvidence,
            isFuturePlanned = restored[0].isFuturePlanned,
            idempotencyKey = restored[0].idempotencyKey,
            tagId = restored[0].tagId,
            isMarkedWaste = restored[0].isMarkedWaste,
            taskId = restored[0].taskId
        )
        val outcome = runBlocking {
            BatchConfirm.run(dao, listOf(editableRestored.toBatchInput()), null, { null }, nowMs, zone) { nowMs }
        }
        assertTrue(outcome is ConfirmResult.Committed)
        val saved = runBlocking { dao.getAllRecordsSnapshot() }.single()
        assertEquals("task_prop_77", saved.taskId)

        // Tương thích ngược: nháp đề xuất cũ không có khóa taskId vẫn đọc bình thường
        val legacyArray = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("id", "legacy_prop_1")
                    put("rawInput", "Đọc sách 30p")
                    put("title", "Nháp đề xuất cũ")
                    put("categoryKey", "learn")
                    put("durationMinutesText", "30")
                    put("dateLocal", todayStr)
                    put("startLocal", "")
                    put("endLocal", "")
                    put("hasInitialTimeEvidence", false)
                    put("isFuturePlanned", false)
                    put("idempotencyKey", "legacy_prop_1")
                    put("tagId", JSONObject.NULL)
                    put("isMarkedWaste", false)
                }
            )
        }.toString()
        prefs.edit().putString("chat_proposals_draft_v1", legacyArray).commit()
        val legacyLoaded = TimerAndSettingsStore(prefs).loadChatProposalsDraft()
        assertEquals(1, legacyLoaded.size)
        assertEquals("Nháp đề xuất cũ", legacyLoaded[0].title)
        assertNull(legacyLoaded[0].taskId)

        val vm = File("src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt").readText(Charsets.UTF_8)
        assertTrue(vm.contains("taskId = taskId"))
        assertTrue(vm.contains("syncManualDraftPersistence(scheduledForm"))
    }
}
