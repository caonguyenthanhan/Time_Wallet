package com.timewallet.mvp

import com.timewallet.mvp.data.local.TimerAndSettingsStore
import com.timewallet.mvp.data.update.GitHubReleaseUpdateChannel
import com.timewallet.mvp.data.update.PlayStoreUpdateChannel
import com.timewallet.mvp.data.update.UpdateHttpOpener
import com.timewallet.mvp.domain.usecase.ArchivePackageMetadata
import com.timewallet.mvp.domain.usecase.ArchiveTrustDecision
import com.timewallet.mvp.domain.usecase.DistributionChannel
import com.timewallet.mvp.domain.usecase.GitHubReleaseTrustPolicy
import com.timewallet.mvp.domain.usecase.LocalApkVerifier
import com.timewallet.mvp.domain.usecase.SemanticVersion
import com.timewallet.mvp.domain.usecase.UpdateCheckOutcome
import com.timewallet.mvp.domain.usecase.UpdatePreferences
import com.timewallet.mvp.domain.usecase.UpdatePrepareOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest

class V210AutoUpdateTest {

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun sampleReleaseJson(
        tagName: String = "v2.2.0",
        body: String = "Cải tiến đồng hồ & Wally AI\nversionCode: 20",
        draft: Boolean = false,
        prerelease: Boolean = false,
        apkName: String = "TimeWallet-MVP-v2.2.0-release.apk",
        apkUrl: String = "https://github.com/caonguyenthanhan/Time_Wallet/releases/download/v2.2.0/TimeWallet-MVP-v2.2.0-release.apk",
        shaUrl: String = "https://github.com/caonguyenthanhan/Time_Wallet/releases/download/v2.2.0/TimeWallet-MVP-v2.2.0-release.apk.sha256",
        apkSize: Long = 1024L
    ): String = """
        {
          "tag_name": "$tagName",
          "draft": $draft,
          "prerelease": $prerelease,
          "html_url": "https://github.com/caonguyenthanhan/Time_Wallet/releases/tag/$tagName",
          "published_at": "2026-11-22T10:00:00Z",
          "body": ${org.json.JSONObject.quote(body)},
          "assets": [
            {
              "name": "TimeWallet-MVP-v2.2.0-debug.apk",
              "browser_download_url": "https://github.com/caonguyenthanhan/Time_Wallet/releases/download/$tagName/TimeWallet-MVP-v2.2.0-debug.apk",
              "size": 2048
            },
            {
              "name": "$apkName",
              "browser_download_url": "$apkUrl",
              "size": $apkSize
            },
            {
              "name": "$apkName.sha256",
              "browser_download_url": "$shaUrl",
              "size": 128
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `SemanticVersion parses tags and compares releases accurately`() {
        val v20 = SemanticVersion.parse("v2.0.0")!!
        val v21 = SemanticVersion.parse("2.1.0")!!
        val v22rc = SemanticVersion.parse("v2.2.0-rc1")!!
        val v22 = SemanticVersion.parse("v2.2.0")!!
        assertTrue(v21 > v20)
        assertTrue(v22rc > v21)
        assertTrue(v22 > v22rc)
        assertNull(SemanticVersion.parse("invalid-tag"))
    }

    @Test
    fun `GitHubReleaseTrustPolicy selects release APK over debug, rejects drafts and untrusted hosts`() {
        val ok = GitHubReleaseTrustPolicy.parseLatestReleaseJson(
            jsonBody = sampleReleaseJson(),
            currentVersionName = "2.1.0",
            currentVersionCode = 19L,
            checkedAtMs = 1_000L
        )
        assertTrue(ok is UpdateCheckOutcome.Available)
        val cand = (ok as UpdateCheckOutcome.Available).candidate
        assertEquals("TimeWallet-MVP-v2.2.0-release.apk", cand.apkFileName)
        assertEquals(20L, cand.embeddedVersionCode)

        val draftOutcome = GitHubReleaseTrustPolicy.parseLatestReleaseJson(
            jsonBody = sampleReleaseJson(draft = true),
            currentVersionName = "2.1.0",
            currentVersionCode = 19L,
            checkedAtMs = 1_000L
        )
        assertTrue(draftOutcome is UpdateCheckOutcome.UpToDate)

        val evilHost = GitHubReleaseTrustPolicy.parseLatestReleaseJson(
            jsonBody = sampleReleaseJson(apkUrl = "https://evil.example.com/malicious.apk"),
            currentVersionName = "2.1.0",
            currentVersionCode = 19L,
            checkedAtMs = 1_000L
        )
        assertTrue(evilHost is UpdateCheckOutcome.Unavailable)
    }

    @Test
    fun `verifyDownloadedArchive blocks tampered checksum, wrong signing cert even with valid sha256, and downgrade`() {
        val trustedCert = GitHubReleaseTrustPolicy.TRUSTED_RELEASE_CERT_SHA256
        val validSha = "a".repeat(64)
        val meta = ArchivePackageMetadata(
            packageName = GitHubReleaseTrustPolicy.DEFAULT_APP_ID,
            versionName = "2.2.0",
            versionCode = 20L,
            certSha256List = setOf(trustedCert)
        )

        // 1) Valid 3-layer verification + matching installed cert -> data preserved
        val valid = GitHubReleaseTrustPolicy.verifyDownloadedArchive(
            apkFilePath = "/cache/updates/app.apk",
            meta = meta,
            expectedAppId = GitHubReleaseTrustPolicy.DEFAULT_APP_ID,
            currentVersionCode = 19L,
            installedCertSha256 = trustedCert,
            computedApkSha256 = validSha,
            expectedSha256 = validSha
        )
        assertTrue(valid is ArchiveTrustDecision.Valid)
        assertTrue((valid as ArchiveTrustDecision.Valid).summary.matchesInstalledCert)
        assertTrue(valid.summary.matchesTrustedReleaseCert)

        // 2) Both APK and .sha256 swapped with attacker's cert -> REJECTED!
        val swappedBoth = GitHubReleaseTrustPolicy.verifyDownloadedArchive(
            apkFilePath = "/cache/updates/evil.apk",
            meta = meta.copy(certSha256List = setOf("f".repeat(64))),
            expectedAppId = GitHubReleaseTrustPolicy.DEFAULT_APP_ID,
            currentVersionCode = 19L,
            installedCertSha256 = trustedCert,
            computedApkSha256 = validSha,
            expectedSha256 = validSha
        )
        assertTrue(swappedBoth is ArchiveTrustDecision.Rejected)
        assertTrue((swappedBoth as ArchiveTrustDecision.Rejected).securityFailure)

        // 3) Checksum mismatch -> REJECTED!
        val badSha = GitHubReleaseTrustPolicy.verifyDownloadedArchive(
            apkFilePath = "/cache/updates/bad.apk",
            meta = meta,
            expectedAppId = GitHubReleaseTrustPolicy.DEFAULT_APP_ID,
            currentVersionCode = 19L,
            installedCertSha256 = trustedCert,
            computedApkSha256 = validSha,
            expectedSha256 = "b".repeat(64)
        )
        assertTrue(badSha is ArchiveTrustDecision.Rejected)

        // 4) Downgrade -> REJECTED!
        val downgrade = GitHubReleaseTrustPolicy.verifyDownloadedArchive(
            apkFilePath = "/cache/updates/old.apk",
            meta = meta.copy(versionCode = 19L),
            expectedAppId = GitHubReleaseTrustPolicy.DEFAULT_APP_ID,
            currentVersionCode = 19L,
            installedCertSha256 = trustedCert,
            computedApkSha256 = validSha,
            expectedSha256 = validSha
        )
        assertTrue(downgrade is ArchiveTrustDecision.Rejected)
    }

    @Test
    fun `GitHubReleaseUpdateChannel verifies redirects, SHA-256, signing cert, and cleans failed downloads`() = runBlocking {
        val apkBytes = "synthetic-apk-payload-v2.2.0".toByteArray()
        val goodSha = sha256Hex(apkBytes)
        val shaContent = "$goodSha  TimeWallet-MVP-v2.2.0-release.apk\n"
        val cacheDir = Files.createTempDirectory("tw_update_test").toFile()

        try {
            val opener: UpdateHttpOpener = { url ->
                object : HttpURLConnection(url) {
                    override fun disconnect() {}
                    override fun usingProxy(): Boolean = false
                    override fun connect() {}
                    override fun getResponseCode(): Int = when {
                        url.host == "api.github.com" -> 200
                        url.host == "github.com" -> 302
                        else -> 200
                    }
                    override fun getHeaderField(name: String?): String? =
                        if (name.equals("Location", ignoreCase = true) && url.host == "github.com") {
                            if (url.path.endsWith(".sha256")) {
                                "https://objects.githubusercontent.com/assets/release.sha256"
                            } else {
                                "https://objects.githubusercontent.com/assets/release.apk"
                            }
                        } else null
                    override fun getInputStream() = when {
                        url.host == "api.github.com" -> ByteArrayInputStream(sampleReleaseJson(apkSize = apkBytes.size.toLong()).toByteArray())
                        url.path.endsWith(".sha256") -> ByteArrayInputStream(shaContent.toByteArray())
                        else -> ByteArrayInputStream(apkBytes)
                    }
                }
            }

            val channel = GitHubReleaseUpdateChannel(httpOpener = opener, nowMs = { 5_000L })
            val check = channel.checkForUpdate("2.1.0", 19L)
            assertTrue(check is UpdateCheckOutcome.Available)
            val candidate = (check as UpdateCheckOutcome.Available).candidate

            val goodVerifier = object : LocalApkVerifier {
                override fun inspectArchive(apkPath: String) = ArchivePackageMetadata(
                    packageName = GitHubReleaseTrustPolicy.DEFAULT_APP_ID,
                    versionName = "2.2.0",
                    versionCode = 20L,
                    certSha256List = setOf(GitHubReleaseTrustPolicy.TRUSTED_RELEASE_CERT_SHA256)
                )
            }
            val ready = channel.prepareInstall(
                candidate = candidate,
                cacheDirPath = cacheDir.absolutePath,
                expectedAppId = GitHubReleaseTrustPolicy.DEFAULT_APP_ID,
                currentVersionCode = 19L,
                installedCertSha256 = GitHubReleaseTrustPolicy.TRUSTED_RELEASE_CERT_SHA256,
                verifier = goodVerifier
            )
            assertTrue(ready is UpdatePrepareOutcome.ReadyToInstall)
            val apkPath = (ready as UpdatePrepareOutcome.ReadyToInstall).verification.apkFilePath
            assertTrue(File(apkPath).exists())

            // Now test untrusted redirect rejection + cache cleanup
            val evilRedirectOpener: UpdateHttpOpener = { url ->
                object : HttpURLConnection(url) {
                    override fun disconnect() {}
                    override fun usingProxy(): Boolean = false
                    override fun connect() {}
                    override fun getResponseCode(): Int = if (url.host == "github.com") 302 else 200
                    override fun getHeaderField(name: String?): String? =
                        if (name.equals("Location", ignoreCase = true)) "https://evil-cdn.example.net/payload.apk" else null
                    override fun getInputStream() = ByteArrayInputStream(shaContent.toByteArray())
                }
            }
            val blocked = GitHubReleaseUpdateChannel(httpOpener = evilRedirectOpener).prepareInstall(
                candidate = candidate,
                cacheDirPath = cacheDir.absolutePath,
                expectedAppId = GitHubReleaseTrustPolicy.DEFAULT_APP_ID,
                currentVersionCode = 19L,
                installedCertSha256 = GitHubReleaseTrustPolicy.TRUSTED_RELEASE_CERT_SHA256,
                verifier = goodVerifier
            )
            assertTrue(blocked is UpdatePrepareOutcome.Rejected)
            assertTrue((blocked as UpdatePrepareOutcome.Rejected).securityFailure)
            assertEquals(0, File(cacheDir, "updates").listFiles()?.count { it.name.endsWith(".apk") || it.name.endsWith(".part") } ?: 0)
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `PlayStoreUpdateChannel enforces Phương án A separation and UpdatePreferences persist`() = runBlocking {
        val play = PlayStoreUpdateChannel()
        assertEquals(DistributionChannel.GOOGLE_PLAY, play.channel)
        val check = play.checkForUpdate("2.1.0", 19L)
        assertTrue(check is UpdateCheckOutcome.Unavailable)
        assertFalse((check as UpdateCheckOutcome.Unavailable).retryable)

        val dummyCandidate = GitHubReleaseTrustPolicy.parseLatestReleaseJson(
            jsonBody = sampleReleaseJson(),
            currentVersionName = "2.1.0",
            currentVersionCode = 19L,
            checkedAtMs = 1L
        ) as UpdateCheckOutcome.Available
        val redirect = play.prepareInstall(
            candidate = dummyCandidate.candidate,
            cacheDirPath = ".",
            expectedAppId = GitHubReleaseTrustPolicy.DEFAULT_APP_ID,
            currentVersionCode = 19L,
            installedCertSha256 = null,
            verifier = object : LocalApkVerifier {
                override fun inspectArchive(apkPath: String): ArchivePackageMetadata? = null
            }
        )
        assertTrue(redirect is UpdatePrepareOutcome.ChannelRedirect)
        assertNotNull((redirect as UpdatePrepareOutcome.ChannelRedirect).storeUrl)

        val prefs = InMemorySharedPreferences()
        val store = TimerAndSettingsStore(prefs, InMemoryApiKeyVault())
        val saved = UpdatePreferences(
            autoCheckEnabled = false,
            skippedVersionCode = 20L,
            lastCheckEpochMs = 123_456L,
            promptedVersionCode = 21L,
            checkInterval = com.timewallet.mvp.domain.usecase.UpdateCheckInterval.BALANCED_12H
        )
        store.saveUpdatePreferences(saved, immediate = true)
        assertEquals(saved, store.loadUpdatePreferences())

        val smallCustom = com.timewallet.mvp.domain.model.CustomizationSettings(
            fontScale = com.timewallet.mvp.domain.model.FontScalePreference.SMALL
        )
        store.saveCustomizationSettings(smallCustom, immediate = true)
        assertEquals(com.timewallet.mvp.domain.model.FontScalePreference.SMALL, store.loadCustomizationSettings().fontScale)
        assertTrue(com.timewallet.mvp.domain.model.FontScalePreference.SMALL.multiplier < com.timewallet.mvp.domain.model.FontScalePreference.MEDIUM.multiplier)
        assertTrue(com.timewallet.mvp.domain.model.FontScalePreference.MEDIUM.multiplier < com.timewallet.mvp.domain.model.FontScalePreference.LARGE.multiplier)
    }

    @Test
    fun `formatCompactReleaseNotes strips SHA table, code blocks and verbose sections while keeping concise points`() {
        val rawBody = """
            ## Điểm mới v2.1.3
            - **Tự bật pop-up cập nhật** khi có bản mới và chấm đỏ báo **Mới** ở icon cài đặt.
            - Chuẩn hóa **Markdown** gọn, dễ đọc.
            versionCode: 22
            cooldownHours: 2

            ### Tệp cài đặt & SHA-256
            | Tệp | SHA-256 |
            | --- | --- |
            | `TimeWallet-MVP-v2.1.3-release.apk` | `deadbeef` |
        """.trimIndent()
        val parsed = GitHubReleaseTrustPolicy.formatCompactReleaseNotes(rawBody)
        assertTrue(parsed.any { it.isHeading && it.text == "Điểm mới v2.1.3" })
        assertTrue(parsed.any { it.isBullet && it.text.contains("**Tự bật pop-up cập nhật**") })
        assertFalse(parsed.any { it.text.contains("deadbeef") || it.text.contains("versionCode") || it.text.contains("|") })
        assertEquals(2, GitHubReleaseTrustPolicy.parseCooldownHoursHint(rawBody))
    }

    @Test
    fun `effectiveCooldownMs adapts between rapid release and maintenance intervals`() {
        val rapidPrefs = UpdatePreferences(checkInterval = com.timewallet.mvp.domain.usecase.UpdateCheckInterval.RAPID_1H)
        assertEquals(3_600_000L, GitHubReleaseTrustPolicy.effectiveCooldownMs(rapidPrefs))
        val monthlyPrefs = UpdatePreferences(checkInterval = com.timewallet.mvp.domain.usecase.UpdateCheckInterval.MONTHLY_30D)
        assertEquals(30L * 24L * 60L * 60_000L, GitHubReleaseTrustPolicy.effectiveCooldownMs(monthlyPrefs))
    }
}
