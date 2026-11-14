package com.timewallet.mvp.data.ai

import com.timewallet.mvp.domain.usecase.AiProposalGuard
import com.timewallet.mvp.domain.usecase.CancelCommand
import com.timewallet.mvp.domain.usecase.CancelCommandParser
import com.timewallet.mvp.domain.usecase.QueryIntentParser
import com.timewallet.mvp.domain.usecase.ReplyGuard
import com.timewallet.mvp.domain.usecase.VietnameseChatParser
import com.timewallet.mvp.domain.usecase.WallyTurnPolicy
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

/** Bộ kiểm tra an toàn (guard) không khởi tạo/chạy được: KHÔNG được gọi AI, tạo hay xác nhận đề xuất. */
internal class GuardUnavailableException(val status: GuardHealth.Status) :
    RuntimeException("Wally guard unavailable: ${status.guard}", status.cause)

/**
 * v1.0.9 — P0 F6: lỗi khởi tạo guard (ví dụ `ExceptionInInitializerError` ← `PatternSyntaxException`) là `Error`, không phải
 * `Exception`; `catch (e: Exception)` không bắt được và tiến trình bị giết. Đối tượng này chạy MỘT lần một bộ "canary" qua mọi guard
 * có Regex (khởi tạo lớp + vài kiểm tra ngữ nghĩa chắc chắn), bắt `Throwable` và ghi nhớ kết quả.
 *
 * Hợp đồng khi KHÔNG khả dụng: giữ nguyên input, báo lỗi rõ ràng, KHÔNG gửi gì lên AI, KHÔNG tạo/xác nhận đề xuất, KHÔNG bỏ qua guard
 * để vẫn ghi dữ liệu. Ghi thủ công, đồng hồ, ngân sách, nhật ký, khóa API không phụ thuộc đối tượng này.
 */
internal object GuardHealth {
    class Status(val ok: Boolean, val guard: String?, val errorClass: String?, val cause: Throwable?)

    class Probe(val name: String, val run: () -> Unit)

    private val lock = Any()
    private val reported = AtomicBoolean(false)

    @Volatile
    private var cached: Status? = null

    // Lambda chỉ tạo đối tượng hàm; lớp guard chỉ được nạp khi probe CHẠY (bên trong try/catch Throwable).
    private val defaultProbes: List<Probe> = listOf(
        Probe("CancelCommandParser") {
            kotlin.check(CancelCommandParser.parse("Hủy tất cả đề xuất") == CancelCommand.All) { "canary: cancel-all" }
        },
        Probe("VietnameseChatParser") {
            kotlin.check(VietnameseChatParser.extractDurationMinutes("Học 45p") == 45) { "canary: 45p" }
        },
        Probe("QueryIntentParser") { QueryIntentParser.parse("Hôm nay tôi học bao nhiêu giờ?", LocalDate.of(2026, 1, 15)) },
        Probe("AiProposalGuard") { AiProposalGuard.apply("Học 45p", emptyList()) },
        Probe("ReplyGuard") { ReplyGuard.sanitize("Chào bạn", "Xin chào", emptyList(), false) },
        Probe("WallyTurnPolicy") { WallyTurnPolicy.isStatusQuestion("Ghi chưa?") },
        Probe("StrictJson") { StrictJson.extract("{\"reply\":\"x\"}") }
    )

    /** Chỗ cắm cho test (mô phỏng guard hỏng). Sản xuất không đổi. */
    @Volatile
    internal var probes: List<Probe> = defaultProbes

    fun check(): Status {
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }
            var result = Status(ok = true, guard = null, errorClass = null, cause = null)
            var transient = false
            for (p in probes) {
                try {
                    p.run()
                } catch (t: Throwable) {
                    // OOM tạm thời không được ghi nhớ vĩnh viễn; còn lỗi khởi tạo/regex là tất định nên ghi nhớ.
                    transient = t is VirtualMachineError && t !is StackOverflowError
                    result = Status(ok = false, guard = p.name, errorClass = t.javaClass.simpleName, cause = t)
                    break
                }
            }
            if (!transient) cached = result
            return result
        }
    }

    /** Ném [GuardUnavailableException] nếu guard không khả dụng (dùng trước mọi việc gọi AI). */
    fun require() {
        val s = check()
        if (!s.ok) throw GuardUnavailableException(s)
    }

    /** Dùng trong giao diện (lúc compose): không bao giờ ném. Guard hỏng ⇒ `false`. */
    fun isCancelCommand(text: String): Boolean = try {
        CancelCommandParser.parse(text) != null
    } catch (_: Exception) {
        false
    } catch (_: LinkageError) {
        false
    } catch (_: StackOverflowError) {
        false
    }

    /** true đúng một lần cho mỗi tiến trình: để ghi nhật ký lỗi một lần thay vì mỗi lần bấm gửi. */
    fun shouldReportOnce(): Boolean = reported.compareAndSet(false, true)

    /** Chỉ cho test. */
    internal fun resetForTest() {
        synchronized(lock) {
            cached = null
            probes = defaultProbes
            reported.set(false)
        }
    }
}
