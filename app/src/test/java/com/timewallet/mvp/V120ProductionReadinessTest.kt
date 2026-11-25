package com.timewallet.mvp

import com.timewallet.mvp.data.local.ActivityReviewNoteEntity
import com.timewallet.mvp.data.local.AiPreferences
import com.timewallet.mvp.data.local.BackupDatabaseBackend
import com.timewallet.mvp.data.local.BackupParseResult
import com.timewallet.mvp.data.local.BackupRestoreConflictException
import com.timewallet.mvp.data.local.BackupRestoreMode
import com.timewallet.mvp.data.local.CategoryBudgetEntity
import com.timewallet.mvp.data.local.DailyReviewEntity
import com.timewallet.mvp.data.local.GoalEntity
import com.timewallet.mvp.data.local.LedgerBackupRestorer
import com.timewallet.mvp.data.local.LedgerJsonBackupParser
import com.timewallet.mvp.data.local.LedgerJsonExporter
import com.timewallet.mvp.data.local.LedgerRecordEntity
import com.timewallet.mvp.data.local.TagEntity
import com.timewallet.mvp.domain.model.ActivityReviewNote
import com.timewallet.mvp.domain.model.BudgetPeriod
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.DailyReviewRecord
import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.GoalVersionStatus
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.OverlapResolution
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.notifications.CompletionBootReceiver
import com.timewallet.mvp.notifications.CompletionReminderPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class V120ProductionReadinessTest {

    private class InMemoryBackupBackend : BackupDatabaseBackend {
        val records = linkedMapOf<String, LedgerRecordEntity>()
        val tags = linkedMapOf<String, TagEntity>()
        val goals = linkedMapOf<String, GoalEntity>()
        val budgets = linkedMapOf<String, CategoryBudgetEntity>()
        val dailyReviews = linkedMapOf<String, DailyReviewEntity>()
        val activityNotes = linkedMapOf<String, ActivityReviewNoteEntity>()

        override suspend fun <T> runInTransaction(block: suspend () -> T): T {
            val snapRecords = records.toMap()
            val snapTags = tags.toMap()
            val snapGoals = goals.toMap()
            val snapBudgets = budgets.toMap()
            val snapReviews = dailyReviews.toMap()
            val snapNotes = activityNotes.toMap()
            return try {
                block()
            } catch (t: Throwable) {
                records.clear(); records.putAll(snapRecords)
                tags.clear(); tags.putAll(snapTags)
                goals.clear(); goals.putAll(snapGoals)
                budgets.clear(); budgets.putAll(snapBudgets)
                dailyReviews.clear(); dailyReviews.putAll(snapReviews)
                activityNotes.clear(); activityNotes.putAll(snapNotes)
                throw t
            }
        }

        override suspend fun getAllRecords(): List<LedgerRecordEntity> = records.values.toList()
        override suspend fun upsertRecord(entity: LedgerRecordEntity) { records[entity.id] = entity }
        override suspend fun deleteAllRecords() = records.clear()
        override suspend fun getAllTags(): List<TagEntity> = tags.values.toList()
        override suspend fun insertTag(entity: TagEntity) {
            if (tags.values.any { it.nameKey == entity.nameKey }) {
                throw IllegalStateException("UNIQUE constraint failed: tags.nameKey=${entity.nameKey}")
            }
            tags[entity.id] = entity
        }
        override suspend fun deleteAllTags() = tags.clear()
        override suspend fun getAllGoals(): List<GoalEntity> = goals.values.toList()
        override suspend fun upsertGoal(entity: GoalEntity) { goals[entity.id] = entity }
        override suspend fun deleteAllGoals() = goals.clear()
        override suspend fun getAllBudgets(): List<CategoryBudgetEntity> = budgets.values.toList()
        override suspend fun upsertBudget(entity: CategoryBudgetEntity) { budgets[entity.categoryKey] = entity }
        override suspend fun deleteAllBudgets() = budgets.clear()
        override suspend fun getAllDailyReviews(): List<DailyReviewEntity> = dailyReviews.values.toList()
        override suspend fun upsertDailyReview(entity: DailyReviewEntity) { dailyReviews[entity.dateLocal] = entity }
        override suspend fun deleteAllDailyReviews() = dailyReviews.clear()
        override suspend fun getAllActivityNotes(): List<ActivityReviewNoteEntity> = activityNotes.values.toList()
        override suspend fun upsertActivityNotes(entities: List<ActivityReviewNoteEntity>) {
            entities.forEach { activityNotes[it.recordId] = it }
        }
        override suspend fun deleteAllActivityNotes() = activityNotes.clear()
    }

    @Test
    fun `lossless export and REPLACE_ALL roundtrip preserves all v5 fields tags goals budgets and reviews`() = runBlocking {
        val tag = Tag(
            id = "tag-deepwork",
            name = "Đọc tài liệu kỹ thuật",
            nameKey = "doc tai lieu ky thuat",
            category = TimeCategory.LEARN,
            archivedAt = null,
            createdAt = 1_763_000_000_000L,
            updatedAt = 1_763_000_050_000L
        )
        val goal = Goal(
            id = "goal-1",
            groupId = "grp-1",
            targetType = GoalTargetType.TAG,
            targetKey = tag.id,
            kind = GoalKind.MIN,
            period = GoalPeriod.DAY,
            targetSeconds = 3_600L,
            status = GoalVersionStatus.ACTIVE,
            validFrom = "2026-11-16",
            validTo = null,
            createdAt = 1_763_000_010_000L,
            updatedAt = 1_763_000_060_000L
        )
        val recMain = LedgerRecord(
            id = "rec-main",
            source = "chat",
            layer = TimeLayer.REPORTED_TIMED,
            createdAt = 1_763_010_000_000L,
            userConfirmedAt = 1_763_046_000_000L,
            category = TimeCategory.WORK,
            title = "Làm việc",
            date = "2026-11-16",
            durationSeconds = 28_800L,
            startedAt = 1_763_258_400_000L,
            endedAt = 1_763_290_800_000L,
            timezone = "Asia/Ho_Chi_Minh",
            status = "CONFIRMED",
            idempotencyKey = "idem-main-001",
            tagId = null,
            confirmationStateRaw = ConfirmationState.ADJUSTED.name,
            plannedStartedAt = 1_763_254_800_000L,
            plannedEndedAt = 1_763_287_200_000L,
            plannedDurationSeconds = 32_400L,
            overlapResolutionRaw = OverlapResolution.PARALLEL_CONFIRMED.name,
            relationGroupId = "rel-parallel-1",
            overlapWithTitle = "Đọc sách"
        )
        val recParallel = LedgerRecord(
            id = "rec-parallel",
            source = "manual",
            layer = TimeLayer.REPORTED_TIMED,
            createdAt = 1_763_020_000_000L,
            userConfirmedAt = 1_763_046_500_000L,
            category = TimeCategory.LEARN,
            title = "Đọc sách",
            date = "2026-11-16",
            durationSeconds = 3_600L,
            startedAt = 1_763_276_400_000L,
            endedAt = 1_763_280_000_000L,
            timezone = "Asia/Ho_Chi_Minh",
            status = "CONFIRMED",
            idempotencyKey = "idem-parallel-002",
            tagId = tag.id,
            confirmationStateRaw = ConfirmationState.COMPLETED.name,
            plannedStartedAt = null,
            plannedEndedAt = null,
            plannedDurationSeconds = 3_600L,
            overlapResolutionRaw = OverlapResolution.PARALLEL_CONFIRMED.name,
            relationGroupId = "rel-parallel-1",
            overlapWithTitle = "Làm việc"
        )
        val budget = CategoryTimeBudget(
            category = TimeCategory.LEARN,
            period = BudgetPeriod.DAILY,
            targetSeconds = 7_200L,
            isPriority = true,
            priorityOrder = 2,
            updatedAt = 1_763_000_900_000L
        )
        val dailyReview = DailyReviewRecord(
            dateLocal = "2026-11-16",
            summaryText = "Hoàn thành mục tiêu tập trung",
            question1 = "Điều gì hiệu quả nhất?",
            question2 = "Cần chỉnh gì ngày mai?",
            answer1 = "Chặn thông báo lúc sáng.",
            answer2 = "Ngủ sớm hơn.",
            generalNote = "Tốt",
            updatedAt = 1_763_050_000_000L
        )
        val activityNote = ActivityReviewNote(
            recordId = recParallel.id,
            dateLocal = "2026-11-16",
            outcomeNote = "Đọc xong chương 3",
            feynmanExplanation = "Tóm tắt định luật Little bằng ví dụ hàng đợi",
            updatedAt = 1_763_050_123_000L
        )

        val json = LedgerJsonExporter.exportToJsonString(
            records = listOf(recMain, recParallel),
            activeTimer = null,
            exportedAtIso = "2026-11-16T22:00:00Z",
            budgets = listOf(budget),
            dailyReviews = listOf(dailyReview),
            activityNotes = listOf(activityNote),
            tags = listOf(tag),
            goals = listOf(goal)
        )

        val parse = LedgerJsonBackupParser.parse(json)
        assertTrue("Phải parse hợp lệ: $parse", parse is BackupParseResult.Valid)
        val payload = (parse as BackupParseResult.Valid).payload

        val backend = InMemoryBackupBackend()
        // Pre-seed stale data that REPLACE_ALL should completely replace
        backend.upsertRecord(LedgerRecordEntity.fromDomain(recMain.copy(id = "stale-id", idempotencyKey = "stale-key")))
        val summary = LedgerBackupRestorer.restoreWithBackend(backend, payload, BackupRestoreMode.REPLACE_ALL)

        assertEquals(2, summary.recordsInserted)
        assertEquals(1, summary.tagsInserted)
        assertEquals(1, summary.goalsRestored)
        assertEquals(1, summary.budgetsRestored)
        assertEquals(1, summary.dailyReviewsRestored)
        assertEquals(1, summary.activityNotesRestored)

        val restoredMain = backend.records["rec-main"]!!.toDomain()
        assertEquals(recMain, restoredMain)
        val restoredParallel = backend.records["rec-parallel"]!!.toDomain()
        assertEquals(recParallel, restoredParallel)
        assertEquals(tag, backend.tags[tag.id]!!.toDomain())
        assertEquals(goal, backend.goals[goal.id]!!.toDomain())
        assertEquals(budget, backend.budgets[budget.category.key]!!.toDomain())
        assertEquals(dailyReview, backend.dailyReviews["2026-11-16"]!!.toDomain())
        assertEquals(activityNote, backend.activityNotes[recParallel.id]!!.toDomain())
        assertFalse(backend.records.containsKey("stale-id"))
    }

    @Test
    fun `MERGE_SAFE skips duplicates and overlaps while remapping colliding Tag nameKey`() = runBlocking {
        val backend = InMemoryBackupBackend()
        val existingTag = TagEntity(
            id = "tag-release-uuid",
            name = "Chạy bộ",
            nameKey = "chay bo",
            categoryKey = TimeCategory.HEALTH.key,
            archivedAt = null,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        backend.insertTag(existingTag)

        val existingRec = LedgerRecord(
            id = "rec-exist",
            source = "manual",
            layer = TimeLayer.REPORTED_TIMED,
            createdAt = 1000L,
            userConfirmedAt = 2000L,
            category = TimeCategory.WORK,
            title = "Họp sáng",
            date = "2026-11-16",
            durationSeconds = 3600L,
            startedAt = 1_763_258_400_000L, // 09:00
            endedAt = 1_763_262_000_000L,   // 10:00
            timezone = "Asia/Ho_Chi_Minh",
            status = "CONFIRMED",
            idempotencyKey = "idem-shared"
        )
        backend.upsertRecord(LedgerRecordEntity.fromDomain(existingRec))

        val importedBackupJson = """
            {
              "schemaVersion": 1,
              "exportedAt": "2026-11-16T18:00:00Z",
              "items": [
                {
                  "id": "rec-dup",
                  "source": "manual",
                  "layer": "REPORTED_TIMED",
                  "createdAt": 1000,
                  "userConfirmedAt": 2000,
                  "category": "work",
                  "title": "Bản ghi trùng khóa",
                  "date": "2026-11-16",
                  "durationSeconds": 1800,
                  "startedAt": 1763265600000,
                  "endedAt": 1763267400000,
                  "timezone": "Asia/Ho_Chi_Minh",
                  "status": "CONFIRMED",
                  "idempotencyKey": "idem-shared"
                },
                {
                  "id": "rec-overlap",
                  "source": "chat",
                  "layer": "REPORTED_TIMED",
                  "createdAt": 1000,
                  "userConfirmedAt": 2000,
                  "category": "work",
                  "title": "Bị chồng lấn 09:30-10:30",
                  "date": "2026-11-16",
                  "durationSeconds": 3600,
                  "startedAt": 1763260200000,
                  "endedAt": 1763263800000,
                  "timezone": "Asia/Ho_Chi_Minh",
                  "status": "CONFIRMED",
                  "idempotencyKey": "idem-overlap"
                },
                {
                  "id": "rec-new-run",
                  "source": "timer",
                  "layer": "MEASURED",
                  "createdAt": 3000,
                  "userConfirmedAt": 4000,
                  "category": "health",
                  "title": "Chạy công viên",
                  "date": "2026-11-16",
                  "durationSeconds": 2400,
                  "startedAt": 1763269200000,
                  "endedAt": 1763271600000,
                  "timezone": "Asia/Ho_Chi_Minh",
                  "status": "CONFIRMED",
                  "idempotencyKey": "idem-run",
                  "tagId": "tag-debug-uuid"
                }
              ],
              "tags": [
                {
                  "id": "tag-debug-uuid",
                  "name": "Chạy  bộ",
                  "nameKey": "chay bo",
                  "category": "health",
                  "archivedAt": null,
                  "createdAt": 500,
                  "updatedAt": 500
                }
              ],
              "goals": [
                {
                  "id": "goal-run",
                  "groupId": "grp-run",
                  "targetType": "TAG",
                  "target": "tag-debug-uuid",
                  "kind": "MIN",
                  "period": "DAY",
                  "targetSeconds": 1800,
                  "status": "ACTIVE",
                  "validFrom": "2026-11-16",
                  "validTo": null,
                  "createdAt": 500,
                  "updatedAt": 500
                }
              ]
            }
        """.trimIndent()

        val payload = (LedgerJsonBackupParser.parse(importedBackupJson) as BackupParseResult.Valid).payload
        val summary = LedgerBackupRestorer.restoreWithBackend(backend, payload, BackupRestoreMode.MERGE_SAFE)

        assertEquals(1, summary.recordsInserted)
        assertEquals(1, summary.recordsSkippedDuplicate)
        assertEquals(1, summary.recordsSkippedOverlap)
        assertEquals(0, summary.tagsInserted)
        assertEquals(1, summary.tagsRemapped)
        assertEquals(1, summary.goalsRestored)
        assertEquals("tag-release-uuid", backend.records["rec-new-run"]!!.tagId)
        assertEquals("tag-release-uuid", backend.goals["goal-run"]!!.targetKey)
    }

    @Test
    fun `invalid or intra-conflicting JSON never destroys existing data`() = runBlocking {
        assertTrue(LedgerJsonBackupParser.parse("") is BackupParseResult.Invalid)
        assertTrue(LedgerJsonBackupParser.parse("{bad") is BackupParseResult.Invalid)
        assertTrue(LedgerJsonBackupParser.parse("{\"schemaVersion\": 99, \"items\": []}") is BackupParseResult.Invalid)
        assertTrue(LedgerJsonBackupParser.parse("{\"schemaVersion\": 1}") is BackupParseResult.Invalid)
        assertTrue(
            LedgerJsonBackupParser.parse(
                "{\"schemaVersion\":1,\"items\":[{\"id\":\"1\",\"title\":\"A\",\"date\":\"2026-11-16\",\"durationSeconds\":-5}]}"
            ) is BackupParseResult.Invalid
        )

        val conflictingBackup = """
            {
              "schemaVersion": 1,
              "items": [
                {"id":"a","title":"Ca 1","date":"2026-11-16","durationSeconds":3600,"startedAt":1763258400000,"endedAt":1763262000000},
                {"id":"b","title":"Ca 2","date":"2026-11-16","durationSeconds":3600,"startedAt":1763260200000,"endedAt":1763263800000}
              ]
            }
        """.trimIndent()
        val payload = (LedgerJsonBackupParser.parse(conflictingBackup) as BackupParseResult.Valid).payload
        val backend = InMemoryBackupBackend()
        val keeper = LedgerRecordEntity(
            id = "keeper",
            source = "manual",
            layer = "REPORTED_UNTIMED",
            createdAt = 100L,
            userConfirmedAt = 200L,
            category = "work",
            title = "Giữ nguyên",
            date = "2026-11-16",
            durationSeconds = 1800L,
            startedAt = null,
            endedAt = null,
            timezone = "Asia/Ho_Chi_Minh",
            status = "CONFIRMED",
            idempotencyKey = "keeper-key"
        )
        backend.upsertRecord(keeper)

        try {
            LedgerBackupRestorer.restoreWithBackend(backend, payload, BackupRestoreMode.REPLACE_ALL)
            fail("Phải từ chối backup chồng lấn trước khi xóa dữ liệu cũ")
        } catch (_: BackupRestoreConflictException) {
            assertEquals(1, backend.records.size)
            assertEquals("Giữ nguyên", backend.records["keeper"]!!.title)
        }
    }

    @Test
    fun `missed overdue recent PENDING_CONFIRMATION records are detected cleanly for boot recovery`() {
        val now = 1_763_290_800_000L // T
        val base = LedgerRecord(
            id = "r1",
            source = "manual",
            layer = TimeLayer.PLANNED,
            createdAt = now - 7_200_000L,
            userConfirmedAt = now - 7_200_000L,
            category = TimeCategory.WORK,
            title = "Ca chiều",
            date = "2026-11-16",
            durationSeconds = 3600L,
            startedAt = now - 4_200_000L,
            endedAt = now - 600_000L, // qua giờ kết thúc 10 phút trước
            timezone = "Asia/Ho_Chi_Minh",
            status = "PLANNED",
            idempotencyKey = "k1",
            confirmationStateRaw = ConfirmationState.PLANNED.name
        )
        val completed = base.copy(id = "r2", confirmationStateRaw = ConfirmationState.COMPLETED.name, status = "CONFIRMED", layer = TimeLayer.REPORTED_TIMED)
        val tooOld = base.copy(id = "r3", startedAt = now - 100_000_000L, endedAt = now - 95_000_000L)
        val future = base.copy(id = "r4", startedAt = now + 1000L, endedAt = now + 3_600_000L)

        val missed = CompletionReminderPolicy.selectMissedRecentPending(listOf(base, completed, tooOld, future), now)
        assertEquals(listOf("r1"), missed.map { it.id })
        val futureTargets = CompletionReminderPolicy.selectTargets(listOf(base, completed, tooOld, future), now)
        assertEquals(listOf("r4"), futureTargets.map { it.id })
    }

    @Test
    fun `production manifest boot receiver default AI model and ImportJsonDialog contract hold`() {
        assertEquals("cinb/claude-sonnet-4-6", AiPreferences.DEFAULT_AI_MODEL)

        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.RECEIVE_BOOT_COMPLETED"))
        assertTrue(manifest.contains(".notifications.CompletionBootReceiver"))
        assertTrue(manifest.contains("android.intent.action.BOOT_COMPLETED"))
        assertTrue(manifest.contains("android.intent.action.MY_PACKAGE_REPLACED"))
        assertTrue(manifest.contains("android.intent.action.TIMEZONE_CHANGED"))
        assertTrue(manifest.contains("android.intent.action.TIME_SET"))
        assertEquals(4, CompletionBootReceiver.SUPPORTED_ACTIONS.size)

        val dialogs = File("src/main/java/com/timewallet/mvp/ui/screens/ComponentsAndDialogs.kt").readText()
        val importStart = dialogs.indexOf("fun ImportJsonDialog(")
        val aiStart = dialogs.indexOf("fun AiSettingsDialog(")
        assertTrue(importStart > 0 && aiStart > importStart)
        val importBody = dialogs.substring(importStart, aiStart)
        assertTrue(importBody.contains("verticalScroll(rememberScrollState())"))
        assertTrue(importBody.indexOf("verticalScroll") < importBody.indexOf("confirmButton"))

        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("versionCode = 27"))
        assertTrue(gradle.contains("versionName = \"2.2.4\""))
        assertTrue(gradle.contains("isMinifyEnabled = false"))
        assertTrue(gradle.contains("signingConfigs"))
        assertNotNull(gradle.indexOf("assembleRelease").takeIf { it >= 0 })
    }
}
