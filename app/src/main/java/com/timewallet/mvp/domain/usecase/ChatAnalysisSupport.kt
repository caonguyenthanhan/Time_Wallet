package com.timewallet.mvp.domain.usecase

import java.util.concurrent.atomic.AtomicBoolean

/**
 * AI-01 (Finding A): thông báo sau khi phân tích câu nhập. Không được nuốt cảnh báo:
 * - lỗi cấu hình / lỗi gọi AI (không còn fallback cục bộ từ WALLY API-FIRST),
 * - hoạt động bị bỏ qua (lệch giờ, thiếu thời lượng, mệnh đề tổng hợp...).
 */
object ChatAnalysisFeedback {

    private const val MAX_WARNINGS_SHOWN = 2

    fun toastMessage(draftCount: Int, warnings: List<String>): String {
        val distinct = warnings.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (draftCount <= 0) {
            return distinct.firstOrNull()
                ?: "Chưa nhận diện được thời lượng. Thử: \"Học 45p, đọc sách 30p\" hoặc \"14h-15h làm việc\"."
        }
        val base = "Đã tách $draftCount hoạt động. Bấm \"Xác nhận toàn bộ\" để lưu hàng loạt."
        if (distinct.isEmpty()) return base
        val shown = distinct.take(MAX_WARNINGS_SHOWN).joinToString(" ")
        val more = distinct.size - MAX_WARNINGS_SHOWN
        val suffix = if (more > 0) " (+$more lưu ý khác)" else ""
        return "$base Lưu ý: $shown$suffix"
    }
}

/**
 * AI-01 (Finding B): chốt chặn re-entrancy cho thao tác phân tích. Nguyên tử, không phụ thuộc vào
 * thời điểm recomposition của UI.
 */
class ChatAnalysisGuard {
    private val running = AtomicBoolean(false)

    /** @return true nếu giành được quyền chạy; false nếu đang có lượt phân tích khác. */
    fun tryAcquire(): Boolean = running.compareAndSet(false, true)

    fun release() {
        running.set(false)
    }
}

/**
 * WALLY v1.0.6 (F-V105-04): quyền sở hữu một lượt phân tích theo "vé" (ticket) gắn với [epoch], thay cho cờ khóa toàn cục.
 *
 * - [tryBegin] chỉ thất bại khi đang có một lượt CÒN HIỆU LỰC (chống bấm đúp). Sau Hủy (epoch tăng) lượt mới bắt đầu ngay,
 *   không bị coroutine cũ đang tháo dỡ chặn ("Hủy → Gửi lại" không mất cú chạm).
 * - [finish] chỉ nhả quyền nếu vé vẫn là vé hiện hành: coroutine cũ không thể nhả khóa của lượt mới.
 * - Mọi ghi trạng thái của một lượt phải kiểm tra [isCurrent]; lượt cũ chỉ được phép biến mất âm thầm.
 * Bất kỳ ai tăng [epoch] (nút Hủy, lệnh "Hủy tất cả đề xuất") đều tự động vô hiệu hóa lượt đang chạy, không cần nhớ nhả khóa.
 */
class ChatRequestOwner(val epoch: AnalysisEpoch = AnalysisEpoch()) {
    private val lock = Any()
    private var inFlight: Int? = null

    /** @return vé của lượt mới, hoặc null nếu một lượt còn hiệu lực đang chạy. */
    fun tryBegin(): Int? = synchronized(lock) {
        val live = inFlight
        if (live != null && epoch.isCurrent(live)) null
        else epoch.current().also { inFlight = it }
    }

    fun isCurrent(ticket: Int): Boolean = epoch.isCurrent(ticket)

    /** @return true nếu [ticket] đang sở hữu và đã được nhả; false nếu vé đã cũ (không làm gì cả). */
    fun finish(ticket: Int): Boolean = synchronized(lock) {
        if (inFlight == ticket && epoch.isCurrent(ticket)) { inFlight = null; true } else false
    }

    /** Hủy lượt đang chạy (nếu có). @return true nếu có lượt còn hiệu lực bị hủy. */
    fun cancel(): Boolean = synchronized(lock) {
        val live = inFlight?.let { epoch.isCurrent(it) } == true
        if (live) epoch.invalidate()
        inFlight = null
        live
    }
}

/** WALLY A08: thông báo sau khi xác nhận; phân biệt "đã lưu" với "đã có sẵn, không ghi trùng". */
object ConfirmFeedback {
    fun message(saved: Int, skippedDuplicates: Int): String = when {
        saved > 0 && skippedDuplicates > 0 ->
            "Đã lưu $saved hoạt động; $skippedDuplicates hoạt động đã có sẵn nên không ghi trùng."
        saved > 0 -> "Đã lưu nguyên tử $saved hoạt động vào sổ cái."
        skippedDuplicates > 0 -> "Các hoạt động này đã có trong sổ cái nên không ghi trùng."
        else -> "Không có hoạt động nào được lưu."
    }
}
