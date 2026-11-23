package com.timewallet.mvp.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.timewallet.mvp.domain.usecase.AppUpdateChannel
import com.timewallet.mvp.domain.usecase.ArchivePackageMetadata
import com.timewallet.mvp.domain.usecase.ArchiveTrustDecision
import com.timewallet.mvp.domain.usecase.DistributionChannel
import com.timewallet.mvp.domain.usecase.GitHubReleaseTrustPolicy
import com.timewallet.mvp.domain.usecase.LocalApkVerifier
import com.timewallet.mvp.domain.usecase.UpdateCheckOutcome
import com.timewallet.mvp.domain.usecase.UpdatePrepareOutcome
import com.timewallet.mvp.domain.usecase.VerifiedReleaseCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

typealias UpdateHttpOpener = (URL) -> HttpURLConnection

class UntrustedUpdateSourceException(message: String) : SecurityException(message)

/**
 * Triển khai kênh cập nhật qua GitHub Releases:
 * - Tắt tự động chuyển hướng HTTP của HttpURLConnection và tự xác thực từng bước Location trên allowlist HTTPS GitHub.
 * - Kiểm tra đầy đủ 3 lớp trước khi mở trình cài đặt Android (nguồn metadata, SHA-256, chữ ký APK).
 */
class GitHubReleaseUpdateChannel(
    private val metadataUrl: String = GitHubReleaseTrustPolicy.DEFAULT_METADATA_URL,
    private val httpOpener: UpdateHttpOpener = { it.openConnection() as HttpURLConnection },
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val trustedCerts: Set<String> = GitHubReleaseTrustPolicy.DEFAULT_TRUSTED_CERTS
) : AppUpdateChannel {

    override val channel: DistributionChannel = DistributionChannel.GITHUB_DIRECT

    override suspend fun checkForUpdate(
        currentVersionName: String,
        currentVersionCode: Long
    ): UpdateCheckOutcome = withContext(Dispatchers.IO) {
        if (!GitHubReleaseTrustPolicy.isTrustedHttpsUrl(metadataUrl, GitHubReleaseTrustPolicy.ALLOWED_METADATA_HOSTS)) {
            return@withContext UpdateCheckOutcome.Unavailable(
                "Địa chỉ kiểm tra bản phát hành không hợp lệ.",
                retryable = false
            )
        }
        try {
            val body = readTextBounded(
                initialUrl = metadataUrl,
                allowedHosts = GitHubReleaseTrustPolicy.ALLOWED_METADATA_HOSTS,
                maxBytes = 512 * 1024,
                acceptHeader = "application/vnd.github+json"
            )
            GitHubReleaseTrustPolicy.parseLatestReleaseJson(
                jsonBody = body,
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode,
                checkedAtMs = nowMs(),
                channel = channel
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: UntrustedUpdateSourceException) {
            UpdateCheckOutcome.Unavailable(e.message ?: "Nguồn kiểm tra phiên bản không đáng tin cậy.", retryable = false)
        } catch (_: HttpStatusException) {
            UpdateCheckOutcome.Unavailable("GitHub Releases tạm thời không phản hồi. Ứng dụng vẫn hoạt động bình thường.", retryable = true)
        } catch (_: Exception) {
            UpdateCheckOutcome.Unavailable("Không thể kiểm tra bản cập nhật lúc này (mạng không ổn định hoặc ngoại tuyến).", retryable = true)
        }
    }

    override suspend fun prepareInstall(
        candidate: VerifiedReleaseCandidate,
        cacheDirPath: String,
        expectedAppId: String,
        currentVersionCode: Long,
        installedCertSha256: String?,
        verifier: LocalApkVerifier,
        onProgress: (Int) -> Unit
    ): UpdatePrepareOutcome = withContext(Dispatchers.IO) {
        val updatesDir = File(cacheDirPath, "updates").apply { mkdirs() }
        // Dọn tệp APK tải dở hoặc cũ trước đó trong cache updates/.
        updatesDir.listFiles()?.forEach { old ->
            if (old.isFile && (old.name.endsWith(".apk") || old.name.endsWith(".part"))) {
                old.delete()
            }
        }
        val safeName = GitHubReleaseTrustPolicy.sanitizeSafeFileName(candidate.apkFileName)
        val partFile = File(updatesDir, "$safeName.part")
        val finalFile = File(updatesDir, safeName)

        try {
            if (!GitHubReleaseTrustPolicy.isTrustedHttpsUrl(candidate.sha256Url, GitHubReleaseTrustPolicy.ALLOWED_DOWNLOAD_HOSTS) ||
                !GitHubReleaseTrustPolicy.isTrustedHttpsUrl(candidate.apkUrl, GitHubReleaseTrustPolicy.ALLOWED_DOWNLOAD_HOSTS)
            ) {
                return@withContext UpdatePrepareOutcome.Rejected(
                    "URL tải xuống không thuộc máy chủ GitHub đáng tin cậy.",
                    securityFailure = true
                )
            }

            onProgress(2)
            val shaBody = readTextBounded(
                initialUrl = candidate.sha256Url,
                allowedHosts = GitHubReleaseTrustPolicy.ALLOWED_DOWNLOAD_HOSTS,
                maxBytes = GitHubReleaseTrustPolicy.MAX_SHA256_BODY_CHARS,
                acceptHeader = "text/plain"
            )
            val expectedSha = GitHubReleaseTrustPolicy.parseExpectedSha256(shaBody, candidate.apkFileName)
                ?: return@withContext UpdatePrepareOutcome.Rejected(
                    "Không tìm thấy checksum SHA-256 khớp với ${candidate.apkFileName}.",
                    securityFailure = true
                )

            val computedSha = downloadAndHashApk(
                initialUrl = candidate.apkUrl,
                destPartFile = partFile,
                expectedSizeBytes = candidate.apkSizeBytes,
                onProgress = onProgress
            )

            ensureActive()
            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                partFile.delete()
                return@withContext UpdatePrepareOutcome.Rejected("Không thể lưu APK vào bộ nhớ đệm cài đặt.")
            }

            val meta = verifier.inspectArchive(finalFile.absolutePath)
            when (val decision = GitHubReleaseTrustPolicy.verifyDownloadedArchive(
                apkFilePath = finalFile.absolutePath,
                meta = meta,
                expectedAppId = expectedAppId,
                currentVersionCode = currentVersionCode,
                installedCertSha256 = installedCertSha256,
                computedApkSha256 = computedSha,
                expectedSha256 = expectedSha,
                trustedCerts = trustedCerts
            )) {
                is ArchiveTrustDecision.Valid -> {
                    onProgress(100)
                    UpdatePrepareOutcome.ReadyToInstall(candidate, decision.summary)
                }
                is ArchiveTrustDecision.Rejected -> {
                    finalFile.delete()
                    UpdatePrepareOutcome.Rejected(decision.reasonVi, securityFailure = decision.securityFailure)
                }
            }
        } catch (e: CancellationException) {
            partFile.delete()
            finalFile.delete()
            throw e
        } catch (e: UntrustedUpdateSourceException) {
            partFile.delete()
            finalFile.delete()
            UpdatePrepareOutcome.Rejected(
                e.message ?: "Nguồn chuyển hướng tải APK không đáng tin cậy.",
                securityFailure = true
            )
        } catch (_: Exception) {
            partFile.delete()
            finalFile.delete()
            UpdatePrepareOutcome.Rejected("Tải xuống bản cập nhật thất bại. Hãy kiểm tra mạng và thử lại sau.")
        }
    }

    private fun readTextBounded(
        initialUrl: String,
        allowedHosts: Set<String>,
        maxBytes: Int,
        acceptHeader: String
    ): String {
        val conn = openTrustedGetConnection(initialUrl, allowedHosts, acceptHeader)
        return try {
            val sb = StringBuilder()
            InputStreamReader(BufferedInputStream(conn.inputStream), Charsets.UTF_8).use { reader ->
                val buf = CharArray(2048)
                var totalChars = 0
                while (true) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    totalChars += n
                    if (totalChars > maxBytes) {
                        throw IllegalStateException("Phản hồi vượt quá giới hạn an toàn.")
                    }
                    sb.append(buf, 0, n)
                }
            }
            sb.toString()
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadAndHashApk(
        initialUrl: String,
        destPartFile: File,
        expectedSizeBytes: Long,
        onProgress: (Int) -> Unit
    ): String {
        val conn = openTrustedGetConnection(initialUrl, GitHubReleaseTrustPolicy.ALLOWED_DOWNLOAD_HOSTS, "application/octet-stream")
        return try {
            val headerLen = conn.contentLengthLong
            val totalExpected = when {
                headerLen > 0L -> headerLen
                expectedSizeBytes > 0L -> expectedSizeBytes
                else -> -1L
            }
            if (totalExpected > GitHubReleaseTrustPolicy.MAX_APK_BYTES) {
                throw SecurityException("APK vượt quá giới hạn dung lượng.")
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var lastReportedPct = 2

            BufferedInputStream(conn.inputStream).use { input ->
                FileOutputStream(destPartFile).use { output ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        downloaded += n
                        if (downloaded > GitHubReleaseTrustPolicy.MAX_APK_BYTES) {
                            throw SecurityException("APK tải vượt quá giới hạn dung lượng an toàn.")
                        }
                        digest.update(buf, 0, n)
                        output.write(buf, 0, n)
                        if (totalExpected > 0L) {
                            val pct = ((downloaded * 96L) / totalExpected).toInt().coerceIn(2, 98)
                            if (pct >= lastReportedPct + 2) {
                                lastReportedPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                    output.flush()
                }
            }
            if (downloaded <= 0L) throw IllegalStateException("Tệp APK tải về rỗng.")
            digest.digest().joinToString("") { "%02x".format(it) }
        } finally {
            conn.disconnect()
        }
    }

    private fun openTrustedGetConnection(
        initialUrl: String,
        allowedHosts: Set<String>,
        acceptHeader: String,
        maxHops: Int = 5
    ): HttpURLConnection {
        var currentUrl = initialUrl
        for (hop in 0..maxHops) {
            if (!GitHubReleaseTrustPolicy.isTrustedHttpsUrl(currentUrl, allowedHosts)) {
                throw UntrustedUpdateSourceException("Chặn chuyển hướng tới nguồn không nằm trong danh sách tin cậy GitHub.")
            }
            val urlObj = URL(currentUrl)
            val conn = httpOpener(urlObj).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 10_000
                readTimeout = 25_000
                setRequestProperty("Accept", acceptHeader)
                setRequestProperty("User-Agent", "TimeWallet-Android-Updater")
            }
            val code = conn.responseCode
            if (code in 301..308) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) throw HttpStatusException(code)
                val resolved = URL(urlObj, loc).toString()
                if (!GitHubReleaseTrustPolicy.isTrustedHttpsUrl(resolved, GitHubReleaseTrustPolicy.ALLOWED_DOWNLOAD_HOSTS)) {
                    throw UntrustedUpdateSourceException("Từ chối chuyển hướng tải APK ra ngoài tên miền GitHub chính thức.")
                }
                currentUrl = resolved
                continue
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw HttpStatusException(code)
            }
            return conn
        }
        throw IllegalStateException("Quá nhiều lần chuyển hướng.")
    }

    private class HttpStatusException(val statusCode: Int) : RuntimeException("HTTP $statusCode")
}

/**
 * Kênh Google Play (Phương án A): không cho phép tải/cài APK ngoài từ GitHub;
 * điều hướng về Play In-App Updates / trang Google Play chính thức.
 */
class PlayStoreUpdateChannel(
    private val appId: String = GitHubReleaseTrustPolicy.DEFAULT_APP_ID
) : AppUpdateChannel {

    override val channel: DistributionChannel = DistributionChannel.GOOGLE_PLAY

    override suspend fun checkForUpdate(
        currentVersionName: String,
        currentVersionCode: Long
    ): UpdateCheckOutcome = UpdateCheckOutcome.Unavailable(
        reasonVi = "Bản cài từ Google Play cập nhật thông qua Google Play In-App Updates, không tải APK trực tiếp từ GitHub.",
        retryable = false
    )

    override suspend fun prepareInstall(
        candidate: VerifiedReleaseCandidate,
        cacheDirPath: String,
        expectedAppId: String,
        currentVersionCode: Long,
        installedCertSha256: String?,
        verifier: LocalApkVerifier,
        onProgress: (Int) -> Unit
    ): UpdatePrepareOutcome = UpdatePrepareOutcome.ChannelRedirect(
        instructionsVi = "Kênh Google Play sử dụng Play Store / AppUpdateManager để cập nhật an toàn.",
        storeUrl = "https://play.google.com/store/apps/details?id=$appId"
    )
}

object UpdateChannelResolver {
    fun create(channel: DistributionChannel): AppUpdateChannel = when (channel) {
        DistributionChannel.GITHUB_DIRECT -> GitHubReleaseUpdateChannel()
        DistributionChannel.GOOGLE_PLAY -> PlayStoreUpdateChannel()
    }
}

class AndroidApkVerifier(private val context: Context) : LocalApkVerifier {

    override fun inspectArchive(apkPath: String): ArchivePackageMetadata? {
        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val info: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkPath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkPath, flags)
            } ?: return null

            ArchivePackageMetadata(
                packageName = info.packageName ?: return null,
                versionName = info.versionName.orEmpty(),
                versionCode = PackageInfoCompat.getLongVersionCode(info),
                certSha256List = extractCertSha256(info)
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun readInstalledCertSha256(): String? {
        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val info: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, flags)
            }
            extractCertSha256(info).firstOrNull()
        } catch (_: Throwable) {
            null
        }
    }

    fun readInstalledVersion(): Pair<String, Long> {
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            (info.versionName.orEmpty().ifBlank { "2.2.1" }) to PackageInfoCompat.getLongVersionCode(info)
        } catch (_: Throwable) {
            "2.2.1" to 24L
        }
    }

    companion object {
        fun extractCertSha256(info: PackageInfo): Set<String> {
            val md = MessageDigest.getInstance("SHA-256")
            val sigBytesList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = info.signingInfo
                val sigs = when {
                    signingInfo == null -> emptyArray()
                    signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                    else -> signingInfo.signingCertificateHistory
                }
                sigs?.map { it.toByteArray() }.orEmpty()
            } else {
                @Suppress("DEPRECATION")
                info.signatures?.map { it.toByteArray() }.orEmpty()
            }
            return sigBytesList.map { bytes ->
                md.reset()
                md.digest(bytes).joinToString("") { "%02x".format(Locale.US, it) }
            }.toSet()
        }
    }
}

object AndroidApkInstallerLauncher {

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun buildUnknownSourcesPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun buildInstallIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
