package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.GoalGroupState
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.GoalVersionStatus
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TimeCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * v1.1.0 (D) — AI Time Coach: ỨNG DỤNG tính số, AI kể chuyện.
 *
 * [CoachDigestBuilder] dựng khối dữ liệu xác định từ sổ cái + Tag + mục tiêu. Mọi con số mà AI được phép nhắc nằm trong
 * [CoachDigest.allowedMinutes] / [CoachDigest.allowedCounts] / [CoachDigest.allowedDates]; [CoachGuard] bác bỏ câu chuyện có số ngoài tập đó,
 * khẳng định đã ghi/lưu/đổi dữ liệu, phán xét, suy diễn nhân quả hoặc khẳng định "không làm" chỉ vì chưa có bản ghi.
 * [CoachDigest.facts] là phần số liệu do ứng dụng tự viết: luôn hiển thị, kể cả khi AI lỗi hoặc bị guard bác.
 */
data class CoachDigest(
    val text: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val totalDays: Int,
    val daysWithData: Int,
    val allowedMinutes: Set<Int>,
    val allowedCounts: Set<Int>,
    val allowedDates: Set<String>,
    /** Câu số liệu trung lập do ứng dụng viết (không qua AI). */
    val facts: List<String>,
    val hasAnyData: Boolean
)

object CoachDigestBuilder {
    private val dm = DateTimeFormatter.ofPattern("dd/MM")
    const val MAX_TAG_LINES = 12
    const val MAX_GOAL_LINES = 8

    private fun mins(seconds: Long): Int = ((seconds + 30L) / 60L).toInt()

    /** @param days 1 = hôm nay; 7 = 7 ngày gần nhất (gồm hôm nay). */
    fun build(
        records: List<LedgerRecord>,
        tags: List<Tag>,
        goals: List<Goal>,
        days: Int,
        nowEpochMs: Long,
        zone: ZoneId
    ): CoachDigest {
        val n = days.coerceIn(1, 31)
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
        val from = today.minusDays((n - 1).toLong())
        val todayStr = today.toString()
        val tagById = tags.associateBy { it.id }

        // 1) Gom thời gian đã diễn ra theo (ngày, đối tượng).
        data class Key(val date: LocalDate, val label: String, val category: TimeCategory)
        val perKey = HashMap<Key, Long>()
        val dataDays = HashSet<LocalDate>()
        var untaggedSeconds = 0L
        for (r in records) {
            val d = runCatching { LocalDate.parse(r.date) }.getOrNull() ?: continue
            if (d.isBefore(from) || d.isAfter(today)) continue
            val sec = r.actualElapsedSecondsAt(nowEpochMs, todayStr)
            if (sec <= 0L) continue
            dataDays += d
            val tag = r.tagId?.let { tagById[it] }
            val label = tag?.name ?: ("(chưa gắn Tag) " + r.category.displayName)
            if (tag == null) untaggedSeconds += sec
            val k = Key(d, label, r.category)
            perKey[k] = (perKey[k] ?: 0L) + sec
        }

        val allowedMinutes = HashSet<Int>()
        val allowedCounts = HashSet<Int>()
        val allowedDates = HashSet<String>()
        allowedCounts += n
        allowedCounts += dataDays.size
        allowedCounts += (n - dataDays.size).coerceAtLeast(0)
        var d0 = from
        while (!d0.isAfter(today)) { allowedDates += d0.format(dm); d0 = d0.plusDays(1) }

        val facts = ArrayList<String>()
        val sb = StringBuilder()
        sb.append("[DỮ LIỆU ĐÃ ĐƯỢC ỨNG DỤNG TÍNH — chỉ được nhắc các con số có trong khối này]\n")
        sb.append("Kỳ: ${from.format(dm)} → ${today.format(dm)} ($n ngày); có dữ liệu ${dataDays.size}/$n ngày. Hôm nay là ${today.format(dm)}.\n")
        val coverage = when {
            dataDays.isEmpty() -> "Chưa có bản ghi nào trong kỳ này."
            dataDays.size < 3 -> "Dữ liệu còn mỏng (dưới 3 ngày có ghi nhận): nhận xét cần thận trọng."
            dataDays.size < n -> "Có ngày chưa ghi nhận: nhận xét chỉ dựa trên các ngày đã có dữ liệu."
            else -> "Mọi ngày trong kỳ đều có ghi nhận."
        }
        sb.append("Độ đầy đủ: $coverage\n")
        if (untaggedSeconds > 0) {
            val m = mins(untaggedSeconds); allowedMinutes += m
            sb.append("Thời gian chưa gắn Tag trong kỳ: $m phút (không phân biệt được hoạt động cụ thể).\n")
        }

        // 2) Mục tiêu đang áp dụng.
        val groups = goals.groupBy { it.groupId }
        val goalLines = ArrayList<String>()
        for ((_, versions) in groups) {
            if (GoalRules.groupState(versions) != GoalGroupState.ACTIVE) continue
            val open = GoalRules.latest(versions) ?: continue
            if (open.status != GoalVersionStatus.ACTIVE) continue
            val name = GoalRules.displayName(open) { id -> tagById[id]?.name }
            val kindVi = if (open.kind == GoalKind.MIN) "tối thiểu" else "tối đa"
            val perVi = if (open.period == GoalPeriod.DAY) "ngày" else "tuần"
            val tgtMin = mins(open.targetSeconds); allowedMinutes += tgtMin
            val line = StringBuilder("- $name: $kindVi $tgtMin phút/$perVi.")
            if (open.period == GoalPeriod.DAY) {
                val series = GoalRules.dailySeries(versions, records, from, today, nowEpochMs, zone)
                if (series.isNotEmpty()) {
                    val met = series.count { it.met }
                    allowedCounts += met; allowedCounts += series.size
                    line.append(" Theo ngày (phút): ")
                    line.append(series.joinToString("; ") { p ->
                        val m = mins(p.usedSeconds); allowedMinutes += m; allowedDates += p.date.format(dm)
                        "${p.date.format(dm)}=$m"
                    })
                    line.append(". ")
                    line.append(if (open.kind == GoalKind.MIN) "Đạt $met/${series.size} ngày." else "Trong giới hạn $met/${series.size} ngày.")
                }
                val used = GoalRules.progress(open, records, today, nowEpochMs, zone)
                val u = mins(used.usedSeconds); val rem = mins(used.remainingSeconds); val over = mins(used.overSeconds)
                allowedMinutes += u; allowedMinutes += rem; allowedMinutes += over
                line.append(" Hôm nay: $u phút")
                if (open.kind == GoalKind.MIN) line.append(if (used.met) "; đã đạt." else "; còn thiếu $rem phút.")
                else line.append(if (used.met) "; còn trong giới hạn (còn $rem phút)." else "; vượt giới hạn $over phút.")
                facts += if (open.kind == GoalKind.MIN) {
                    if (used.met) "$name hôm nay: ${TimeRange.formatDuration(u)} — đã đạt mục tiêu tối thiểu ${TimeRange.formatDuration(tgtMin)}."
                    else if (u == 0) "$name hôm nay: chưa ghi nhận — mục tiêu tối thiểu ${TimeRange.formatDuration(tgtMin)}."
                    else "$name hôm nay: ${TimeRange.formatDuration(u)} / ${TimeRange.formatDuration(tgtMin)} — còn thiếu ${TimeRange.formatDuration(rem)}."
                } else {
                    if (used.met) "$name hôm nay: ${TimeRange.formatDuration(u)} / giới hạn ${TimeRange.formatDuration(tgtMin)} — còn trong giới hạn."
                    else "$name hôm nay: ${TimeRange.formatDuration(u)} / giới hạn ${TimeRange.formatDuration(tgtMin)} — vượt ${TimeRange.formatDuration(over)}."
                }
            } else {
                val used = GoalRules.progress(open, records, today, nowEpochMs, zone)
                val u = mins(used.usedSeconds); val rem = mins(used.remainingSeconds); val over = mins(used.overSeconds)
                allowedMinutes += u; allowedMinutes += rem; allowedMinutes += over
                allowedDates += LocalDate.parse(used.periodStart).format(dm); allowedDates += LocalDate.parse(used.periodEnd).format(dm)
                line.append(" Tuần này (${LocalDate.parse(used.periodStart).format(dm)}–${LocalDate.parse(used.periodEnd).format(dm)}) đến hiện tại: $u phút")
                line.append(if (open.kind == GoalKind.MIN) (if (used.met) "; đã đạt." else "; còn thiếu $rem phút.") else (if (used.met) "; còn trong giới hạn (còn $rem phút)." else "; vượt giới hạn $over phút."))
                facts += if (open.kind == GoalKind.MIN) "$name tuần này: ${TimeRange.formatDuration(u)} / ${TimeRange.formatDuration(tgtMin)}" + (if (used.met) " — đã đạt." else " — còn thiếu ${TimeRange.formatDuration(rem)}.")
                else "$name tuần này: ${TimeRange.formatDuration(u)} / giới hạn ${TimeRange.formatDuration(tgtMin)}" + (if (used.met) " — còn trong giới hạn." else " — vượt ${TimeRange.formatDuration(over)}.")
            }
            goalLines += line.toString()
        }
        if (goalLines.isEmpty()) sb.append("Mục tiêu: người dùng chưa đặt mục tiêu nào.\n")
        else {
            sb.append("Mục tiêu đang áp dụng:\n")
            goalLines.take(MAX_GOAL_LINES).forEach { sb.append(it).append('\n') }
        }

        // 3) Hoạt động theo Tag/nhãn trong kỳ và hôm nay.
        val totals = perKey.entries.groupBy { it.key.label }.mapValues { (_, es) -> es.sumOf { it.value } to es.first().key.category }
        if (totals.isEmpty()) sb.append("Hoạt động trong kỳ: chưa có.\n")
        else {
            sb.append("Hoạt động trong kỳ (phút, theo Tag; danh mục trong ngoặc):\n")
            totals.entries.sortedByDescending { it.value.first }.take(MAX_TAG_LINES).forEach { (label, v) ->
                val m = mins(v.first); allowedMinutes += m
                sb.append("- $label (${v.second.displayName}): $m\n")
            }
        }
        if (n > 1 && totals.isNotEmpty()) {
            // Theo ngày cho từng Tag: để so sánh nhiều ngày (xu hướng tăng/giảm) mà AI không phải tự tính.
            sb.append("Theo ngày (phút; chỉ các ngày có ghi nhận):\n")
            totals.entries.sortedByDescending { it.value.first }.take(MAX_TAG_LINES).forEach { (label, _) ->
                val perDay = perKey.entries.filter { it.key.label == label }.sortedBy { it.key.date }
                sb.append("- $label: ").append(perDay.joinToString("; ") { e ->
                    val m = mins(e.value); allowedMinutes += m; "${e.key.date.format(dm)}=$m"
                }).append('\n')
            }
        }
        val todayTotals = perKey.entries.filter { it.key.date == today }.groupBy { it.key.label }.mapValues { (_, es) -> es.sumOf { it.value } to es.first().key.category }
        if (n > 1 && todayTotals.isNotEmpty()) {
            sb.append("Hôm nay (phút): ")
            sb.append(todayTotals.entries.sortedByDescending { it.value.first }.take(MAX_TAG_LINES).joinToString("; ") { (label, v) ->
                val m = mins(v.first); allowedMinutes += m; "$label=$m"
            }).append('\n')
        }
        if (todayTotals.isNotEmpty()) {
            facts += "Hôm nay đã ghi nhận: " + todayTotals.entries.sortedByDescending { it.value.first }.take(6).joinToString(", ") { (label, v) -> "$label ${TimeRange.formatDuration(mins(v.first))}" } + "."
        }
        if (n > 1) {
            facts += "Có dữ liệu ${dataDays.size}/$n ngày trong kỳ ${from.format(dm)}–${today.format(dm)}."
        }
        if (dataDays.isEmpty()) facts += "Chưa có bản ghi nào trong kỳ để phân tích."
        return CoachDigest(
            text = sb.toString().trimEnd(),
            fromDate = from, toDate = today, totalDays = n, daysWithData = dataDays.size,
            allowedMinutes = allowedMinutes, allowedCounts = allowedCounts, allowedDates = allowedDates,
            facts = facts, hasAnyData = dataDays.isNotEmpty()
        )
    }
}

/** Kết quả kiểm tra lời kể của AI. */
sealed class CoachCheck {
    object Ok : CoachCheck()
    data class Rejected(val reason: String) : CoachCheck()
}

/** Đề xuất mục tiêu do AI gợi ý — chỉ là THẺ ĐỀ XUẤT chờ người dùng xác nhận, không bao giờ tự tạo. */
data class CoachSuggestion(
    val targetType: GoalTargetType,
    val targetKey: String,
    val targetLabel: String,
    val kind: GoalKind,
    val period: GoalPeriod,
    val targetSeconds: Long
)

object CoachGuard {
    const val MAX_STORY_CHARS = 900

    private val judgement = Rx.ascii("""(?<![a-z])(lang phi|vo ich|luoi|that bai|te hai|dang xau ho|khong nen choi|vo bo|sa doa|nghien)(?![a-z])""")
    private val causal = Rx.ascii("""(?<![a-z])(nguyen nhan|gay ra|dan den|chinh vi|la do |boi vi ban|ket qua cua viec)""")
    // Đoán lý do/cảm xúc/ý định của người dùng (kể cả khi có "có vẻ"): dữ liệu thời gian không nói điều đó.
    private val motive = Rx.ascii("""(?<![a-z])(thu gian|giai tri cua ban|xa stress|giai toa|ban (dang )?(cam thay|met|ap luc|chan|buon|vui)|de nghi ngoi|sau nhung khoi|sau khi (tap trung|lam viec|hoc))""")
    private val negativeClaim = Rx.ascii("""(?<![a-z])(ban (da )?khong (he )?(chay|tap|doc|hoc|lam|danh|thuc hien|dung)|khong he (chay|tap|doc|hoc|lam|danh))""")

    // Coach KHÔNG dùng ReplyGuard.claimsStateChange: "đã ghi nhận 175 phút" là MÔ TẢ dữ liệu người dùng (hợp lệ); chỉ chặn việc chính Wally nói mình đã/sẽ làm thay đổi.
    // Dùng chữ có dấu (không bỏ dấu): "đặt" (đặt mục tiêu) khác "đạt" (đạt mục tiêu).
    private val ownChange = listOf(
        Rx.uni("""(?<![\p{L}])(mình|wally|tôi)\s+(đã|vừa|sẽ)\s+(ghi|lưu|tạo|đặt|cập nhật|xóa|sửa|thêm|thay đổi|điều chỉnh|hủy)(?![\p{L}])"""),
        Rx.uni("""(?<![\p{L}])(đã|vừa)\s+(tạo|đặt|lưu|cập nhật|xóa|sửa|thêm|thay đổi|điều chỉnh|hủy)\s+(cho bạn|giúp bạn|mục tiêu|bản ghi|dữ liệu|kế hoạch|nhắc nhở|vào sổ)"""),
        Rx.uni("""(?<![\p{L}])(đã ghi|đã lưu)\s+(vào|thêm|giúp|cho bạn)"""),
        Rx.uni("""không cần xác nhận|tự động (tạo|đặt|lưu|thêm|cập nhật|xóa|sửa)""")
    )

    /** True nếu lời kể nói chính Wally đã/sẽ ghi, lưu, tạo, đặt, sửa, xóa dữ liệu hoặc mục tiêu. */
    fun claimsOwnChange(text: String): Boolean {
        val t = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC).lowercase()
        return ownChange.any { it.containsMatchIn(t) }
    }

    // "10–20 phút" ⇒ cả hai đầu đều là thời lượng: tách thành "10 phút 20 phút" để mỗi số được đối chiếu với dữ liệu.
    private val durationRange = Rx.uni("""(\d+(?:[.,]\d+)?)\s*[-–]\s*(\d+(?:[.,]\d+)?)\s*(giờ|tiếng|phút|h(?![\p{L}])|p(?![\p{L}]))""", RegexOption.IGNORE_CASE)

    // "h" chỉ là đơn vị khi không dính chữ sau nó ("11 hiện" KHÔNG phải 11 giờ); ngày dạng dd/MM bị tách ra trước khi tìm số đếm.
    private val duration = Rx.uni("""(\d+(?:[.,]\d+)?)\s*(?:giờ|tiếng|h(?![\p{L}]))(?:\s*(\d{1,2})\s*(?:phút|p(?![\p{L}]))?)?|(\d+)\s*(?:phút|p(?![\p{L}]))""", RegexOption.IGNORE_CASE)
    private val dateToken = Rx.uni("""\d{1,2}/\d{1,2}""")
    private val anyNumber = Rx.uni("""\d+""")

    private fun fold(s: String): String = TagRules.nameKey(s)

    /** Số phút của một nhóm khớp [duration] (giờ[ phút] hoặc phút). */
    private fun minutesOf(m: MatchResult): Int {
        val g = m.groupValues
        if (g[3].isNotEmpty()) return g[3].toInt()
        val h = g[1].replace(',', '.').toDoubleOrNull() ?: return -1
        val extra = g[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
        return Math.round(h * 60).toInt() + extra
    }

    fun check(story: String?, digest: CoachDigest): CoachCheck {
        val text = story?.trim().orEmpty()
        if (text.isEmpty()) return CoachCheck.Rejected("Lời kể trống.")
        if (text.length > MAX_STORY_CHARS) return CoachCheck.Rejected("Lời kể quá dài.")
        if (claimsOwnChange(text)) return CoachCheck.Rejected("Khẳng định đã ghi/lưu/đổi dữ liệu.")
        val f = fold(text)
        if (judgement.containsMatchIn(f)) return CoachCheck.Rejected("Có lời phán xét.")
        if (causal.containsMatchIn(f)) return CoachCheck.Rejected("Khẳng định quan hệ nhân quả.")
        if (motive.containsMatchIn(f)) return CoachCheck.Rejected("Đoán lý do/cảm xúc của người dùng.")
        if (negativeClaim.containsMatchIn(f)) return CoachCheck.Rejected("Khẳng định người dùng không làm vì chưa có bản ghi.")
        // Số: mọi thời lượng phải thuộc tập phút của dữ liệu; ngày/đếm phải thuộc tập tương ứng; không có số lạ.
        var rest = durationRange.replace(text) { m -> "${m.groupValues[1]} ${m.groupValues[3]} ${m.groupValues[2]} ${m.groupValues[3]}" }
        val expanded = rest
        for (m in duration.findAll(expanded).toList().reversed()) {
            val v = minutesOf(m)
            if (v < 0 || v !in digest.allowedMinutes) return CoachCheck.Rejected("Số liệu thời lượng ngoài dữ liệu.")
            rest = rest.replaceRange(m.range, " ")
        }
        for (m in dateToken.findAll(rest).toList().reversed()) {
            val parts = m.value.split('/')
            val norm = "%02d/%02d".format(parts[0].toInt(), parts[1].toInt())
            if (norm !in digest.allowedDates && !(parts[0].toInt() in digest.allowedCounts && parts[1].toInt() in digest.allowedCounts)) {
                return CoachCheck.Rejected("Ngày/phân số ngoài dữ liệu.")
            }
            rest = rest.replaceRange(m.range, " ")
        }
        for (m in anyNumber.findAll(rest)) {
            val v = m.value.toIntOrNull() ?: return CoachCheck.Rejected("Số không đọc được.")
            // Số trần chỉ hợp lệ khi chính là một số đếm hoặc một số phút CÓ trong dữ liệu ("đi từ 10 lên 20, 30 rồi 40 phút").
            if (v !in digest.allowedCounts && v !in digest.allowedMinutes) return CoachCheck.Rejected("Số liệu ngoài dữ liệu.")
        }
        return CoachCheck.Ok
    }

    /** Lọc đề xuất mục tiêu của AI: đúng Tag/Category có thật, mức hợp lệ, không trùng mục tiêu đang chạy; tối đa 2. */
    fun validSuggestions(
        raw: List<Map<String, Any?>>,
        tags: List<Tag>,
        goals: List<Goal>
    ): List<CoachSuggestion> {
        val activeGoals = goals.groupBy { it.groupId }.values
            .filter { GoalRules.groupState(it) == GoalGroupState.ACTIVE }.mapNotNull { GoalRules.latest(it) }
        val out = ArrayList<CoachSuggestion>()
        for (s in raw) {
            if (out.size >= 2) break
            val type = (s["targetType"] as? String)?.trim()?.lowercase()
            val target = (s["target"] as? String)?.trim().orEmpty()
            val kind = GoalKind.entries.firstOrNull { it.storageKey == (s["kind"] as? String)?.trim()?.uppercase() } ?: continue
            val period = GoalPeriod.entries.firstOrNull { it.storageKey == (s["period"] as? String)?.trim()?.uppercase() } ?: continue
            val minutes = (s["minutes"] as? Number)?.toInt() ?: continue
            val seconds = minutes * 60L
            if (GoalRules.checkTarget(period, seconds) is GoalRules.Check.Invalid) continue
            val (tType, key, label) = when (type) {
                "tag" -> {
                    val t = tags.firstOrNull { !it.isArchived && it.nameKey == TagRules.nameKey(target) } ?: continue
                    Triple(GoalTargetType.TAG, t.id, t.name)
                }
                "category" -> {
                    val c = TimeCategory.fromKeyOrNull(target.lowercase())
                        ?: TimeCategory.entries.firstOrNull { TagRules.nameKey(it.displayName) == TagRules.nameKey(target) }
                        ?: continue
                    Triple(GoalTargetType.CATEGORY, c.key, c.displayName)
                }
                else -> continue
            }
            if (activeGoals.any { it.targetType == tType && it.targetKey == key && it.kind == kind && it.period == period }) continue
            out += CoachSuggestion(tType, key, label, kind, period, seconds)
        }
        return out
    }
}
