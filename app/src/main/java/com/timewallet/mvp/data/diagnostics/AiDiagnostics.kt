package com.timewallet.mvp.data.diagnostics

import com.timewallet.mvp.domain.usecase.AiFault
import com.timewallet.mvp.domain.usecase.CacheStatus
import com.timewallet.mvp.domain.usecase.OutcomeSource
import com.timewallet.mvp.domain.usecase.WallyIntentKind
import java.util.UUID

/**
 * Sự kiện chẩn đoán AI. Tuyệt đối không chứa văn bản người dùng, prompt, khóa API, phản hồi hay dữ liệu sổ cái:
 * chỉ có mã yêu cầu, nguồn, lớp lỗi, mã HTTP, độ trễ, số lần thử lại, số token do nhà cung cấp báo và loại ý định.
 */
data class AiEvent(
    val requestId: String,
    val source: OutcomeSource,
    val fault: AiFault?,
    val httpStatus: Int?,
    val latencyMs: Long,
    val promptVersion: String,
    val retryCount: Int = 0,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val cachedTokens: Int? = null,
    val cacheStatus: CacheStatus? = null,
    val intent: WallyIntentKind? = null,
    /** Tên lớp ngoại lệ (không có thông điệp/nội dung/khóa) để lỗi tự nhận diện được; null nếu không có. */
    val errorClass: String? = null,
    /** v1.0.13: true cho nút "Kiểm tra kết nối" (yêu cầu "ping"); không phải yêu cầu chat nên không vào số liệu chat. */
    val connectionTest: Boolean = false
)

/** Số liệu cộng dồn từ khi mở app (RAM, không lưu đĩa). */
data class AiMetricsSummary(
    val cloudRequests: Int,
    val cloudFailures: Int,
    val failuresByFault: Map<AiFault, Int>,
    val retries: Int,
    val localControl: Int,
    val notConfigured: Int,
    val totalLatencyMs: Long,
    val maxLatencyMs: Long,
    val promptTokens: Long,
    val completionTokens: Long,
    val requestsWithUsage: Int,
    val cachedTokens: Long,
    val cacheHit: Int,
    val cacheMiss: Int,
    val cacheNotReported: Int,
    val cacheNoUsage: Int,
    val byIntent: Map<WallyIntentKind, Int>,
    /** v1.0.13: số lần bấm "Kiểm tra kết nối" đã mở kết nối thật và số lần trong đó lỗi (tách khỏi yêu cầu chat). */
    val connectionTests: Int = 0,
    val connectionTestFailures: Int = 0
) {
    val averageLatencyMs: Long get() = if (cloudRequests == 0) 0L else totalLatencyMs / cloudRequests
}

object AiDiagnostics {
    private const val MAX_EVENTS = 20
    private val events = ArrayDeque<AiEvent>()

    private var cloudRequests = 0
    private var cloudFailures = 0
    private val failuresByFault = HashMap<AiFault, Int>()
    private var retries = 0
    private var localControl = 0
    private var notConfigured = 0
    private var totalLatencyMs = 0L
    private var maxLatencyMs = 0L
    private var promptTokens = 0L
    private var completionTokens = 0L
    private var requestsWithUsage = 0
    private var cachedTokens = 0L
    private var cacheHit = 0
    private var cacheMiss = 0
    private var cacheNotReported = 0
    private var cacheNoUsage = 0
    private val byIntent = HashMap<WallyIntentKind, Int>()
    private var connectionTests = 0
    private var connectionTestFailures = 0

    fun newRequestId(): String = "req-" + UUID.randomUUID().toString().take(8)

    @Synchronized
    fun record(event: AiEvent) {
        if (events.size >= MAX_EVENTS) events.removeFirst()
        events.addLast(event)

        event.intent?.let { byIntent[it] = (byIntent[it] ?: 0) + 1 }
        when (event.source) {
            OutcomeSource.LOCAL -> localControl++
            OutcomeSource.NOT_CONFIGURED -> notConfigured++
            OutcomeSource.CLOUD -> if (event.connectionTest) {
                // Kiểm tra kết nối có mở kết nối thật nhưng KHÔNG phải yêu cầu chat: không đụng vào số yêu cầu/độ trễ/token/bộ nhớ đệm của chat.
                connectionTests++
                if (event.fault != null) connectionTestFailures++
            } else {
                cloudRequests++
                retries += event.retryCount
                totalLatencyMs += event.latencyMs
                maxLatencyMs = maxOf(maxLatencyMs, event.latencyMs)
                if (event.fault != null) {
                    cloudFailures++
                    failuresByFault[event.fault] = (failuresByFault[event.fault] ?: 0) + 1
                } else {
                    when (event.cacheStatus) {
                        CacheStatus.HIT -> cacheHit++
                        CacheStatus.MISS -> cacheMiss++
                        CacheStatus.NOT_REPORTED -> cacheNotReported++
                        CacheStatus.NO_USAGE, null -> cacheNoUsage++
                    }
                    if (event.promptTokens != null || event.completionTokens != null) requestsWithUsage++
                    promptTokens += event.promptTokens ?: 0
                    completionTokens += event.completionTokens ?: 0
                    cachedTokens += event.cachedTokens ?: 0
                }
            }
        }
    }

    @Synchronized
    fun snapshot(): List<AiEvent> = events.toList()

    @Synchronized
    fun summary(): AiMetricsSummary = AiMetricsSummary(
        cloudRequests, cloudFailures, failuresByFault.toMap(), retries, localControl, notConfigured,
        totalLatencyMs, maxLatencyMs, promptTokens, completionTokens, requestsWithUsage, cachedTokens,
        cacheHit, cacheMiss, cacheNotReported, cacheNoUsage, byIntent.toMap(), connectionTests, connectionTestFailures
    )

    @Synchronized
    fun clear() {
        events.clear()
        cloudRequests = 0; cloudFailures = 0; failuresByFault.clear(); retries = 0; localControl = 0; notConfigured = 0
        totalLatencyMs = 0; maxLatencyMs = 0; promptTokens = 0; completionTokens = 0; requestsWithUsage = 0; cachedTokens = 0
        cacheHit = 0; cacheMiss = 0; cacheNotReported = 0; cacheNoUsage = 0; byIntent.clear()
        connectionTests = 0; connectionTestFailures = 0
    }

    fun summaryText(promptVersion: String? = null, promptPrefixHash: String? = null): String {
        val m = summary()
        val faults = if (m.failuresByFault.isEmpty()) "-" else m.failuresByFault.entries.joinToString(",") { "${it.key}=${it.value}" }
        val tokens = if (m.requestsWithUsage == 0) "nhà cung cấp chưa báo usage" else "vào=${m.promptTokens} ra=${m.completionTokens} đệm=${m.cachedTokens}"
        val cache = "HIT=${m.cacheHit} MISS=${m.cacheMiss} KHÔNG_BÁO=${m.cacheNotReported} KHÔNG_USAGE=${m.cacheNoUsage}"
        val intents = if (m.byIntent.isEmpty()) "-" else m.byIntent.entries.joinToString(",") { "${it.key}=${it.value}" }
        return buildString {
            appendLine("Tổng hợp từ khi mở app: yêu cầu cloud=${m.cloudRequests} lỗi=${m.cloudFailures} ($faults) thử lại=${m.retries}")
            appendLine("Độ trễ TB=${m.averageLatencyMs}ms tối đa=${m.maxLatencyMs}ms · lệnh cục bộ=${m.localControl} · chưa cấu hình=${m.notConfigured} · kiểm tra kết nối=${m.connectionTests} (lỗi=${m.connectionTestFailures})")
            appendLine("Token: $tokens · bộ nhớ đệm prompt: $cache")
            appendLine("Ý định: $intents")
            if (promptVersion != null) appendLine("Prompt: $promptVersion (prefix=${promptPrefixHash ?: "?"})")
        }.trimEnd()
    }

    fun asText(): String = snapshot().asReversed().joinToString("\n") {
        "${it.requestId} source=${it.source} fault=${it.fault ?: "-"} http=${it.httpStatus ?: "-"} " +
            "latency=${it.latencyMs}ms retry=${it.retryCount} intent=${if (it.connectionTest) "CONNECTION_TEST" else it.intent ?: "-"} " +
            "tokens=${it.promptTokens ?: "-"}/${it.completionTokens ?: "-"} cache=${it.cacheStatus ?: "-"}(${it.cachedTokens ?: "-"}) " +
            "prompt=${it.promptVersion}" + (it.errorClass?.let { c -> " err=$c" } ?: "")
    }.ifEmpty { "(chưa có yêu cầu AI nào)" }
}
