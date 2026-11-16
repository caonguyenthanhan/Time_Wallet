package com.timewallet.mvp.domain.usecase

import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * AI-01 CLOSURE (F1, F2): lệnh hủy đề xuất đang chờ.
 *
 * Phạm vi: CHỈ các draft chưa lưu trong RAM của tab Chat. Không có đường nào tới Ledger/DB trong file này;
 * xóa bản ghi đã lưu bằng chat không được hỗ trợ.
 */
sealed class CancelCommand {
    /** "Hủy tất cả đề xuất". */
    data object All : CancelCommand()

    /** "Hủy đề xuất" / "Hủy" không nêu đối tượng. */
    data object Unspecified : CancelCommand()

    /** Có tham chiếu: mô tả hoạt động, thời lượng hoặc số thứ tự ("hủy ghi nhận 30 phút đọc sách", "hủy đề xuất số 2"). */
    data class Targeted(val phrase: String, val durationMinutes: Int?, val index: Int?) : CancelCommand()
}

object CancelCommandParser {

    private val vi = Locale("vi", "VN")

    // Lệnh hủy phải ĐỨNG ĐẦU câu và theo sau là đối tượng thuộc về đề xuất/bản ghi ("ghi nhận", "đề xuất", "tất cả"...).
    // "Hủy hợp đồng 2h", "Bỏ cuộc họp 1h", "Bỏ ghi chú 20 phút" là mô tả hoạt động, không phải lệnh hủy.
    private val commandRegex = Rx.uni(
        """^(?:(?:xin|vui lòng|hãy|làm ơn|cho tôi|cho mình|tôi muốn|mình muốn|tôi|mình)\s+)*(?:hủy|huỷ|xóa|xoá|bỏ)(?=\s|$|[.!,])\s*(.*)$"""
    )
    private val objectWordRegex = Rx.uni(
        """^(?:ghi nhận|ghi(?!\s+chú)|đề xuất|bản ghi|bản nháp|nháp|draft|proposal|mục|tất cả|toàn bộ|hết|cái|hoạt động)(?=\s|$|[.!,:])"""
    )
    private val allRegex = Rx.uni("""^(?:tất cả|toàn bộ|hết)(?=\s|$|[.!,:])""")
    private val indexRegex = Rx.uni("""(?:số|thứ|#)\s*(\d{1,2})(?=\s|$|[.!,:])""")
    private val objectFillerRegex = Rx.uni(
        """(?:^|\s)(?:ghi nhận|ghi|đề xuất|bản ghi|bản nháp|nháp|draft|proposal|mục|cái|hoạt động|việc|về|cho|của|đi|nhé|giúp|giùm)(?=\s|$|[.!,:])"""
    )

    // v1.0.13 (C1): dấu hiệu RÕ RÀNG rằng câu nói về dữ liệu của chính ứng dụng (đề xuất, nháp, bản ghi, "ghi nhận", hoạt động đang chờ).
    // "ghi" trần cũng tính, trừ các từ ghép chỉ việc khác: ghi âm, ghi hình, ghi chú, ghi chép, ghi danh, ghi bàn/điểm/tên, ghi nhớ.
    private val appMarkerRegex = Rx.uni(
        """(?:^|\s)(?:ghi nhận|ghi(?!\s+(?:chú|âm|hình|chép|danh|bàn|điểm|tên|nhớ))|đề xuất|bản ghi|bản nháp|nháp|draft|proposal|hoạt động\s+(?:đang chờ|chờ|chưa lưu))(?=\s|$|[.!,:])"""
    )
    private val quantifierRegex = Rx.uni("""(?:^|\s)(?:tất cả|toàn bộ|hết)(?=\s|$|[.!,:])""")

    /** [outside] != null: câu "xóa X <thời lượng>" mà X không phải dữ liệu của ứng dụng (một việc ngoài đời); [clear] = !(outside != null). */
    private class Analysis(val command: CancelCommand, val clear: Boolean, val outside: CancelCommand.Targeted?)

    private fun analyse(text: String): Analysis? {
        val lowered = text.trim().lowercase(vi).trimEnd('.', '!', ' ')
        val rest = (commandRegex.find(lowered) ?: return null).groupValues[1].trim()

        if (rest.isEmpty()) return Analysis(CancelCommand.Unspecified, clear = true, outside = null)
        if (!objectWordRegex.containsMatchIn(rest)) return null
        val isAll = allRegex.containsMatchIn(rest) || objectFillerRegex.replace(rest, " ").trim().let { allRegex.containsMatchIn(it) }

        val index = indexRegex.find(rest)?.groupValues?.get(1)?.toIntOrNull()
        val withoutIndex = indexRegex.replace(rest, " ")
        val minutes = VietnameseChatParser.extractDurationMinutes(withoutIndex)
        val phrase = VietnameseChatParser.stripDurationText(withoutIndex)
            .let { objectFillerRegex.replace(it, " ") }
            .replace(Rx.ascii("""\s+"""), " ")
            .trim(' ', ',', '.', ':', '-')
            .lowercase(vi)

        val command = when {
            isAll -> CancelCommand.All
            phrase.isEmpty() && minutes == null && index == null -> CancelCommand.Unspecified
            else -> CancelCommand.Targeted(phrase, minutes, index)
        }
        // Có thời lượng + một đối tượng được gọi tên mà không có dấu hiệu dữ liệu ứng dụng và không có số thứ tự ("xóa ghi hình buổi livestream 30 phút",
        // "xóa hết ảnh trùng 50 phút"): là mô tả một hoạt động ngoài đời (quy tắc 7d của prompt), không phải lệnh hủy.
        val subject = quantifierRegex.replace(phrase, " ").replace(Rx.ascii("""\s+"""), " ").trim()
        val outside = minutes != null && index == null && subject.isNotEmpty() && !appMarkerRegex.containsMatchIn(rest)
        return Analysis(command, clear = !outside, outside = if (outside) CancelCommand.Targeted(subject, minutes, null) else null)
    }

    /**
     * @return lệnh hủy nếu câu RÕ RÀNG nói về dữ liệu của ứng dụng (lệnh trần "Hủy", "đề xuất", "nháp", "tất cả/hết", "số N", "ghi nhận"...);
     * null nếu là mô tả hoạt động/câu khác, kể cả "xóa <việc ngoài ứng dụng> <thời lượng>". Không biết đề xuất đang chờ nên dùng cho mọi nơi
     * không có danh sách đó (đường cloud, guard); nơi có danh sách (ViewModel) gọi thêm [parseDraftReference].
     */
    fun parse(text: String): CancelCommand? = analyse(text)?.takeIf { it.clear }?.command

    /**
     * Câu "xóa X <thời lượng>" mà [parse] không coi là lệnh (X không có dấu hiệu dữ liệu ứng dụng) nhưng X khớp (tiêu đề/thời lượng, như "hủy ghi nhận 30 phút
     * đọc sách") một đề xuất đang chờ trong [drafts]: vẫn là lệnh hủy đề xuất đó. Không khớp draft nào => null (câu đi lên cloud như mô tả hoạt động).
     */
    fun parseDraftReference(text: String, drafts: List<DraftRef>): CancelCommand.Targeted? {
        val t = analyse(text)?.outside ?: return null
        val hasToken = t.phrase.split(' ').any { it.length >= 2 }
        return t.takeIf { hasToken && ProposalLifecycle.matchTargeted(it, drafts).isNotEmpty() }
    }
}

/** Ảnh chụp tối thiểu của một draft chưa lưu, đủ để đối chiếu lệnh hủy. */
data class DraftRef(
    val id: String,
    val title: String,
    val rawInput: String,
    val durationMinutes: Int
)

data class CancelOutcome(
    val cancelIds: Set<String>,
    val message: String,
    /** true: câu hủy cần được trả lời bằng hỏi lại (không đổi gì). */
    val askBack: Boolean,
    /** true: kết quả phân tích đang chạy phải bị bỏ khi về. */
    val invalidateInFlight: Boolean
)

/** Số thế hệ của phân tích chat. Hủy làm tăng số thế hệ; kết quả về với số cũ bị bỏ. */
class AnalysisEpoch {
    private val value = AtomicInteger(0)
    fun current(): Int = value.get()
    fun invalidate(): Int = value.incrementAndGet()
    fun isCurrent(captured: Int): Boolean = value.get() == captured
}

object ProposalLifecycle {

    const val STORED_NOTE = "Bản ghi đã lưu không bị thay đổi."
    const val MAX_DRAFTS = 15

    private fun describe(drafts: List<DraftRef>): String =
        drafts.mapIndexed { i, d -> "${i + 1}. ${d.title} (${d.durationMinutes}p)" }.joinToString("; ")

    /**
     * Các draft khớp một lệnh hủy có mô tả. v1.0.6 (K1): các draft tách từ CÙNG một câu dùng chung rawInput, nên đối chiếu theo rawInput khiến "hủy học"
     * khớp mọi draft. Ưu tiên TIÊU ĐỀ của draft; chỉ khi không draft nào khớp tiêu đề mới xét thêm rawInput.
     */
    internal fun matchTargeted(command: CancelCommand.Targeted, drafts: List<DraftRef>): List<DraftRef> {
        val vi = Locale("vi", "VN")
        val tokens = command.phrase.split(' ').filter { it.length >= 2 }
        fun candidates(haystackOf: (DraftRef) -> String) = drafts.filter { d ->
            val durationOk = command.durationMinutes == null || d.durationMinutes == command.durationMinutes
            val hay = haystackOf(d)
            durationOk && tokens.all { hay.contains(it) }
        }
        val byTitle = candidates { it.title.lowercase(vi) }
        return if (tokens.isNotEmpty() && byTitle.isNotEmpty()) byTitle else candidates { (it.title + " " + it.rawInput).lowercase(vi) }
    }

    fun resolve(command: CancelCommand, drafts: List<DraftRef>, analysisInFlight: Boolean): CancelOutcome {
        fun nothing(msg: String, invalidate: Boolean = false) =
            CancelOutcome(emptySet(), msg, askBack = false, invalidateInFlight = invalidate)
        fun ask(msg: String) = CancelOutcome(emptySet(), msg, askBack = true, invalidateInFlight = false)
        fun cancel(ids: Set<String>, msg: String) =
            CancelOutcome(ids, msg, askBack = false, invalidateInFlight = analysisInFlight)

        val inFlightNote = if (analysisInFlight) " Kết quả phân tích đang chạy cũng được bỏ, hãy nhập lại nếu cần." else ""

        return when (command) {
            CancelCommand.All -> when {
                drafts.isNotEmpty() -> cancel(drafts.map { it.id }.toSet(), "Đã hủy ${drafts.size} đề xuất chưa lưu. $STORED_NOTE$inFlightNote")
                analysisInFlight -> nothing("Đã hủy kết quả phân tích đang chạy. $STORED_NOTE", invalidate = true)
                else -> nothing("Không có đề xuất nào đang chờ để hủy. $STORED_NOTE")
            }

            CancelCommand.Unspecified -> when {
                drafts.size == 1 -> cancel(setOf(drafts[0].id), "Đã hủy đề xuất \"${drafts[0].title}\". $STORED_NOTE$inFlightNote")
                drafts.isEmpty() && analysisInFlight -> nothing("Đã hủy kết quả phân tích đang chạy. $STORED_NOTE", invalidate = true)
                drafts.isEmpty() -> nothing("Không có đề xuất nào đang chờ để hủy. $STORED_NOTE")
                else -> ask("Đang có ${drafts.size} đề xuất chờ: ${describe(drafts)}. Bạn muốn hủy đề xuất nào? Ví dụ \"hủy đề xuất số 2\" hoặc \"hủy tất cả đề xuất\".")
            }

            is CancelCommand.Targeted -> {
                if (command.index != null) {
                    val picked = drafts.getOrNull(command.index - 1)
                    return if (picked != null) {
                        cancel(setOf(picked.id), "Đã hủy đề xuất số ${command.index}: \"${picked.title}\". $STORED_NOTE$inFlightNote")
                    } else if (drafts.isEmpty()) {
                        nothing("Không có đề xuất nào đang chờ để hủy. $STORED_NOTE")
                    } else {
                        ask("Không có đề xuất số ${command.index}. Đang có: ${describe(drafts)}.")
                    }
                }
                val matches = matchTargeted(command, drafts)
                when {
                    matches.size == 1 ->
                        cancel(setOf(matches[0].id), "Đã hủy đề xuất \"${matches[0].title}\" (${matches[0].durationMinutes}p). $STORED_NOTE$inFlightNote")
                    matches.size > 1 ->
                        ask("Có ${matches.size} đề xuất khớp: ${describe(matches)}. Hãy nói rõ hơn, ví dụ \"hủy đề xuất số 1\".")
                    drafts.isEmpty() && !analysisInFlight ->
                        nothing("Không có đề xuất nào đang chờ để hủy. $STORED_NOTE Xóa bản ghi đã lưu không hỗ trợ qua chat.")
                    analysisInFlight ->
                        ask("Đang phân tích nên chưa thấy đề xuất khớp. Nói \"hủy tất cả đề xuất\" để bỏ cả kết quả đang chạy.")
                    else ->
                        ask("Không thấy đề xuất chờ nào khớp. Đang có: ${describe(drafts)}. $STORED_NOTE")
                }
            }
        }
    }

    /**
     * Áp lệnh hủy lên danh sách draft. Nếu cần bỏ kết quả đang chạy thì tăng [epoch] NGAY trong cùng bước,
     * để kết quả về sau không tái tạo draft đã hủy. Chỉ đụng danh sách draft trong RAM.
     */
    fun <T> applyCancel(
        drafts: List<T>,
        toRef: (T) -> DraftRef,
        command: CancelCommand,
        analysisInFlight: Boolean,
        epoch: AnalysisEpoch
    ): Pair<CancelOutcome, List<T>> {
        val outcome = resolve(command, drafts.map(toRef), analysisInFlight)
        if (outcome.invalidateInFlight) epoch.invalidate()
        val remaining = drafts.filterNot { toRef(it).id in outcome.cancelIds }
        return outcome to remaining
    }

    /** Gộp kết quả phân tích vào draft; null nếu kết quả đã cũ (đã bị hủy). */
    fun <T> mergeIfCurrent(
        current: List<T>,
        incoming: List<T>,
        capturedEpoch: Int,
        epoch: AnalysisEpoch
    ): List<T>? =
        if (epoch.isCurrent(capturedEpoch)) (current + incoming).take(MAX_DRAFTS) else null
}
