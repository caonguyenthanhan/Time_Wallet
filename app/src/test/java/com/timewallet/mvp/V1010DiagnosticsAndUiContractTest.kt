package com.timewallet.mvp

import com.timewallet.mvp.data.diagnostics.CrashRecorder
import com.timewallet.mvp.data.diagnostics.CrashReport
import com.timewallet.mvp.data.diagnostics.CrashReportInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId

class V1010DiagnosticsAndUiContractTest {

    private fun resolveProjectFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("..", relativePath)
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate project file: $relativePath (cwd=${File(".").absolutePath})")
    }

    @Test
    fun crashReportInfo_legacyLogWithoutVersion_displaysUnknownAndNeverFakesV1010() {
        val legacyRaw = """
            time=2026-11-10T01:02:03Z
            exception=java.lang.IllegalStateException: Legacy crash
            stack:
            java.lang.IllegalStateException: Legacy crash
        """.trimIndent()

        val info = CrashReportInfo.parse(legacyRaw)
        assertEquals("2026-11-10T01:02:03Z", info.timeIso)
        assertNull(info.version)
        assertEquals("java.lang.IllegalStateException: Legacy crash", info.exception)

        val header = info.header(ZoneId.of("UTC"))
        assertTrue("Header must contain formatted timestamp: $header", header.contains("10/11/2026 01:02:03"))
        assertTrue(
            "Header must show unknown version label: $header",
            header.contains("Phiên bản khi lỗi: ${CrashReportInfo.UNKNOWN_VERSION_LABEL}")
        )
        assertFalse("Legacy header must never fabricate v1.0.10: $header", header.contains("1.0.10"))
    }

    @Test
    fun crashReportInfo_emptyTimestamp_displaysUnknownTimestampAndVersion() {
        val info = CrashReportInfo.parse("exception=RuntimeException")
        assertNull(info.timeIso)
        assertNull(info.version)

        val header = info.header(ZoneId.of("UTC"))
        assertTrue(header.contains("Thời điểm: ${CrashReportInfo.UNKNOWN_VERSION_LABEL}"))
        assertTrue(header.contains("Phiên bản khi lỗi: ${CrashReportInfo.UNKNOWN_VERSION_LABEL}"))
    }

    @Test
    fun crashReportRedact_recordsVersionAndPreservesCauseChainAndStack() {
        val cause = IllegalArgumentException("Bad synthetic state")
        val error = IllegalStateException("Crash with phone 0912345678", cause)
        val raw = CrashReport.redact(
            t = error,
            now = Instant.parse("2026-11-14T10:15:30Z"),
            appVersion = "1.0.9 (10)"
        )

        assertTrue(raw.contains("time=2026-11-14T10:15:30Z"))
        assertTrue(raw.contains("version=1.0.9 (10)"))
        assertTrue(raw.contains("caused_by=java.lang.IllegalArgumentException"))
        assertTrue(raw.contains("V1010DiagnosticsAndUiContractTest"))
        assertFalse("Exception messages must be redacted", raw.contains("0912345678"))
        assertFalse("Exception messages must be redacted", raw.contains("Bad synthetic state"))

        val parsed = CrashReportInfo.parse(raw)
        assertEquals("1.0.9 (10)", parsed.version)
        val header = parsed.header(ZoneId.of("UTC"))
        assertTrue(header.contains("14/11/2026 10:15:30"))
        assertTrue(header.contains("Phiên bản khi lỗi: 1.0.9 (10)"))
    }

    @Test
    fun crashRecorder_preservesPreviousCrashAcrossNewCrashAndClearsBoth() {
        val tempDir = Files.createTempDirectory("v1010_crash_recorder_").toFile()
        val crashFile = File(tempDir, "last_crash.txt")
        try {
            crashFile.writeText(
                "time=2026-11-10T01:02:03Z\nexception=IllegalStateException: Old crash\n",
                Charsets.UTF_8
            )
            CrashRecorder.install(crashFile, appVersion = "1.0.10 (11)")
            CrashRecorder.record(IllegalStateException("Second crash in v1.0.10"))

            val latest = CrashRecorder.readLast()
            val previous = CrashRecorder.readPrevious()

            assertNotNull(latest)
            assertNotNull(previous)
            assertTrue(latest!!.contains("version=1.0.10 (11)"))
            assertTrue(latest.contains("exception=java.lang.IllegalStateException"))
            assertFalse("Message text must remain redacted", latest.contains("Second crash in v1.0.10"))
            assertTrue(previous!!.contains("Old crash"))

            val prevInfo = CrashReportInfo.parse(previous)
            assertNull(prevInfo.version)

            CrashRecorder.clear()
            assertNull(CrashRecorder.readLast())
            assertNull(CrashRecorder.readPrevious())
        } finally {
            CrashRecorder.clear()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun timeWalletApp_wiresInstalledVersionIntoCrashRecorder() {
        val appSource = resolveProjectFile("app/src/main/java/com/timewallet/mvp/TimeWalletApp.kt")
            .readText(Charsets.UTF_8)
        assertTrue(appSource.contains("PackageInfoCompat.getLongVersionCode"))
        assertTrue(appSource.contains("CrashRecorder.install("))
        assertTrue(appSource.contains("appVersion ="))
    }

    @Test
    fun mainActivityAndDialogs_enforceSingleDialogAndPreserveBudgetEditorDraft() {
        val mainSource = resolveProjectFile("app/src/main/java/com/timewallet/mvp/MainActivity.kt")
            .readText(Charsets.UTF_8)
        val dialogSource = resolveProjectFile("app/src/main/java/com/timewallet/mvp/ui/screens/ComponentsAndDialogs.kt")
            .readText(Charsets.UTF_8)

        assertTrue(mainSource.contains("val activeDeleteKey = pendingBudgetDelete"))
        assertTrue(mainSource.contains("if (activeDeleteKey != null)"))
        assertTrue(mainSource.contains("} else if (state.showBudgetDialog)"))
        assertTrue(mainSource.contains("preservedBudgetDraft"))
        assertTrue(mainSource.contains("onDeleteWithDraft ="))

        assertTrue(dialogSource.contains("data class BudgetEditorDraft("))
        assertTrue(dialogSource.contains("preservedDraft: BudgetEditorDraft? = null"))
        assertTrue(dialogSource.contains("onDeleteWithDraft: ((BudgetEditorDraft) -> Unit)? = null"))
    }

    @Test
    fun uiComponents_preserveLayoutInvariantsForV1010() {
        val dialogSource = resolveProjectFile("app/src/main/java/com/timewallet/mvp/ui/screens/ComponentsAndDialogs.kt")
            .readText(Charsets.UTF_8)

        assertTrue(dialogSource.contains("fun SectionHeader("))
        assertTrue(dialogSource.contains("fun ActivityRecordRow("))
        assertTrue(dialogSource.contains("fun TimeBudgetItemCard("))
        assertTrue(dialogSource.contains("softWrap = false"))
        assertTrue(dialogSource.contains("maxLines = 2"))
        assertTrue(dialogSource.contains("FlowRow("))
    }
}
