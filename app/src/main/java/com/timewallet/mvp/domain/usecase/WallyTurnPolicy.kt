package com.timewallet.mvp.domain.usecase

/**
 * WALLY v1.0.8 (V106-N1, V107-F1/F2): kiến trúc "trạng thái giao dịch do ỨNG DỤNG phát ngôn".
 *
 * Quy tắc cốt lõi: AI chỉ hiểu và diễn giải; AI KHÔNG BAO GIỜ là nguồn của câu "đã lưu / chưa lưu / đang chờ". Trạng thái đó
 * chỉ lấy từ ứng dụng (số đề xuất đang chờ, kết quả commit). Mỗi lượt được xếp vào một loại; loại quyết định thẻ trả lời:
 *
 *  - RECORD   (có đề xuất, không truy vấn): không dựng thẻ lời đáp AI; thẻ đề xuất + thông báo do ứng dụng tạo là nội dung chính.
 *  - MIXED    (có đề xuất VÀ truy vấn): bỏ lời đáp AI; headline/số liệu do LedgerQueryEngine tính, kèm dòng nói rõ số liệu chỉ gồm
 *             bản ghi đã lưu và các đề xuất đang chờ chưa được tính, chưa được lưu. Giữ đủ mọi truy vấn hợp lệ.
 *  - QUERY    (chỉ truy vấn): lời đáp AI (đã qua ReplyGuard) chỉ là phần mở đầu; số liệu do engine tính.
 *  - TRANSACTION_CHAT (không đề xuất, không truy vấn, nhưng câu liên quan ghi nhận / hỏi đã lưu chưa / đang còn đề xuất chờ):
 *             thẻ do ỨNG DỤNG tạo, là nội dung chính; lời đáp AI bị bỏ hoàn toàn (không đứng cạnh, không bị "chú thích" bên dưới).
 *             Ngoại lệ duy nhất (v1.0.13, G2): CÂU HỎI LẠI của AI đã qua ReplyGuard ("Bạn muốn ghi 2 giờ hay 3 giờ?") là nội dung chính của thẻ,
 *             và dòng "Chưa có gì được lưu..." do ứng dụng đứng ngay dưới nó. Chỉ lấy các câu hỏi; câu khẳng định của AI vẫn không bao giờ hiện,
 *             và khi người dùng hỏi "đã lưu chưa" thì luôn do ứng dụng trả lời. Lời đáp bị ReplyGuard bỏ thì vẫn là thẻ chung của ứng dụng.
 *  - PLAIN_CHAT: hội thoại không liên quan giao dịch: giữ nguyên lời đáp tự nhiên của AI.
 */
object WallyTurnPolicy {

    enum class Kind { RECORD, MIXED, QUERY, TRANSACTION_CHAT, PLAIN_CHAT }

    private const val B = """(?<![\p{L}\p{N}])"""
    private const val E = """(?![\p{L}\p{N}])"""

    // "Ghi chưa?", "đã lưu chưa", "xong chưa", "lưu rồi à", "có ghi không"...
    private val statusQuestion = Rx.uni(
        """$B(?:đã\s+|được\s+|có\s+)?(?:ghi(?:\s*nhận)?|lưu|thêm|cập\s*nhật|xác\s*nhận|hoàn\s*tất|xong|vào\s+sổ)\s+(?:rồi\s+)?(?:chưa|không|à|hả|nhỉ|phải\s+không)$E""",
        RegexOption.IGNORE_CASE
    )

    // Động từ ghi nhận (loại "ghi chú").
    private val recordVerb = Rx.uni(
        """$B(?:ghi\s*nhận|ghi\s*lại|ghi(?!\s+chú)|lưu|thêm|nhập)$E""",
        RegexOption.IGNORE_CASE
    )

    // Câu hỏi thông tin thuần ("bao nhiêu", "nên làm gì"): có số giờ nhưng không phải yêu cầu ghi.
    private val informationQuestion = Rx.uni(
        """bao\s+nhiêu|như\s+thế\s+nào|thế\s+nào|làm\s+sao|vì\s+sao|tại\s+sao|là\s+gì|ở\s+đâu|${B}nên\s+(?!ghi|lưu)""",
        RegexOption.IGNORE_CASE
    )

    // Cách nói "hoàn tất" mềm trong lời đáp (dùng để bỏ lời đáp khi đang có đề xuất chờ).
    private val weakCompletion = Rx.uni(
        """$B(?:xong|hoàn\s*tất|ổn\s*thỏa|đâu\s+vào\s+đấy)$E|${B}rồi\s+(?:nhé|nha|đó|nè)$E""",
        RegexOption.IGNORE_CASE
    )

    fun isStatusQuestion(userText: String): Boolean = statusQuestion.containsMatchIn(userText)

    /** Câu của người dùng liên quan tới việc ghi nhận (có thời lượng, có động từ ghi, hoặc hỏi đã lưu chưa), trừ câu hỏi thông tin thuần. */
    fun isTransactionRelated(userText: String): Boolean {
        if (statusQuestion.containsMatchIn(userText)) return true
        if (informationQuestion.containsMatchIn(userText)) return false
        return VietnameseChatParser.allDurationMinutes(userText).isNotEmpty() || recordVerb.containsMatchIn(userText)
    }

    fun hasCompletionSemantics(reply: String?): Boolean = !reply.isNullOrBlank() && weakCompletion.containsMatchIn(reply)

    fun classify(userText: String, proposalCount: Int, queryCount: Int, pendingDrafts: Int): Kind = when {
        proposalCount > 0 && queryCount > 0 -> Kind.MIXED
        proposalCount > 0 -> Kind.RECORD
        queryCount > 0 -> Kind.QUERY
        pendingDrafts > 0 || isTransactionRelated(userText) -> Kind.TRANSACTION_CHAT
        else -> Kind.PLAIN_CHAT
    }

    /** Dòng đứng ngay dưới headline của thẻ MIXED; không chứa số đề xuất nên không cũ đi sau khi xác nhận. */
    const val MIXED_LEAD_LINE =
        "Số liệu bên dưới tính từ các bản ghi đã lưu tại thời điểm hỏi; các đề xuất đang chờ xác nhận chưa được tính và chưa được lưu."

    const val STATUS_LABEL = "Trạng thái ghi nhận"

    /**
     * Thẻ trạng thái do ứng dụng tạo (TRANSACTION_CHAT). Là nội dung chính (headline), không phải chú thích.
     * Đánh dấu [ChatAnswer.isTransactionStatus] để ViewModel xóa thẻ khi danh sách đề xuất đổi (tránh số "đang chờ" bị cũ).
     */
    fun statusCard(question: String, pendingDrafts: Int, notes: List<String>, askBack: String? = null): ChatAnswer {
        val statusQ = isStatusQuestion(question)
        val headline = if (pendingDrafts > 0) {
            "Chưa có gì được lưu. Còn $pendingDrafts đề xuất đang chờ bạn xác nhận."
        } else {
            "Chưa có gì được lưu: Wally chưa tạo được đề xuất ghi nhận nào từ câu này."
        }
        val lines = buildList {
            if (pendingDrafts > 0) {
                add("Bấm Xác nhận ở thẻ đề xuất để lưu vào sổ cái, hoặc Bỏ hết.")
                if (!statusQ && isTransactionRelated(question)) add("Câu vừa rồi chưa tạo thêm đề xuất mới.")
            } else if (statusQ) {
                add("Hiện không có đề xuất nào đang chờ. Bản ghi chỉ được lưu sau khi bạn bấm Xác nhận; xem Nhật ký để biết các bản ghi đã lưu.")
            } else {
                add("Hãy nêu rõ hoạt động và thời lượng, ví dụ \"Học 45p\", hoặc dùng Ghi thủ công.")
            }
        }
        if (askBack != null) {
            // Câu hỏi của AI là nội dung chính (nhãn mặc định "Cần hỏi lại"); trạng thái "chưa lưu" do ứng dụng nói ở dòng dưới.
            val below = if (pendingDrafts > 0) listOf(headline) + lines
            else listOf("Chưa có gì được lưu. Hãy nhập lại cả câu cho rõ (hoạt động và thời lượng), hoặc dùng Ghi thủ công.")
            return ChatAnswer(
                question = question,
                headline = askBack,
                lines = below,
                notes = notes.distinct(),
                isClarification = true,
                isTransactionStatus = true
            )
        }
        return ChatAnswer(
            question = question,
            headline = headline,
            lines = lines,
            notes = notes.distinct(),
            isClarification = true,
            label = STATUS_LABEL,
            isTransactionStatus = true
        )
    }

    /** Các câu hỏi của lời đáp AI (đã qua ReplyGuard) nối thành một; null nếu không có câu hỏi nào hoặc người dùng đang hỏi trạng thái lưu. */
    internal fun askBackOf(question: String, reply: String?): String? =
        if (reply.isNullOrBlank() || isStatusQuestion(question)) null
        else ReplyGuard.questionSentences(reply).joinToString(" ").takeIf { it.isNotBlank() }

    /**
     * Dựng thẻ trả lời của một lượt AI thành công (không lỗi). Dùng cho cả ViewModel (qua reducer) và test pipeline.
     * @param pendingDrafts số đề xuất đang chờ SAU khi đã gộp đề xuất mới của lượt này.
     */
    fun buildCard(
        question: String,
        outcome: ParseChatOutcome,
        answers: List<QueryAnswer>,
        pendingDrafts: Int
    ): ChatAnswer? {
        val kind = classify(question, outcome.proposals.size, outcome.queries.size, pendingDrafts)
        return when (kind) {
            Kind.RECORD -> null // thẻ đề xuất + toast do ứng dụng tạo; không dựng thẻ lời đáp AI
            Kind.MIXED -> WallyAnswerBuilder.build(question, null, answers, outcome.warnings, proposalCount = 0, leadLines = listOf(MIXED_LEAD_LINE))
            Kind.QUERY -> {
                val reply = outcome.reply?.takeUnless { pendingDrafts > 0 && hasCompletionSemantics(it) }
                WallyAnswerBuilder.build(question, reply, answers, outcome.warnings, proposalCount = 0)
            }
            Kind.TRANSACTION_CHAT -> statusCard(question, pendingDrafts, outcome.warnings, askBack = askBackOf(question, outcome.reply))
            Kind.PLAIN_CHAT -> WallyAnswerBuilder.build(question, outcome.reply, answers, outcome.warnings, proposalCount = 0)
        }
    }
}
