package com.timewallet.mvp.ui

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.usecase.BatchRecordInput
import com.timewallet.mvp.domain.usecase.ChatEvent
import com.timewallet.mvp.domain.usecase.ChatPhaseMachine
import com.timewallet.mvp.domain.usecase.ParseChatOutcome
import com.timewallet.mvp.domain.usecase.QueryAnswer
import com.timewallet.mvp.domain.usecase.QueryContext
import com.timewallet.mvp.domain.usecase.WallyTurnPolicy

/** Đề xuất -> đầu vào ghi; dùng chung bởi ViewModel và test (không có bản sao). */
fun EditableProposalDraft.toBatchInput(): BatchRecordInput = BatchRecordInput(
    id = id,
    source = "chat",
    rawTitle = title,
    rawCategory = categoryKey,
    dateLocal = dateLocal,
    durationMinutes = durationMinutesText.trim().toIntOrNull(),
    startHm = startLocal.trim(),
    endHm = endLocal.trim(),
    convertFutureToPlanned = isFuturePlanned,
    idempotencyKey = idempotencyKey,
    tagId = tagId
)

/**
 * Áp kết quả phân tích của Cloud AI lên trạng thái giao diện. Hàm THUẦN (không Android, không DAO, không mạng) nên
 * kiểm thử được bằng JVM; ViewModel gọi đúng hàm này trong `_uiState.update`.
 *
 * Bất biến:
 * - Lỗi: không đề xuất, không thẻ trả lời giả, GIỮ input và draft hiện có.
 * - Số liệu của câu hỏi luôn tính từ sổ cái HIỆN TẠI của `state`: nếu sổ cái/timer đã đổi kể từ lúc tính trước
 *   (thêm, sửa, xóa bản ghi) thì tính lại, không dùng số cũ.
 * - Không bao giờ xóa draft đang chờ; chỉ thay bằng danh sách đã gộp (`merged`).
 */
object WallyChatReducer {

    /** Kết quả tính trước (ngoài luồng chính) kèm sổ cái/timer/ngân sách đã dùng, để biết khi nào phải tính lại. */
    class Precomputed(
        val answers: List<QueryAnswer>,
        val basedOnRecords: List<LedgerRecord>,
        val basedOnTimer: ActiveTimerSession?,
        /** null = số liệu không phụ thuộc ngân sách. */
        val basedOnBudgets: List<CategoryTimeBudget>? = null
    ) {
        fun matches(state: TimeWalletUiState): Boolean =
            basedOnRecords === state.records && basedOnTimer == state.activeTimer &&
                (basedOnBudgets == null || basedOnBudgets === state.budgets)
    }

    /**
     * Phiên bản KHÔNG tính toán nặng: trả null nếu cần số liệu mà số tính trước không còn khớp trạng thái hiện tại
     * (sổ cái/timer/ngân sách đã đổi). Người gọi phải tính lại NGOÀI luồng chính rồi thử lại (v1.0.6, nhóm C).
     */
    fun applyResultOrNull(
        state: TimeWalletUiState,
        question: String,
        outcome: ParseChatOutcome,
        merged: List<EditableProposalDraft>,
        precomputed: Precomputed?
    ): TimeWalletUiState? {
        if (outcome.fault != null) return build(state, question, outcome, merged, emptyList())
        val answers = when {
            outcome.queries.isEmpty() -> emptyList()
            precomputed != null && precomputed.matches(state) -> precomputed.answers
            else -> return null
        }
        return build(state, question, outcome, merged, answers)
    }

    /** Đường tính-trong-reducer, chỉ giữ cho test và tương thích; luồng thật dùng [applyResultOrNull]. */
    fun applyResult(
        state: TimeWalletUiState,
        question: String,
        outcome: ParseChatOutcome,
        merged: List<EditableProposalDraft>,
        precomputed: Precomputed?,
        compute: (records: List<LedgerRecord>, timer: ActiveTimerSession?) -> List<QueryAnswer>
    ): TimeWalletUiState =
        applyResultOrNull(state, question, outcome, merged, precomputed)
            ?: build(state, question, outcome, merged, compute(state.records, state.activeTimer))

    private fun build(
        state: TimeWalletUiState,
        question: String,
        outcome: ParseChatOutcome,
        merged: List<EditableProposalDraft>,
        answers: List<QueryAnswer>
    ): TimeWalletUiState {
        if (outcome.fault != null) {
            return state.copy(
                isAnalyzingChat = false,
                chatFault = outcome.fault,
                lastAnalysisSource = outcome.source,
                lastRequestId = outcome.requestId,
                chatPhase = ChatPhaseMachine.next(state.chatPhase, ChatEvent.Result(0, outcome.fault))
            )
        }
        // v1.0.8: trạng thái giao dịch do ỨNG DỤNG phát ngôn (xem WallyTurnPolicy); AI không phải nguồn của "đã lưu / chưa lưu / đang chờ".
        val card = WallyTurnPolicy.buildCard(question, outcome, answers, pendingDrafts = merged.size)
        return state.copy(
            isAnalyzingChat = false,
            proposals = merged,
            expandedProposalId = null,
            chatAnswer = card,
            lastQueryContext = outcome.queries.lastOrNull()
                ?.let { QueryContext(it.metric, it.period, it.category) } ?: state.lastQueryContext,
            chatInputText = if (outcome.proposals.isNotEmpty() || card != null) "" else state.chatInputText,
            chatFault = null,
            lastAnalysisSource = outcome.source,
            lastRequestId = outcome.requestId,
            chatPhase = ChatPhaseMachine.next(
                state.chatPhase, ChatEvent.Result(outcome.proposals.size, null, hasAnswer = card != null)
            )
        )
    }
}
