package com.timewallet.mvp.ui

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.data.ai.GuardUnavailableException
import com.timewallet.mvp.domain.usecase.AiFault
import com.timewallet.mvp.domain.usecase.AnalysisEpoch
import com.timewallet.mvp.domain.usecase.CancelArming
import com.timewallet.mvp.domain.usecase.ChatAnalysisFeedback
import com.timewallet.mvp.domain.usecase.ChatEvent
import com.timewallet.mvp.domain.usecase.ChatPhaseMachine
import com.timewallet.mvp.domain.usecase.ChatRequestOwner
import com.timewallet.mvp.domain.usecase.ParseChatOutcome
import com.timewallet.mvp.domain.usecase.ProposalLifecycle
import com.timewallet.mvp.domain.usecase.QueryAnswer
import com.timewallet.mvp.domain.usecase.QueryIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** Ảnh chụp bất biến của dữ liệu mà số liệu câu hỏi phụ thuộc vào. */
class QueryBasis(
    val records: List<LedgerRecord>,
    val timer: ActiveTimerSession?,
    val budgets: List<CategoryTimeBudget>
)

/**
 * WALLY v1.0.6 (F-V105-04, nhóm C): chạy một lượt phân tích chat với quyền sở hữu theo vé.
 * Tách khỏi ViewModel để kiểm thử được bằng JVM với thứ tự hoàn thành được điều khiển.
 *
 * Bất biến:
 *  1. Một lượt còn hiệu lực tại một thời điểm (bấm đúp bị bỏ); sau Hủy, lượt mới bắt đầu NGAY.
 *  2. Lượt cũ (đã hủy/bị vô hiệu) KHÔNG ghi vào trạng thái: không đổi loading, input, câu trả lời, draft, lỗi; không toast;
 *     và không nhả quyền sở hữu của lượt mới.
 *  3. Số liệu truy vấn được tính NGOÀI luồng chính (cả khi phải tính lại vì sổ cái/timer/ngân sách vừa đổi).
 *  4. (v1.0.13) Hủy trong cửa sổ [CancelArming] ngay sau khi gửi bị bỏ qua (cú chạm thứ hai của bấm đúp Gửi không hủy yêu cầu vừa gửi).
 */
class WallyChatRunner(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<TimeWalletUiState>,
    private val computeAnswers: (QueryBasis, List<QueryIntent>) -> List<QueryAnswer>,
    private val toast: (String) -> Unit,
    private val onUnexpected: (Throwable) -> Unit = {},
    private val computeContext: CoroutineContext = Dispatchers.Default,
    private val launchContext: CoroutineContext = EmptyCoroutineContext,
    val owner: ChatRequestOwner = ChatRequestOwner(),
    // v1.0.13 (F4): mặc định armMs = 0 (Hủy có hiệu lực ngay) cho test/F3-F5; ViewModel truyền CancelArming() = 700 ms để bấm đúp Gửi không tự hủy.
    private val cancelArming: CancelArming = CancelArming(armMs = 0L),
    private val onChatInputConsumed: () -> Unit = {},
    private val onChatTurnApplied: (question: String, stateAfter: TimeWalletUiState) -> Unit = { _, _ -> }
) {
    val epoch: AnalysisEpoch get() = owner.epoch

    private var job: Job? = null
    private var jobTicket: Int = -1

    /** Dành cho test: lượt đang/đã chạy gần nhất. */
    internal val currentJob: Job? get() = job

    /** @return true nếu lượt mới được nhận; false nếu đang có lượt còn hiệu lực (chống bấm đúp). */
    fun submit(text: String, analyze: suspend () -> ParseChatOutcome): Boolean {
        val ticket = owner.tryBegin() ?: return false
        cancelArming.onSendStarted() // chỉ lượt được nhận mới đặt lại cửa sổ; lần bấm trùng (return false ở trên) không động vào
        state.update {
            it.copy(
                isAnalyzingChat = true,
                chatFault = null,
                chatPhase = ChatPhaseMachine.next(it.chatPhase, ChatEvent.Submit)
            )
        }
        jobTicket = ticket
        job = scope.launch(launchContext) { run(ticket, text, analyze) }
        return true
    }

    /** Nút "Hủy". @return true nếu có lượt còn hiệu lực bị hủy; false nếu không có lượt nào hoặc Hủy chưa "lên đạn" (xem [CancelArming]). */
    fun cancel(): Boolean {
        if (!cancelArming.isArmed()) return false
        if (!owner.cancel()) return false
        cancelStaleJob()
        state.update {
            it.copy(
                isAnalyzingChat = false,
                chatPhase = ChatPhaseMachine.next(it.chatPhase, ChatEvent.Cancelled)
            )
        }
        return true
    }

    /** Sau khi epoch bị tăng từ bên ngoài (lệnh "Hủy tất cả đề xuất"): hủy coroutine đã cũ để đóng socket. */
    fun cancelStaleJob() {
        val j = job ?: return
        if (!owner.isCurrent(jobTicket)) j.cancel()
    }

    private enum class Step { APPLIED, STALE, RETRY }

    private suspend fun run(ticket: Int, text: String, analyze: suspend () -> ParseChatOutcome) {
        try {
            val outcome = analyze()
            if (!owner.isCurrent(ticket)) return
            val tagsNow = state.value.tags
            val newDrafts = outcome.proposals.map { EditableProposalDraft.fromProposal(it).withTagMatch(tagsNow) }
            val needsAnswers = outcome.fault == null && outcome.queries.isNotEmpty()
            var attempts = 0
            var effective = outcome
            while (true) {
                val snap = state.value
                val basis = QueryBasis(snap.records, snap.activeTimer, snap.budgets)
                val pre = if (needsAnswers && effective.queries.isNotEmpty()) {
                    WallyChatReducer.Precomputed(
                        answers = withContext(computeContext) { computeAnswers(basis, effective.queries) },
                        basedOnRecords = basis.records,
                        basedOnTimer = basis.timer,
                        basedOnBudgets = basis.budgets
                    )
                } else null
                if (!owner.isCurrent(ticket)) return

                var step = Step.RETRY
                var producedCard = false
                val cur = effective
                state.update { s ->
                    if (!owner.isCurrent(ticket)) {
                        step = Step.STALE
                        s
                    } else {
                        val merged = ProposalLifecycle.mergeIfCurrent(s.proposals, newDrafts, ticket, epoch)
                        if (merged == null) {
                            step = Step.STALE
                            s
                        } else {
                            val next = WallyChatReducer.applyResultOrNull(s, text, cur, merged, pre)
                            if (next == null) {
                                step = Step.RETRY
                                s
                            } else {
                                step = Step.APPLIED
                                producedCard = next.chatAnswer != null
                                next
                            }
                        }
                    }
                }
                when (step) {
                    Step.STALE -> return
                    Step.APPLIED -> {
                        if (state.value.chatInputText.isEmpty()) {
                            onChatInputConsumed()
                        }
                        if (outcome.fault == null && (newDrafts.isNotEmpty() || producedCard)) {
                            onChatTurnApplied(text, state.value)
                        }
                        if (outcome.fault == null && newDrafts.isNotEmpty()) {
                            toast(ChatAnalysisFeedback.toastMessage(newDrafts.size, effective.warnings))
                        } else if (outcome.fault == null && !producedCard) {
                            toast(ChatAnalysisFeedback.toastMessage(0, effective.warnings))
                        }
                        return
                    }
                    Step.RETRY -> {
                        // Sổ cái/timer/ngân sách đổi giữa lúc tính và lúc áp: tính lại (ngoài luồng chính) trên ảnh chụp mới.
                        if (++attempts >= MAX_RECOMPUTE) {
                            effective = outcome.copy(
                                queries = emptyList(),
                                warnings = outcome.warnings + "Sổ cái đang thay đổi liên tục nên chưa tính được số liệu. Hãy hỏi lại."
                            )
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // v1.0.9 (P0 F6): lỗi khởi tạo guard là Error (ExceptionInInitializerError/NoClassDefFoundError) nên `catch Exception` không bắt.
            // OOM thật vẫn được ném lại. Lỗi guard ⇒ thẻ lỗi rõ ràng; không có đề xuất nào được tạo vì kết quả chưa từng được áp.
            if (e is VirtualMachineError && e !is StackOverflowError) throw e
            if (owner.isCurrent(ticket)) {
                if (isGuardFailure(e)) {
                    state.update {
                        it.copy(
                            chatFault = AiFault.GUARD_UNAVAILABLE,
                            chatPhase = ChatPhaseMachine.next(it.chatPhase, ChatEvent.Result(0, AiFault.GUARD_UNAVAILABLE))
                        )
                    }
                }
                onUnexpected(e)
            }
        } finally {
            // Chỉ lượt đang sở hữu mới được tắt trạng thái đang chạy; lượt cũ không đụng vào.
            if (owner.finish(ticket)) state.update { it.copy(isAnalyzingChat = false) }
        }
    }

    private fun isGuardFailure(t: Throwable): Boolean =
        t is GuardUnavailableException || t is LinkageError || t is StackOverflowError ||
            t is java.util.regex.PatternSyntaxException ||
            (t is IllegalArgumentException && t.message?.startsWith("Rx:") == true)

    private companion object {
        const val MAX_RECOMPUTE = 5
    }
}
