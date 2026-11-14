package com.timewallet.mvp.domain.usecase

/**
 * WALLY: máy trạng thái của luồng chat/phân tích. Mỗi trạng thái đều có đường thoát.
 *
 * Ghi chú ánh xạ với SPEC: VALIDATING diễn ra nguyên tử bên trong `GeminiProposalClient.analyzeInput`
 * (không quan sát được từ UI), CONFIRMING/SAVED nằm ở luồng xác nhận có chốt chặn riêng (`confirmGuard`).
 */
enum class ChatPhase { IDLE, SENDING, ANSWER_READY, PROPOSAL_READY, ERROR, CANCELLED }

sealed class ChatEvent {
    /** Người dùng gửi câu cần phân tích (đường ghi nhận). */
    data object Submit : ChatEvent()

    /** Một thẻ trả lời đã sẵn sàng ngoài luồng gửi AI (hiện chỉ dùng cho kiểm thử máy trạng thái). */
    data object Answered : ChatEvent()

    /** Kết quả phân tích về: số đề xuất, lỗi cloud (nếu có), và có thẻ trả lời của Wally hay không. */
    data class Result(val proposalCount: Int, val fault: AiFault?, val hasAnswer: Boolean = false) : ChatEvent()

    data object Cancelled : ChatEvent()
    data object Dismissed : ChatEvent()

    /** Số draft còn lại sau khi hủy/xác nhận/bỏ. */
    data class DraftsChanged(val remaining: Int) : ChatEvent()
}

object ChatPhaseMachine {
    fun next(phase: ChatPhase, event: ChatEvent): ChatPhase = when (event) {
        ChatEvent.Submit -> ChatPhase.SENDING // gửi lặp khi đang SENDING bị chốt chặn ở ViewModel, trạng thái giữ nguyên
        ChatEvent.Answered -> if (phase == ChatPhase.SENDING) phase else ChatPhase.ANSWER_READY
        is ChatEvent.Result -> when (phase) {
            ChatPhase.SENDING -> when {
                event.fault != null -> ChatPhase.ERROR
                event.proposalCount > 0 -> ChatPhase.PROPOSAL_READY
                event.hasAnswer -> ChatPhase.ANSWER_READY
                else -> ChatPhase.IDLE
            }
            // Kết quả muộn sau khi hủy không đổi trạng thái (và ViewModel đã bỏ nội dung).
            else -> phase
        }
        ChatEvent.Cancelled -> if (phase == ChatPhase.SENDING) ChatPhase.CANCELLED else phase
        ChatEvent.Dismissed -> when (phase) {
            ChatPhase.ERROR, ChatPhase.CANCELLED, ChatPhase.ANSWER_READY -> ChatPhase.IDLE
            else -> phase
        }
        is ChatEvent.DraftsChanged ->
            if (phase == ChatPhase.PROPOSAL_READY && event.remaining == 0) ChatPhase.IDLE else phase
    }
}
