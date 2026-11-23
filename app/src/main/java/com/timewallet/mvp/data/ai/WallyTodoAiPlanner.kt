package com.timewallet.mvp.data.ai

import com.timewallet.mvp.data.diagnostics.AiDiagnostics
import com.timewallet.mvp.data.local.AiPreferences
import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.AiReadPermissions
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.TaskPriority
import com.timewallet.mvp.domain.model.TaskStatus
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.AiFault
import com.timewallet.mvp.domain.usecase.AiTodoAnalysisReport
import com.timewallet.mvp.domain.usecase.AiTodoEngine
import com.timewallet.mvp.domain.usecase.AiTodoProposal
import com.timewallet.mvp.domain.usecase.AiTodoProposalKind
import com.timewallet.mvp.domain.usecase.ApiKeyHygiene
import com.timewallet.mvp.domain.usecase.ScheduleGridEngine
import com.timewallet.mvp.domain.usecase.TimeRange
import com.timewallet.mvp.domain.usecase.TodoRiskAlert
import com.timewallet.mvp.domain.usecase.fingerprint
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Bộ lập kế hoạch AI To-do tách khỏi `GeminiProposalClient` để giữ tuyệt đối bất biến không cho phép
 * lớp phân tích chat sổ cái/ngân sách (`GeminiProposalClient`) đụng tới `LedgerRecord` hay `CategoryTimeBudget`,
 * đồng thời vẫn gửi mọi yêu cầu HTTPS qua gateway kiểm soát origin duy nhất của [GeminiProposalClient].
 */
object WallyTodoAiPlanner {

    private val hmFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    sealed class TodoPlanOutcome {
        data class CloudReady(val report: AiTodoAnalysisReport, val requestId: String) : TodoPlanOutcome()
        data class OfflineFallback(val report: AiTodoAnalysisReport, val reasonVi: String) : TodoPlanOutcome()
        data class Blocked(val reasonVi: String, val fault: AiFault) : TodoPlanOutcome()
    }

    suspend fun analyzeTodoPlan(
        tasks: List<TaskItem>,
        records: List<LedgerRecord>,
        activeTimer: ActiveTimerSession?,
        todayLocal: String,
        nowTime: LocalTime,
        zoneId: ZoneId,
        aiReadPermissions: AiReadPermissions,
        optInEnabled: Boolean,
        baseUrl: String = AiPreferences.DEFAULT_AI_BASE_URL,
        apiKey: String,
        modelName: String = AiPreferences.DEFAULT_AI_MODEL,
        trustedHost: String? = null,
        connectionFactory: HttpOpener? = null,
        nowMs: () -> Long = { System.currentTimeMillis() }
    ): TodoPlanOutcome {
        val nowEpoch = nowMs()
        if (!aiReadPermissions.readTasks) {
            return TodoPlanOutcome.Blocked(
                reasonVi = "Quyền đọc Công việc & Inbox đang tắt trong Cài đặt.",
                fault = AiFault.CONFIG
            )
        }
        val openTasks = tasks.filter { !it.isDone && it.status != TaskStatus.ARCHIVED }
        val localRisks = AiTodoEngine.analyzeWorkloadAndRisks(openTasks, records, activeTimer, todayLocal, nowEpoch, zoneId)
        val localProposals = AiTodoEngine.buildDeterministicTodoProposals(openTasks, records, activeTimer, todayLocal, nowEpoch, zoneId)
        val localOrder = openTasks.sortedWith(
            compareBy<TaskItem>(
                { if (it.dueDateLocal != null && it.dueDateLocal < todayLocal) 0 else 1 },
                { it.priority.sortWeight },
                { it.dueDateLocal ?: "9999-99-99" }
            )
        ).map { it.id }

        fun localReport(summary: String): AiTodoAnalysisReport = AiTodoAnalysisReport(
            generatedAtEpochMs = nowEpoch,
            dateLocal = todayLocal,
            fromCloudAi = false,
            summaryVi = summary,
            orderedTaskIds = localOrder,
            risks = localRisks,
            proposals = localProposals,
            permissionsFingerprint = aiReadPermissions.fingerprint()
        )

        val cleanKey = ApiKeyHygiene.normalize(apiKey)
        val cleanBaseUrl = baseUrl.trim().ifEmpty { AiPreferences.DEFAULT_AI_BASE_URL }.trimEnd('/')
        val host = AiPreferences.extractValidatedHttpsHost(cleanBaseUrl)
        if (!optInEnabled || cleanKey.isBlank() || host == null ||
            (!trustedHost.isNullOrBlank() && !host.equals(trustedHost.trim(), ignoreCase = true)) ||
            ApiKeyHygiene.check(cleanKey) != null
        ) {
            return TodoPlanOutcome.OfflineFallback(
                report = localReport("Tổng hợp cục bộ (${openTasks.size} việc mở, ${localRisks.size} lưu ý) — chưa bật Cloud AI."),
                reasonVi = "Chưa cấu hình hoặc chưa bật Cloud AI; đang dùng bộ lọc & cảnh báo rủi ro cục bộ."
            )
        }
        if (openTasks.isEmpty()) {
            return TodoPlanOutcome.OfflineFallback(
                report = localReport("Hôm nay không còn công việc mở."),
                reasonVi = "Chưa có To-do nào cần AI sắp xếp."
            )
        }

        val todayDate = runCatching { LocalDate.parse(todayLocal) }.getOrElse { LocalDate.now(zoneId) }
        val nowMin = nowTime.hour * 60 + nowTime.minute
        val contextObj = JSONObject().apply {
            put("today", todayLocal)
            put("nowHm", hmFormatter.format(nowTime))
            put("tasks", JSONArray().apply {
                openTasks.take(15).forEach { t ->
                    put(
                        JSONObject()
                            .put("id", t.id)
                            .put("title", t.title)
                            .put("priority", t.priority.name)
                            .put("estimatedMinutes", t.estimatedMinutes ?: 30)
                            .put("dueDate", t.dueDateLocal)
                            .put("dueTime", t.dueTimeLocal)
                            .put("postponedCount", t.postponedCount)
                            .put("subtasksCount", t.subtasks.size)
                            .put("category", t.category.key)
                    )
                }
            })
            if (aiReadPermissions.readLedger) {
                val dayModel = ScheduleGridEngine.buildDayScheduleModel(todayDate, records, activeTimer, nowEpoch, zoneId)
                put("freeGaps", JSONArray().apply {
                    dayModel.freeGaps
                        .filter { it.endMinute > nowMin + 10 }
                        .take(8)
                        .forEach { g ->
                            val s = maxOf(g.startMinute, nowMin)
                            val e = minOf(g.endMinute, 23 * 60)
                            if (e - s >= 15) put("${TimeRange.formatHm(s)}-${TimeRange.formatHm(e)}")
                        }
                })
            }
        }

        val systemInstruction = """
            Bạn là Wally AI To-do Planner. Hãy phân tích danh sách công việc trong JSON và trả về ĐÚNG MỘT JSON object:
            {
              "summary": "Nhận xét ngắn gọn 1-2 câu về khối lượng việc & điểm cần ưu tiên hôm nay",
              "orderedTaskIds": ["id1", "id2"],
              "proposals": [
                {
                  "kind": "SCHEDULE_INTO_GAP | PRIORITIZE_TODAY | POSTPONE_TO_TOMORROW | SPLIT_SUBTASKS",
                  "taskId": "id",
                  "reason": "Lý do ngắn gọn",
                  "targetDateLocal": "YYYY-MM-DD hoặc null",
                  "startHm": "HH:mm hoặc null",
                  "endHm": "HH:mm hoặc null",
                  "suggestedPriority": "P1 | P2 | P3 hoặc null",
                  "suggestedSubtasks": ["bước 1", "bước 2"]
                }
              ]
            }
            QUY TẮC: Chỉ dùng đúng id từ danh sách; chỉ chọn khoảng giờ trống nằm trong freeGaps; tối đa 5 proposals; mọi thay đổi chỉ là ĐỀ XUẤT chờ người dùng duyệt. Tuyệt đối KHÔNG sửa deadline (dueDate/dueTime) của việc khi gợi ý SCHEDULE_INTO_GAP; nếu khung giờ đề xuất nằm sau hạn hoặc việc đã quá hạn thì phải ghi rõ hạn hiện tại, trạng thái quá hạn và việc khung đề xuất nằm sau hạn trong reason.
        """.trimIndent()

        val requestId = AiDiagnostics.newRequestId()
        val endpoint = if (cleanBaseUrl.endsWith("/chat/completions")) cleanBaseUrl else "$cleanBaseUrl/chat/completions"
        val payload = JSONObject().apply {
            put("model", modelName.trim().ifEmpty { AiPreferences.DEFAULT_AI_MODEL })
            put("temperature", 0.2)
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemInstruction))
                    .put(JSONObject().put("role", "user").put("content", contextObj.toString()))
            )
        }.toString()

        return try {
            val body = GeminiProposalClient.executeTodoPlannerCompletion(
                endpoint = endpoint,
                apiKey = cleanKey,
                payload = payload,
                opener = connectionFactory,
                remainingMs = 30_000L,
                expectedOrigin = host
            )
            val parsed = parseTodoPlanJson(
                responseBody = body,
                openTasks = openTasks,
                records = records,
                activeTimer = activeTimer,
                todayLocal = todayLocal,
                nowEpochMs = nowEpoch,
                zoneId = zoneId,
                fallbackRisks = localRisks,
                permsFingerprint = aiReadPermissions.fingerprint()
            )
            TodoPlanOutcome.CloudReady(parsed, requestId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val fault = GeminiProposalClient.classifyTodoPlannerError(e)
            TodoPlanOutcome.OfflineFallback(
                report = localReport("Không kết nối được Cloud AI (${fault.fault.messageVi}); dùng gợi ý cục bộ."),
                reasonVi = GeminiProposalClient.describeTodoPlannerError(fault)
            )
        }
    }

    internal fun parseTodoPlanJson(
        responseBody: String,
        openTasks: List<TaskItem>,
        records: List<LedgerRecord>,
        activeTimer: ActiveTimerSession?,
        todayLocal: String,
        nowEpochMs: Long,
        zoneId: ZoneId,
        fallbackRisks: List<TodoRiskAlert>,
        permsFingerprint: String
    ): AiTodoAnalysisReport {
        val root = StrictJson.parseSingleObject(responseBody)
        if (root.has("error") && !root.has("choices")) throw AiHttpException(AiFault.UNKNOWN, 200)
        val contentJson = if (root.has("choices")) {
            val choices = root.get("choices") as? JSONArray ?: throw AiHttpException(AiFault.MALFORMED)
            val msg = (choices.optJSONObject(0)?.opt("message") as? JSONObject) ?: throw AiHttpException(AiFault.MALFORMED)
            val c = msg.opt("content") as? String ?: throw AiHttpException(AiFault.MALFORMED)
            when (val extracted = StrictJson.extract(c)) {
                is StrictJson.Extracted.Obj -> extracted.json
                is StrictJson.Extracted.Text -> throw AiHttpException(AiFault.MALFORMED)
            }
        } else {
            root
        }

        val taskMap = openTasks.associateBy { it.id }
        val summary = contentJson.optString("summary", "").trim().take(260)
            .ifBlank { "Wally AI đã sắp xếp ${openTasks.size} công việc mở theo độ khẩn và quỹ thời gian." }

        val orderSeen = LinkedHashSet<String>()
        val orderArr = contentJson.optJSONArray("orderedTaskIds") ?: JSONArray()
        for (i in 0 until orderArr.length()) {
            val id = orderArr.optString(i, "").trim()
            if (id in taskMap) orderSeen += id
        }
        openTasks.forEach { if (it.id !in orderSeen) orderSeen += it.id }

        val rawProps = contentJson.optJSONArray("proposals") ?: JSONArray()
        val parsedProps = mutableListOf<AiTodoProposal>()
        for (i in 0 until minOf(rawProps.length(), 6)) {
            val item = rawProps.optJSONObject(i) ?: continue
            val taskId = item.optString("taskId", "").trim()
            val task = taskMap[taskId] ?: continue
            val kind = runCatching { AiTodoProposalKind.valueOf(item.optString("kind", "").trim()) }.getOrNull() ?: continue
            val reason = item.optString("reason", "").trim().ifBlank { kind.labelVi }.take(180)
            val dateStr = item.optString("targetDateLocal", "").trim().takeIf { it.isNotEmpty() && it != "null" }
            val startHm = item.optString("startHm", "").trim().takeIf { it.isNotEmpty() && it != "null" && TimeRange.parseMinuteOfDay(it) != null }
            val endHm = item.optString("endHm", "").trim().takeIf { it.isNotEmpty() && it != "null" && TimeRange.parseMinuteOfDay(it) != null }
            val prio = item.optString("suggestedPriority", "").trim().takeIf { it.isNotEmpty() && it != "null" }?.let(TaskPriority::fromStorage)
            val subs = buildList {
                val sa = item.optJSONArray("suggestedSubtasks")
                if (sa != null) {
                    for (j in 0 until minOf(sa.length(), 5)) {
                        sa.optString(j, "").trim().takeIf { it.isNotEmpty() }?.let(::add)
                    }
                }
            }
            if (kind == AiTodoProposalKind.SCHEDULE_INTO_GAP && (startHm == null || endHm == null)) continue
            if (kind == AiTodoProposalKind.SPLIT_SUBTASKS && subs.isEmpty()) continue
            parsedProps += AiTodoProposal(
                id = "ai_todo_${kind.name.lowercase()}_${task.id}_$i",
                kind = kind,
                taskId = task.id,
                taskTitle = task.title,
                reasonVi = reason,
                targetDateLocal = dateStr ?: todayLocal,
                startHm = startHm,
                endHm = endHm,
                suggestedPriority = prio,
                suggestedSubtasks = subs,
                categoryKey = task.category.key,
                needsConfirmation = true
            )
        }
        val validatedProps = AiTodoEngine.revalidateTodoProposals(
            proposals = parsedProps,
            tasks = openTasks,
            records = records,
            activeTimer = activeTimer,
            todayLocal = todayLocal,
            nowEpochMs = nowEpochMs,
            zoneId = zoneId
        )

        return AiTodoAnalysisReport(
            generatedAtEpochMs = nowEpochMs,
            dateLocal = todayLocal,
            fromCloudAi = true,
            summaryVi = summary,
            orderedTaskIds = orderSeen.toList(),
            risks = fallbackRisks,
            proposals = validatedProps,
            permissionsFingerprint = permsFingerprint
        )
    }
}
