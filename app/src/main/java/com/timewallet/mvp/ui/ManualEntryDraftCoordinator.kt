package com.timewallet.mvp.ui

import com.timewallet.mvp.data.local.ManualEntryDraft
import com.timewallet.mvp.data.local.TimerAndSettingsStore
import com.timewallet.mvp.domain.model.Tag
import java.time.LocalDate

/**
 * Điều phối vòng đời bản nháp Nhập giờ thủ công giữa UI state và [TimerAndSettingsStore].
 * ViewModel và unit test dùng chung đúng các hàm này để bảo đảm hành vi khôi phục, Clean và xóa sau khi Lưu
 * nhất quán khi chuyển màn hình, đóng app, lưu lỗi hoặc lưu thành công.
 */
object ManualEntryDraftCoordinator {

    /**
     * Mở hoặc khôi phục form từ [storedDraft] (nếu có dữ liệu đáng giữ).
     * Bỏ qua `tagId` nếu danh sách [tags] đã tải không còn Tag đó ở trạng thái hoạt động, nhưng giữ nguyên nhóm & nội dung khác.
     */
    fun openOrRestore(
        storedDraft: ManualEntryDraft?,
        requestedCategoryKey: String?,
        tags: List<Tag>,
        todayLocal: String = LocalDate.now().toString()
    ): Pair<ManualEntryState, ManualEntryDraft?> {
        if (storedDraft != null && storedDraft.isPersistable(todayLocal)) {
            val activeTagIds = tags.filter { !it.isArchived }.map { it.id }.toSet()
            val safeTagId = storedDraft.tagId?.takeIf { tags.isEmpty() || it in activeTagIds }
            val restored = ManualEntryState(
                sessionId = storedDraft.sessionId,
                categoryKey = storedDraft.categoryKey ?: requestedCategoryKey,
                hoursText = storedDraft.hoursText,
                minutesText = storedDraft.minutesText,
                dateLocal = storedDraft.dateLocal,
                note = storedDraft.note,
                mode = if (storedDraft.modeName == ManualMode.RANGE.name) ManualMode.RANGE else ManualMode.DURATION,
                tagId = safeTagId,
                tagQuery = storedDraft.tagQuery,
                startText = storedDraft.startText,
                endText = storedDraft.endText,
                endIsNow = storedDraft.endIsNow,
                saveAsPlanned = storedDraft.saveAsPlanned,
                isMarkedWaste = storedDraft.isMarkedWaste,
                taskId = storedDraft.taskId
            )
            return restored to storedDraft
        }
        return ManualEntryState(categoryKey = requestedCategoryKey, dateLocal = todayLocal) to null
    }

    /**
     * Đồng bộ ngay mọi thay đổi đang nhập xuống [store]. Nếu người dùng xóa sạch mọi trường về rỗng mặc định thì dọn nháp.
     */
    fun syncDraft(
        store: TimerAndSettingsStore,
        form: ManualEntryState,
        todayLocal: String = LocalDate.now().toString(),
        nowEpochMs: Long = System.currentTimeMillis(),
        immediate: Boolean = false
    ): ManualEntryDraft? {
        return if (form.isPersistable(todayLocal)) {
            val draft = form.toDraft(nowEpochMs)
            store.saveManualEntryDraft(draft, immediate = immediate)
            draft
        } else {
            store.clearManualEntryDraft(immediate = immediate)
            null
        }
    }

    /** True khi biểu mẫu đã có dữ liệu và cần hộp thoại xác nhận trước khi Clean. */
    fun requiresCleanConfirmation(
        form: ManualEntryState,
        todayLocal: String = LocalDate.now().toString()
    ): Boolean = form.hasUserData(todayLocal)

    /** Clean chủ động: xóa bản nháp đã lưu và cấp [ManualEntryState] sạch với `sessionId` mới. */
    fun cleanAndReset(
        store: TimerAndSettingsStore,
        keepSheetOpen: Boolean,
        todayLocal: String = LocalDate.now().toString()
    ): ManualEntryState? {
        store.clearManualEntryDraft(immediate = true)
        return if (keepSheetOpen) ManualEntryState(dateLocal = todayLocal) else null
    }

    /** Chỉ gọi sau khi ghi nhận sổ cái THÀNH CÔNG (commit DB xong). */
    fun clearOnCommitSuccess(store: TimerAndSettingsStore) {
        store.clearManualEntryDraft(immediate = true)
    }
}
