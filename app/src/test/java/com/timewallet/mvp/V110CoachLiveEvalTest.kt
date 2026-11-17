package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.ApiKeyHygiene
import com.timewallet.mvp.domain.usecase.CoachCheck
import com.timewallet.mvp.domain.usecase.CoachDigestBuilder
import com.timewallet.mvp.domain.usecase.CoachGuard
import com.timewallet.mvp.domain.usecase.CoachStructuredCheck
import com.timewallet.mvp.domain.usecase.GoalRules
import com.timewallet.mvp.domain.usecase.ReplyGuard
import com.timewallet.mvp.domain.usecase.TagRules
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * v1.1.0 L4 — kiểm chứng AI Time Coach với API THẬT trên dữ liệu SYNTHETIC. KHÔNG chạy trong bộ test thường: chỉ chạy khi có biến môi trường
 * COACH_EVAL_KEY_FILE (đường dẫn tệp khóa QA, đọc trong JVM, không in ra) và COACH_EVAL_OUT (tệp JSONL kết quả); COACH_EVAL_MODEL, COACH_EVAL_REPEAT tùy chọn.
 * Gọi đúng đường production (`CoachDigestBuilder` → `GeminiProposalClient.requestCoach` → `CoachGuard`), ghi từng lần: lỗi, lời kể, kết luận của guard,
 * kiểm tra rubric độc lập (đề cập đúng mục tiêu, không phán xét, không khẳng định đã ghi, dữ liệu mỏng thì thận trọng). Không sửa kết quả; mọi lần không đạt đều được ghi.
 */
class V110CoachLiveEvalTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 18)
    private val nowMs = LocalDateTime.of(2026, 11, 18, 20, 0).atZone(zone).toInstant().toEpochMilli()

    private fun tag(name: String, cat: TimeCategory) = TagRules.newTag(name, TagRules.nameKey(name), cat, 1L, "tag_" + TagRules.nameKey(name).replace(' ', '_'))
    private val run = tag("Chạy bộ", TimeCategory.HEALTH)
    private val gym = tag("Gym", TimeCategory.HEALTH)
    private val read = tag("Đọc sách", TimeCategory.LEARN)
    private val game = tag("Chơi game", TimeCategory.FUN)
    private val yt = tag("YouTube", TimeCategory.FUN)
    private val tags: List<Tag> = listOf(run, gym, read, game, yt)

    private fun rec(id: String, daysAgo: Int, cat: TimeCategory, minutes: Int, t: Tag?) =
        LedgerRecord(id, "manual", TimeLayer.REPORTED_UNTIMED, nowMs, nowMs, cat, t?.name ?: "việc khác", today.minusDays(daysAgo.toLong()).toString(), minutes * 60L, null, null, zone.id, "CONFIRMED", "k_$id", t?.id)

    private fun goal(t: Tag, kind: GoalKind, period: GoalPeriod, minutes: Int, since: Int = 6, id: String = "g_${t.id}_${kind.name}") =
        GoalRules.create(GoalTargetType.TAG, t.id, kind, period, minutes * 60L, today.minusDays(since.toLong()), 1L, id, "grp_$id")

    private class Scenario(val id: String, val days: Int, val records: List<LedgerRecord>, val goals: List<Goal>, val needsGoalMention: String? = null, val expectCaution: Boolean = false, val expectBoth: List<String> = emptyList())

    private fun scenarios(): List<Scenario> = listOf(
        // S1 — ví dụ của Owner: mục tiêu chạy bộ 30p/ngày; hôm nay game 120, YouTube 60, đọc sách 30, chạy bộ 0.
        Scenario("S1_owner_example", 1,
            listOf(rec("a", 0, TimeCategory.FUN, 120, game), rec("b", 0, TimeCategory.FUN, 60, yt), rec("c", 0, TimeCategory.LEARN, 30, read)),
            listOf(goal(run, GoalKind.MIN, GoalPeriod.DAY, 30)), needsGoalMention = "chạy bộ"),
        Scenario("S2_goal_met_streak", 7,
            (0..4).map { rec("r$it", it, TimeCategory.HEALTH, 35, run) } + listOf(rec("g", 0, TimeCategory.FUN, 40, game)),
            listOf(goal(run, GoalKind.MIN, GoalPeriod.DAY, 30)), needsGoalMention = "chạy bộ"),
        Scenario("S3_max_goal_exceeded", 7,
            listOf(rec("g1", 0, TimeCategory.FUN, 120, game), rec("g2", 1, TimeCategory.FUN, 90, game), rec("g3", 2, TimeCategory.FUN, 30, game), rec("r", 1, TimeCategory.HEALTH, 30, run)),
            listOf(goal(game, GoalKind.MAX, GoalPeriod.DAY, 60)), needsGoalMention = "chơi game"),
        Scenario("S4_thin_data", 7, listOf(rec("t", 0, TimeCategory.LEARN, 45, read)), listOf(goal(read, GoalKind.MIN, GoalPeriod.DAY, 20)), expectCaution = true),
        Scenario("S5_improving_week", 7,
            listOf(rec("i1", 6, TimeCategory.HEALTH, 10, run), rec("i2", 4, TimeCategory.HEALTH, 20, run), rec("i3", 2, TimeCategory.HEALTH, 30, run), rec("i4", 0, TimeCategory.HEALTH, 40, run)),
            listOf(goal(run, GoalKind.MIN, GoalPeriod.DAY, 30)), needsGoalMention = "chạy bộ"),
        Scenario("S6_stagnant_week", 7,
            (0..6).map { rec("s$it", it, TimeCategory.HEALTH, if (it % 2 == 0) 0 else 15, run) }.filter { it.durationSeconds > 0 } + listOf(rec("gm", 0, TimeCategory.FUN, 100, game)),
            listOf(goal(run, GoalKind.MIN, GoalPeriod.DAY, 30)), needsGoalMention = "chạy bộ"),
        Scenario("S7_no_goals_same_category", 7,
            listOf(rec("x1", 0, TimeCategory.FUN, 150, game), rec("x2", 1, TimeCategory.FUN, 130, game), rec("x3", 0, TimeCategory.FUN, 30, yt), rec("x4", 1, TimeCategory.FUN, 20, read.copy(category = TimeCategory.FUN)), rec("x5", 2, TimeCategory.FUN, 45, read.copy(category = TimeCategory.FUN))),
            emptyList(), expectBoth = listOf("chơi game", "đọc sách")),
        Scenario("S8_mostly_untagged", 7,
            listOf(rec("u1", 0, TimeCategory.WORK, 240, null), rec("u2", 1, TimeCategory.WORK, 300, null), rec("u3", 1, TimeCategory.LEARN, 30, read)),
            listOf(goal(read, GoalKind.MIN, GoalPeriod.DAY, 20)), needsGoalMention = "đọc sách"),
        Scenario("S9_weekly_goal", 7,
            listOf(rec("w1", 5, TimeCategory.HEALTH, 60, run), rec("w2", 3, TimeCategory.HEALTH, 45, gym), rec("w3", 1, TimeCategory.HEALTH, 30, run)),
            listOf(goal(run, GoalKind.MIN, GoalPeriod.WEEK, 150)), needsGoalMention = "chạy bộ")
    )

    private val judgementWords = listOf("lười", "lãng phí", "vô ích", "thất bại", "đáng trách", "tệ hại", "hư hỏng")
    private val cautionWords = listOf("ít", "mỏng", "thận trọng", "chưa đủ", "mới có", "một ngày", "vài ngày", "chưa nhiều", "hạn chế")

    @Test
    fun coachLiveEval() {
        val keyFile = System.getenv("COACH_EVAL_KEY_FILE")
        val outFile = System.getenv("COACH_EVAL_OUT")
        assumeTrue("COACH_EVAL_KEY_FILE/COACH_EVAL_OUT chưa đặt: bỏ qua (không gọi mạng)", !keyFile.isNullOrBlank() && !outFile.isNullOrBlank())
        val key = ApiKeyHygiene.normalize(File(keyFile!!).readText(Charsets.UTF_8))
        check(ApiKeyHygiene.check(key) == null && key.isNotBlank()) { "credential unusable" }
        val model = System.getenv("COACH_EVAL_MODEL") ?: "cinb/claude-sonnet-4-6"
        val repeat = (System.getenv("COACH_EVAL_REPEAT") ?: "2").toInt()
        val out = File(outFile!!).also { it.parentFile?.mkdirs() }
        var total = 0; var guardOk = 0; var rubricFail = 0; var faults = 0
        for (sc in scenarios()) {
            val digest = CoachDigestBuilder.build(sc.records, tags, sc.goals, sc.days, nowMs, zone)
            for (r in 1..repeat) {
                val t0 = System.nanoTime()
                val raw = runBlocking {
                    GeminiProposalClient.requestCoach(digestText = digest.text, optInEnabled = true, apiKey = key, modelName = model)
                }
                val ms = (System.nanoTime() - t0) / 1_000_000L
                total++
                val check = if (raw.fault == null) CoachGuard.check(raw.story, digest) else CoachCheck.Rejected("fault=${raw.fault}")
                val structuredCheck = if (check is CoachCheck.Ok) {
                    CoachGuard.checkStructured(
                        rawStory = raw.story,
                        rawHeadline = raw.headline,
                        rawHighlights = raw.highlights,
                        rawWatchout = raw.watchout,
                        rawActions = raw.actionItems,
                        digest = digest,
                        tags = tags
                    )
                } else null
                val story = raw.story.orEmpty()
                val folded = story.lowercase()
                val suggestions = CoachGuard.validSuggestions(raw.suggestions, tags, sc.goals)
                val rubric = ArrayList<String>()
                if (raw.fault != null) faults++
                if (check is CoachCheck.Ok && structuredCheck is CoachStructuredCheck.Ok) {
                    guardOk++
                    if (sc.needsGoalMention != null && !folded.contains(sc.needsGoalMention)) rubric += "không nhắc mục tiêu '${sc.needsGoalMention}'"
                    if (sc.expectCaution && cautionWords.none { folded.contains(it) }) rubric += "dữ liệu mỏng nhưng không nêu sự thận trọng"
                    sc.expectBoth.forEach { if (!folded.contains(it)) rubric += "không phân biệt '$it'" }
                    if (judgementWords.any { folded.contains(it) }) rubric += "có từ phán xét"
                    if (CoachGuard.claimsOwnChange(story)) rubric += "khẳng định đã ghi/đổi dữ liệu"
                }
                if (rubric.isNotEmpty()) rubricFail++
                val brief = (structuredCheck as? CoachStructuredCheck.Ok)?.brief
                val line = JSONObject()
                    .put("scenario", sc.id).put("repeat", r).put("model", model).put("latency_ms", ms).put("fault", raw.fault?.name ?: JSONObject.NULL)
                    .put("prompt_tokens", raw.usage?.promptTokens ?: JSONObject.NULL).put("completion_tokens", raw.usage?.completionTokens ?: JSONObject.NULL)
                    .put("guard", if (check is CoachCheck.Ok && structuredCheck is CoachStructuredCheck.Ok) "OK" else (check as? CoachCheck.Rejected)?.reason ?: (structuredCheck as CoachStructuredCheck.Rejected).reason)
                    .put("rubric_failures", rubric.joinToString("; ").ifEmpty { JSONObject.NULL })
                    .put("headline", brief?.headline ?: raw.headline ?: JSONObject.NULL)
                    .put("highlights_count", brief?.highlights?.size ?: raw.highlights.size)
                    .put("action_items_count", brief?.actionItems?.size ?: 0)
                    .put("action_items_titles", brief?.actionItems?.joinToString(" | ") { "${it.kind.storageKey}:${it.title}" } ?: "")
                    .put("suggestions_raw", raw.suggestions.size).put("suggestions_valid", suggestions.size)
                    .put("story_chars", story.length)
                    .put("story", story)
                    .toString()
                out.appendText(line + "\n", Charsets.UTF_8)
                println("[coach] ${sc.id}#$r ${ms}ms fault=${raw.fault} chars=${story.length} actions=${brief?.actionItems?.size ?: 0} guard=${if (check is CoachCheck.Ok && structuredCheck is CoachStructuredCheck.Ok) "OK" else "REJECT"} rubric=${rubric.size}")
                Thread.sleep(500)
            }
        }
        println("[coach] total=$total guardOk=$guardOk rubricFail=$rubricFail faults=$faults")
    }
}
