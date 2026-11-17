package com.timewallet.mvp

import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.GoalGroupState
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.GoalVersionStatus
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.GoalRules
import com.timewallet.mvp.domain.usecase.TagRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** v1.1.0 (B, C; L2): Tag cá nhân và Mục tiêu — quy tắc thuần, kể cả ca bắt buộc "Gym không làm tăng tiến độ Chạy bộ". */
class V110TagAndGoalTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 18)           // thứ Tư
    private val nowMs = LocalDateTime.of(2026, 11, 18, 16, 30).atZone(zone).toInstant().toEpochMilli()

    private fun tag(name: String, cat: TimeCategory, created: Long = 1L, id: String = "tag_" + TagRules.nameKey(name).replace(' ', '_')) =
        TagRules.newTag(TagRules.cleanName(name), TagRules.nameKey(name), cat, created, id)

    private fun rec(id: String, date: LocalDate, cat: TimeCategory, minutes: Int, tagId: String? = null, layer: TimeLayer = TimeLayer.REPORTED_UNTIMED, status: String = "CONFIRMED", confirmedAt: Long = nowMs) =
        LedgerRecord(id, "manual", layer, confirmedAt, confirmedAt, cat, "x", date.toString(), minutes * 60L, null, null, zone.id, status, "k_$id", tagId)

    private val run = tag("Chạy bộ", TimeCategory.HEALTH)
    private val gym = tag("Gym", TimeCategory.HEALTH)
    private val read = tag("Đọc sách", TimeCategory.LEARN)
    private val game = tag("Chơi game", TimeCategory.FUN)

    // ---------- Tag ----------
    @Test
    fun `tag names are folded so duplicates are found across case accents and spaces`() {
        assertEquals("chay bo", TagRules.nameKey("  Chạy   BỘ "))
        val existing = listOf(run)
        assertTrue(TagRules.checkName("chay bo", existing) is TagRules.NameCheck.Duplicate)
        assertTrue(TagRules.checkName("Chạy bộ ", existing) is TagRules.NameCheck.Duplicate)
        assertTrue(TagRules.checkName("Đi bộ", existing) is TagRules.NameCheck.Ok)
        assertTrue(TagRules.checkName("   ", existing) is TagRules.NameCheck.Invalid)
        assertTrue(TagRules.checkName("a".repeat(41), existing) is TagRules.NameCheck.Invalid)
    }

    @Test
    fun `renaming recategorizing archiving and restoring keep the id so history never detaches`() {
        val records = listOf(rec("1", today, TimeCategory.HEALTH, 30, run.id))
        val renamed = TagRules.rename(run, "Chạy bộ sáng", TagRules.nameKey("Chạy bộ sáng"), 5)
        val moved = TagRules.recategorize(renamed, TimeCategory.LIFE, 6)
        val archived = TagRules.archive(moved, 7)
        val restored = TagRules.restore(archived, 8)
        listOf(renamed, moved, archived, restored).forEach { assertEquals(run.id, it.id) }
        assertTrue(archived.isArchived); assertFalse(restored.isArchived)
        assertEquals(1, TagRules.usageCount(run.id, records))
        assertEquals(TimeCategory.HEALTH, records.single().category)   // bản ghi cũ giữ nhóm đã ghi nhận
        // đổi tên sang tên của Tag khác bị chặn, đổi sang chính tên mình thì không
        assertTrue(TagRules.checkName("Gym", listOf(run, gym), excludeId = run.id) is TagRules.NameCheck.Duplicate)
        assertTrue(TagRules.checkName("Chạy bộ", listOf(run, gym), excludeId = run.id) is TagRules.NameCheck.Ok)
    }

    @Test
    fun `an archived tag is offered back instead of a duplicate and is hidden from suggestions and search`() {
        val arch = TagRules.archive(run, 3)
        val check = TagRules.checkName("chay bo", listOf(arch)) as TagRules.NameCheck.Duplicate
        assertTrue(check.existing.isArchived)
        assertTrue(TagRules.suggestions(listOf(arch, gym), emptyList(), nowMs, zone).none { it.id == arch.id })
        assertTrue(TagRules.search(listOf(arch, gym), "chay").isEmpty())
        assertEquals(listOf(arch.id), TagRules.search(listOf(arch, gym), "chay", includeArchived = true).map { it.id })
    }

    @Test
    fun `suggestions put recent tags first then frequent then unused and stay short`() {
        val tags = listOf(run, gym, read, game, tag("Họp", TimeCategory.WORK, created = 9), tag("Vẽ", TimeCategory.FUN, created = 10))
        val old = nowMs - 3L * 86_400_000L
        val records = listOf(
            rec("a", today.minusDays(3), TimeCategory.FUN, 60, game.id, confirmedAt = old),
            rec("b", today.minusDays(2), TimeCategory.LEARN, 30, read.id, confirmedAt = nowMs - 2L * 86_400_000L),
            rec("c", today, TimeCategory.HEALTH, 30, run.id, confirmedAt = nowMs),
            rec("d", today.minusDays(1), TimeCategory.HEALTH, 30, run.id, confirmedAt = nowMs - 86_400_000L)
        )
        val s = TagRules.suggestions(tags, records, nowMs, zone, limit = 5, recentCount = 2)
        assertEquals(5, s.size)
        assertEquals(listOf(run.id, read.id), s.take(2).map { it.id })   // 2 Tag dùng gần nhất
        assertEquals(game.id, s[2].id)                                      // rồi Tag đã dùng nhưng không còn gần
        assertEquals("Tag chưa dùng xếp sau cùng, tạo gần nhất trước", listOf("Vẽ", "Họp"), s.drop(3).map { it.name })
        assertEquals(listOf(read.id), TagRules.search(tags, "doc").map { it.id })
    }

    @Test
    fun `a proposal title is matched to the user's tag deterministically and the tag decides the category`() {
        val tags = listOf(run, gym, read, tag("Chơi", TimeCategory.FUN))
        assertEquals(run.id, TagRules.matchTitle("Chạy bộ", tags)?.id)
        assertEquals(run.id, TagRules.matchTitle("chay bo buoi sang", tags)?.id)
        assertEquals(read.id, TagRules.matchTitle("Đọc sách thiết kế", tags)?.id)
        assertNull(TagRules.matchTitle("Chạy việc vặt", tags))
        assertNull("Tag lưu trữ không dùng để khớp", TagRules.matchTitle("Chạy bộ", listOf(TagRules.archive(run, 1))))
        assertEquals("tên dài nhất thắng", read.id, TagRules.matchTitle("Đọc sách chơi", listOf(tag("Chơi", TimeCategory.FUN), read))?.id)
    }

    // ---------- Goal ----------
    private fun goalRun() = GoalRules.create(GoalTargetType.TAG, run.id, GoalKind.MIN, GoalPeriod.DAY, 30 * 60L, today, 1L, id = "g1", groupId = "grp1")

    @Test
    fun `gym time never advances a running goal while running does`() {
        val g = goalRun()
        val records = listOf(rec("gym", today, TimeCategory.HEALTH, 30, gym.id), rec("run", today, TimeCategory.HEALTH, 12, run.id))
        val p = GoalRules.progress(g, records, today, nowMs, zone)
        assertEquals(12 * 60L, p.usedSeconds)
        assertFalse(p.met); assertEquals(18 * 60L, p.remainingSeconds)
        val onlyGym = GoalRules.progress(g, records.filter { it.id == "gym" }, today, nowMs, zone)
        assertEquals(0L, onlyGym.usedSeconds)
    }

    @Test
    fun `a category goal counts every tag of the category and the max kind reports over and within the limit`() {
        val health = GoalRules.create(GoalTargetType.CATEGORY, TimeCategory.HEALTH.key, GoalKind.MIN, GoalPeriod.DAY, 40 * 60L, today, 1L)
        val records = listOf(rec("gym", today, TimeCategory.HEALTH, 30, gym.id), rec("run", today, TimeCategory.HEALTH, 12, run.id), rec("x", today, TimeCategory.LEARN, 90, read.id))
        assertEquals(42 * 60L, GoalRules.progress(health, records, today, nowMs, zone).usedSeconds)
        assertTrue(GoalRules.progress(health, records, today, nowMs, zone).met)
        val cap = GoalRules.create(GoalTargetType.TAG, game.id, GoalKind.MAX, GoalPeriod.DAY, 60 * 60L, today, 1L)
        val gameRecs = listOf(rec("g", today, TimeCategory.FUN, 120, game.id))
        val p = GoalRules.progress(cap, gameRecs, today, nowMs, zone)
        assertFalse(p.met); assertEquals(60 * 60L, p.overSeconds)
        assertTrue(GoalRules.progress(cap, listOf(rec("g", today, TimeCategory.FUN, 45, game.id)), today, nowMs, zone).met)
    }

    @Test
    fun `planned time does not count and the week runs Monday to Sunday`() {
        val weekly = GoalRules.create(GoalTargetType.TAG, run.id, GoalKind.MIN, GoalPeriod.WEEK, 150 * 60L, today, 1L)
        val monday = LocalDate.of(2026, 11, 16); val lastSunday = LocalDate.of(2026, 11, 15)
        val records = listOf(
            rec("mon", monday, TimeCategory.HEALTH, 60, run.id),
            rec("lastSun", lastSunday, TimeCategory.HEALTH, 90, run.id),
            rec("planned", today, TimeCategory.HEALTH, 45, run.id, layer = TimeLayer.PLANNED, status = "PLANNED")
        )
        val p = GoalRules.progress(weekly, records, today, nowMs, zone)
        assertEquals("2026-11-16", p.periodStart); assertEquals("2026-11-22", p.periodEnd)
        assertEquals(60 * 60L, p.usedSeconds)
    }

    @Test
    fun `editing a goal closes the old version so past days keep the old target`() {
        val open = goalRun().copy(validFrom = "2026-11-10")
        val rows = GoalRules.change(open, today, 5L, newTargetSeconds = 60 * 60L, newId = "g2")
        assertEquals(2, rows.size)
        val closed = rows[0]; val fresh = rows[1]
        assertEquals("2026-11-17", closed.validTo); assertEquals(30 * 60L, closed.targetSeconds)
        assertEquals("2026-11-18", fresh.validFrom); assertNull(fresh.validTo); assertEquals(60 * 60L, fresh.targetSeconds)
        assertEquals("grp1", fresh.groupId)
        val records = listOf(rec("y", today.minusDays(1), TimeCategory.HEALTH, 45, run.id), rec("t", today, TimeCategory.HEALTH, 45, run.id))
        val series = GoalRules.dailySeries(rows, records, today.minusDays(1), today, nowMs, zone)
        assertEquals(2, series.size)
        assertTrue("hôm qua đạt theo mục tiêu CŨ 30p", series[0].met && series[0].goal.targetSeconds == 30 * 60L)
        assertFalse("hôm nay chưa đạt theo mục tiêu MỚI 60p", series[1].met)
    }

    @Test
    fun `a goal created today is edited in place and pause resume end never rewrite the past`() {
        val g = goalRun()
        val inPlace = GoalRules.change(g, today, 5L, newTargetSeconds = 45 * 60L)
        assertEquals(1, inPlace.size); assertEquals(g.id, inPlace[0].id); assertEquals(45 * 60L, inPlace[0].targetSeconds)

        val older = g.copy(validFrom = "2026-11-10")
        val paused = GoalRules.change(older, today, 6L, newStatus = GoalVersionStatus.PAUSED, newId = "gp")
        assertEquals(GoalGroupState.PAUSED, GoalRules.groupState(paused))
        val resumed = GoalRules.change(paused[1], today.plusDays(1), 7L, newStatus = GoalVersionStatus.ACTIVE, newId = "gr")
        assertEquals(GoalGroupState.ACTIVE, GoalRules.groupState(listOf(paused[0]) + resumed))
        val ended = GoalRules.end(older, today, 8L)
        assertEquals("2026-11-17", ended.validTo)
        assertEquals(GoalGroupState.ENDED, GoalRules.groupState(listOf(ended)))
        // dòng thời gian: ngày đã kết thúc không còn được tính, ngày trước đó vẫn còn
        val rec10 = rec("r", LocalDate.of(2026, 11, 12), TimeCategory.HEALTH, 40, run.id)
        val series = GoalRules.dailySeries(listOf(ended), listOf(rec10), LocalDate.of(2026, 11, 12), today, nowMs, zone)
        assertEquals(LocalDate.of(2026, 11, 17), series.last().date)
        assertTrue(series.first().met)
    }

    @Test
    fun `goal targets are bounded and a target without data is zero not a failure`() {
        assertTrue(GoalRules.checkTarget(GoalPeriod.DAY, 30) is GoalRules.Check.Invalid)
        assertTrue(GoalRules.checkTarget(GoalPeriod.DAY, 25 * 3600L) is GoalRules.Check.Invalid)
        assertTrue(GoalRules.checkTarget(GoalPeriod.WEEK, 100 * 3600L) is GoalRules.Check.Ok)
        val p = GoalRules.progress(goalRun(), emptyList(), today, nowMs, zone)
        assertEquals(0L, p.usedSeconds); assertEquals(30 * 60L, p.remainingSeconds)
        assertNotEquals(GoalGroupState.ENDED, GoalRules.groupState(listOf(goalRun())))
    }

    @Test
    fun `summaries list active and paused goals with progress only for active ones`() {
        val g = goalRun()
        val paused = g.copy(id = "g9", groupId = "grp9", targetKey = read.id, status = GoalVersionStatus.PAUSED)
        val s = GoalRules.summaries(listOf(g, paused), emptyList(), { id -> listOf(run, read).firstOrNull { it.id == id }?.name }, today, nowMs, zone)
        assertEquals(2, s.size)
        assertEquals("Chạy bộ", s[0].name); assertTrue(s[0].progress != null)
        assertEquals(GoalGroupState.PAUSED, s[1].state); assertNull(s[1].progress)
        assertTrue(GoalRules.summaries(listOf(g.copy(validTo = "2026-11-17")), emptyList(), { null }, today, nowMs, zone).isEmpty())
    }
}
