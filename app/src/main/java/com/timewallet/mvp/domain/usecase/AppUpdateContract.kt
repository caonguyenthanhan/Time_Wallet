package com.timewallet.mvp.domain.usecase

import org.json.JSONObject
import java.net.URI
import java.util.Locale

/**
 * Kênh phân phối ứng dụng (Phương án A):
 * Dùng chung hợp đồng cập nhật nhưng tách cơ chế thực thi giữa bản cài trực tiếp từ GitHub và Google Play.
 */
enum class DistributionChannel(val storageKey: String, val labelVi: String) {
    GITHUB_DIRECT("github_direct", "GitHub Releases (Cài trực tiếp APK)"),
    GOOGLE_PLAY("google_play", "Google Play (Play In-App Updates)")
}

enum class UpdateCheckInterval(
    val storageKey: String,
    val labelVi: String,
    val shortLabel: String,
    val intervalMs: Long
) {
    RAPID_1H("rapid_1h", "Giai đoạn tối ưu (1 giờ/lần)", "1 giờ", 1L * 60L * 60_000L),
    BALANCED_12H("balanced_12h", "Bình thường (12 giờ/lần)", "12 giờ", 12L * 60L * 60_000L),
    MAINTENANCE_7D("maintenance_7d", "Bảo trì (7 ngày/lần)", "7 ngày", 7L * 24L * 60L * 60_000L),
    MONTHLY_30D("monthly_30d", "Ổn định (30 ngày/lần)", "30 ngày", 30L * 24L * 60L * 60_000L);

    companion object {
        fun fromStorageKey(raw: String?): UpdateCheckInterval =
            entries.firstOrNull { it.storageKey.equals(raw?.trim(), ignoreCase = true) } ?: RAPID_1H
    }
}

data class UpdatePreferences(
    val autoCheckEnabled: Boolean = true,
    val skippedVersionCode: Long = 0L,
    val lastCheckEpochMs: Long = 0L,
    val promptedVersionCode: Long = 0L,
    val checkInterval: UpdateCheckInterval = UpdateCheckInterval.RAPID_1H
)

data class ReleaseMarkdownLine(
    val text: String,
    val isHeading: Boolean = false,
    val isBullet: Boolean = false
)

data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        return when {
            preRelease == null && other.preRelease == null -> 0
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> preRelease.compareTo(other.preRelease, ignoreCase = true)
        }
    }

    override fun toString(): String = buildString {
        append(major).append('.').append(minor).append('.').append(patch)
        if (!preRelease.isNullOrBlank()) append('-').append(preRelease)
    }

    companion object {
        fun parse(raw: String?): SemanticVersion? {
            val cleaned = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val withoutV = if (cleaned[0] == 'v' || cleaned[0] == 'V') cleaned.substring(1) else cleaned
            if (withoutV.isEmpty()) return null
            val dashIdx = withoutV.indexOf('-')
            val corePart = if (dashIdx >= 0) withoutV.substring(0, dashIdx) else withoutV
            val prePart = if (dashIdx >= 0) withoutV.substring(dashIdx + 1).takeIf { it.isNotEmpty() } else null
            if (prePart != null && !prePart.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' || it == '-' }) {
                return null
            }
            val segments = corePart.split('.')
            if (segments.size !in 2..3 || segments.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
            val major = segments[0].toIntOrNull() ?: return null
            val minor = segments[1].toIntOrNull() ?: return null
            val patch = segments.getOrNull(2)?.toIntOrNull() ?: 0
            return SemanticVersion(major, minor, patch, prePart)
        }
    }
}

data class ArchivePackageMetadata(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val certSha256List: Set<String>
)

interface LocalApkVerifier {
    fun inspectArchive(apkPath: String): ArchivePackageMetadata?
}

data class VerifiedReleaseCandidate(
    val tagName: String,
    val versionName: String,
    val embeddedVersionCode: Long?,
    val releaseNotes: String,
    val apkUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val sha256Url: String,
    val htmlUrl: String,
    val publishedAtIso: String?,
    val recommendedCooldownHours: Int? = null
)

data class ApkVerificationSummary(
    val apkFilePath: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val apkSha256: String,
    val signingCertSha256: String,
    val matchesTrustedReleaseCert: Boolean,
    val matchesInstalledCert: Boolean
)

sealed class UpdateCheckOutcome {
    data class UpToDate(
        val currentVersionName: String,
        val currentVersionCode: Long,
        val checkedAtMs: Long
    ) : UpdateCheckOutcome()

    data class Available(
        val candidate: VerifiedReleaseCandidate,
        val channel: DistributionChannel
    ) : UpdateCheckOutcome()

    data class Unavailable(
        val reasonVi: String,
        val retryable: Boolean = true
    ) : UpdateCheckOutcome()
}

sealed class UpdatePrepareOutcome {
    data class ReadyToInstall(
        val candidate: VerifiedReleaseCandidate,
        val verification: ApkVerificationSummary
    ) : UpdatePrepareOutcome()

    data class Rejected(
        val reasonVi: String,
        val securityFailure: Boolean = false
    ) : UpdatePrepareOutcome()

    data class ChannelRedirect(
        val instructionsVi: String,
        val storeUrl: String?
    ) : UpdatePrepareOutcome()
}

sealed class ArchiveTrustDecision {
    data class Valid(val summary: ApkVerificationSummary) : ArchiveTrustDecision()
    data class Rejected(val reasonVi: String, val securityFailure: Boolean) : ArchiveTrustDecision()
}

/**
 * Hợp đồng chung cho mọi kênh cập nhật. UI & ViewModel chỉ phụ thuộc vào hợp đồng này,
 * không gắn cứng logic tải/cài APK trực tiếp vào tầng giao diện.
 */
interface AppUpdateChannel {
    val channel: DistributionChannel

    suspend fun checkForUpdate(
        currentVersionName: String,
        currentVersionCode: Long
    ): UpdateCheckOutcome

    suspend fun prepareInstall(
        candidate: VerifiedReleaseCandidate,
        cacheDirPath: String,
        expectedAppId: String,
        currentVersionCode: Long,
        installedCertSha256: String?,
        verifier: LocalApkVerifier,
        onProgress: (Int) -> Unit = {}
    ): UpdatePrepareOutcome
}

/**
 * Chính sách tin cậy 3 lớp cho kênh GitHub Releases:
 * 1) Nguồn metadata & asset tải xuống phải thuộc danh sách HTTPS host hợp lệ của GitHub (không chuyển hướng tùy tiện).
 * 2) APK tải về phải khớp checksum SHA-256 tham chiếu trong release.
 * 3) APK phải vượt qua xác minh chữ ký số (signing certificate), đúng applicationId và versionCode cao hơn bản đang cài.
 */
object GitHubReleaseTrustPolicy {
    const val DEFAULT_APP_ID = "com.timewallet.mvp"
    const val DEFAULT_REPO_OWNER = "caonguyenthanhan"
    const val DEFAULT_REPO_NAME = "Time_Wallet"
    const val DEFAULT_METADATA_URL = "https://api.github.com/repos/$DEFAULT_REPO_OWNER/$DEFAULT_REPO_NAME/releases/latest"
    const val DEFAULT_RELEASES_PAGE_URL = "https://github.com/$DEFAULT_REPO_OWNER/$DEFAULT_REPO_NAME/releases"
    const val TRUSTED_RELEASE_CERT_SHA256 = "39f5712d139124e05d550458180e69a1919ade5a331d2eadbb9de47da79cdcd0"
    const val MAX_APK_BYTES = 100L * 1024L * 1024L
    const val MAX_SHA256_BODY_CHARS = 16 * 1024
    const val AUTO_CHECK_COOLDOWN_MS = 12L * 60L * 60_000L

    val ALLOWED_METADATA_HOSTS = setOf("api.github.com")
    val ALLOWED_DOWNLOAD_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "codeload.github.com"
    )
    val DEFAULT_TRUSTED_CERTS = setOf(TRUSTED_RELEASE_CERT_SHA256)

    fun isHex64(value: String): Boolean =
        value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    fun isTrustedHttpsUrl(rawUrl: String?, allowedHosts: Set<String>): Boolean {
        val trimmed = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return try {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
            if (scheme != "https") return false
            if (!uri.userInfo.isNullOrEmpty()) return false
            if (uri.port != -1 && uri.port != 443) return false
            val host = uri.host?.trim()?.lowercase(Locale.US)?.trimEnd('.') ?: return false
            host in allowedHosts
        } catch (_: Exception) {
            false
        }
    }

    fun sanitizeSafeFileName(rawName: String?): String {
        val base = rawName?.substringAfterLast('/')?.substringAfterLast('\\')?.trim().orEmpty()
        val safe = buildString(base.length) {
            for (ch in base) {
                if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '.' || ch == '_' || ch == '-') {
                    append(ch)
                } else {
                    append('_')
                }
            }
        }
        return if (safe.isEmpty() || safe == "." || safe == "..") "timewallet-update.apk" else safe
    }

    private fun scanNumberAfterKeyword(text: String, keyword: String): Long? {
        var searchFrom = 0
        while (searchFrom < text.length) {
            val idx = text.indexOf(keyword, startIndex = searchFrom, ignoreCase = true)
            if (idx < 0) return null
            var pos = idx + keyword.length
            while (pos < text.length && text[pos] in setOf(' ', '\t', ':', '=', '#', '-')) pos++
            val startDigits = pos
            while (pos < text.length && text[pos].isDigit() && (pos - startDigits) < 9) pos++
            if (pos > startDigits) {
                val parsed = text.substring(startDigits, pos).toLongOrNull()
                if (parsed != null && parsed > 0L) return parsed
            }
            searchFrom = idx + keyword.length
        }
        return null
    }

    fun parseVersionCodeHint(tagName: String, body: String): Long? =
        scanNumberAfterKeyword(body, "versionCode")
            ?: scanNumberAfterKeyword(body, "build")
            ?: scanNumberAfterKeyword(tagName, "build")

    fun parseCooldownHoursHint(body: String): Int? =
        (scanNumberAfterKeyword(body, "cooldownHours") ?: scanNumberAfterKeyword(body, "cooldownHour"))
            ?.toInt()
            ?.takeIf { it in 1..720 }

    fun effectiveCooldownMs(
        prefs: UpdatePreferences,
        candidate: VerifiedReleaseCandidate? = null
    ): Long {
        val recMs = candidate?.recommendedCooldownHours?.takeIf { it in 1..720 }?.let { it.toLong() * 60L * 60_000L }
        return if (recMs != null) minOf(prefs.checkInterval.intervalMs, recMs) else prefs.checkInterval.intervalMs
    }

    /**
     * Đọc digest SHA-256 chuẩn 64 ký tự hex từ tệp `.sha256` phát hành.
     * Nếu tệp chứa nhiều dòng, bắt buộc tên tệp trên dòng phải khớp [expectedApkFileName] để tránh ghép nhầm digest.
     */
    fun parseExpectedSha256(rawText: String?, expectedApkFileName: String): String? {
        val clean = rawText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val targetName = expectedApkFileName.substringAfterLast('/').substringAfterLast('\\').trim()
        val candidates = clean.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                if (line.length < 64) return@mapNotNull null
                val hexPart = line.substring(0, 64)
                if (!isHex64(hexPart)) return@mapNotNull null
                val rest = line.substring(64)
                if (rest.isNotEmpty() && !rest[0].isWhitespace()) return@mapNotNull null
                val filePart = rest.trim().removePrefix("*").substringAfterLast('/').substringAfterLast('\\').trim()
                hexPart.lowercase(Locale.US) to filePart
            }
            .toList()
        if (candidates.isEmpty()) return null
        val exactMatch = candidates.firstOrNull { (_, name) ->
            name.isNotEmpty() && name.equals(targetName, ignoreCase = true)
        }
        if (exactMatch != null) return exactMatch.first
        if (candidates.size == 1 && candidates.first().second.isEmpty()) {
            return candidates.first().first
        }
        return null
    }

    fun parseLatestReleaseJson(
        jsonBody: String,
        currentVersionName: String,
        currentVersionCode: Long,
        checkedAtMs: Long,
        channel: DistributionChannel = DistributionChannel.GITHUB_DIRECT
    ): UpdateCheckOutcome {
        return try {
            val root = JSONObject(jsonBody)
            if (root.optBoolean("draft", false) || root.optBoolean("prerelease", false)) {
                return UpdateCheckOutcome.UpToDate(currentVersionName, currentVersionCode, checkedAtMs)
            }
            val tagName = root.optString("tag_name", "").trim()
            val candidateSemver = SemanticVersion.parse(tagName)
                ?: return UpdateCheckOutcome.Unavailable("Nhãn phiên bản phát hành không hợp lệ.", retryable = false)

            val currentSemver = SemanticVersion.parse(currentVersionName)
            val rawBody = root.optString("body", "").trim()
            val embeddedCode = parseVersionCodeHint(tagName, rawBody)
            val recommendedCooldownHours = parseCooldownHoursHint(rawBody)

            val isNewer = when {
                embeddedCode != null && currentSemver != null ->
                    embeddedCode > currentVersionCode && candidateSemver >= currentSemver
                embeddedCode != null -> embeddedCode > currentVersionCode
                currentSemver != null -> candidateSemver > currentSemver
                else -> tagName.removePrefix("v") != currentVersionName.removePrefix("v")
            }
            if (!isNewer) {
                return UpdateCheckOutcome.UpToDate(currentVersionName, currentVersionCode, checkedAtMs)
            }

            val htmlUrl = root.optString("html_url", DEFAULT_RELEASES_PAGE_URL).trim().let {
                if (isTrustedHttpsUrl(it, ALLOWED_DOWNLOAD_HOSTS)) it else DEFAULT_RELEASES_PAGE_URL
            }
            val assetsArr = root.optJSONArray("assets")
                ?: return UpdateCheckOutcome.Unavailable("Bản phát hành chưa đính kèm tệp APK.", retryable = false)

            data class AssetEntry(val name: String, val url: String, val size: Long)
            val assets = buildList {
                for (i in 0 until assetsArr.length()) {
                    val o = assetsArr.optJSONObject(i) ?: continue
                    val name = o.optString("name", "").trim()
                    val url = o.optString("browser_download_url", "").trim()
                    val size = o.optLong("size", -1L)
                    if (name.isNotEmpty() && url.isNotEmpty()) add(AssetEntry(name, url, size))
                }
            }

            // Chỉ chọn APK phát hành (ưu tiên `-release.apk`, loại trừ bản `-debug.apk`).
            val apkAssets = assets.filter {
                it.name.endsWith(".apk", ignoreCase = true) && !it.name.contains("debug", ignoreCase = true)
            }
            val apkAsset = apkAssets.firstOrNull { it.name.contains("release", ignoreCase = true) }
                ?: apkAssets.firstOrNull()
                ?: return UpdateCheckOutcome.Unavailable("Bản phát hành mới chưa có APK release hợp lệ.", retryable = false)

            if (!isTrustedHttpsUrl(apkAsset.url, ALLOWED_DOWNLOAD_HOSTS)) {
                return UpdateCheckOutcome.Unavailable("Nguồn tải APK không thuộc máy chủ GitHub đáng tin cậy.", retryable = false)
            }
            if (apkAsset.size <= 0L || apkAsset.size > MAX_APK_BYTES) {
                return UpdateCheckOutcome.Unavailable("Kích thước tệp APK vượt giới hạn an toàn.", retryable = false)
            }

            val shaAsset = assets.firstOrNull {
                it.name.equals("${apkAsset.name}.sha256", ignoreCase = true)
            } ?: assets.filter { it.name.endsWith(".sha256", ignoreCase = true) }.singleOrNull()
                ?: return UpdateCheckOutcome.Unavailable("Thiếu tệp kiểm tra toàn vẹn (.sha256) đi kèm APK.", retryable = false)

            if (!isTrustedHttpsUrl(shaAsset.url, ALLOWED_DOWNLOAD_HOSTS)) {
                return UpdateCheckOutcome.Unavailable("Nguồn tệp checksum không thuộc máy chủ GitHub đáng tin cậy.", retryable = false)
            }

            val cleanNotes = summarizeReleaseNotes(rawBody)
            UpdateCheckOutcome.Available(
                candidate = VerifiedReleaseCandidate(
                    tagName = tagName,
                    versionName = candidateSemver.toString(),
                    embeddedVersionCode = embeddedCode,
                    releaseNotes = cleanNotes,
                    apkUrl = apkAsset.url,
                    apkFileName = sanitizeSafeFileName(apkAsset.name),
                    apkSizeBytes = apkAsset.size,
                    sha256Url = shaAsset.url,
                    htmlUrl = htmlUrl,
                    publishedAtIso = root.optString("published_at", "").takeIf { it.isNotBlank() },
                    recommendedCooldownHours = recommendedCooldownHours
                ),
                channel = channel
            )
        } catch (_: Exception) {
            UpdateCheckOutcome.Unavailable("Không đọc được thông tin phiên bản từ GitHub Releases.", retryable = true)
        }
    }

    fun formatCompactReleaseNotes(rawMarkdown: String, maxLines: Int = 7): List<ReleaseMarkdownLine> {
        val rawLines = rawMarkdown.lineSequence().map { it.trim() }.toList()
        val result = mutableListOf<ReleaseMarkdownLine>()
        var inCodeFence = false
        var skippingTechnicalSection = false

        for (line in rawLines) {
            if (line.startsWith("```")) {
                inCodeFence = !inCodeFence
                continue
            }
            if (inCodeFence || line.isEmpty()) continue
            if (line.startsWith("|") || line.startsWith("---") || line.startsWith("===")) continue
            if (line.startsWith("<!--")) continue
            val lower = line.lowercase(Locale.US)
            if (lower.startsWith("versioncode:") || lower.startsWith("version code:") ||
                lower.startsWith("cooldownhours:") || lower.startsWith("sha256:") || lower.startsWith("sha-256:")
            ) {
                continue
            }

            if (line.startsWith("#")) {
                val heading = line.trimStart('#', ' ', '\t').replace("`", "").trim()
                val hLower = heading.lowercase(Locale.US)
                skippingTechnicalSection = hLower.contains("tệp cài đặt") ||
                    hLower.contains("tệp apk") ||
                    hLower.contains("checksum") ||
                    hLower.contains("sha-256") ||
                    hLower.contains("sha256") ||
                    hLower.contains("xác minh")
                if (!skippingTechnicalSection && heading.isNotEmpty()) {
                    if (result.lastOrNull()?.isHeading == true) {
                        result.removeAt(result.lastIndex)
                    }
                    result += ReleaseMarkdownLine(text = heading, isHeading = true)
                }
                continue
            }
            if (skippingTechnicalSection) continue

            val isBullet = line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ")
            val cleaned = line
                .removePrefix("- ")
                .removePrefix("* ")
                .removePrefix("• ")
                .replace("`", "")
                .trim()
            if (cleaned.isEmpty()) continue
            if (cleaned.lowercase(Locale.US).contains("xem sha-256")) continue

            result += ReleaseMarkdownLine(text = cleaned, isHeading = false, isBullet = isBullet)
            if (result.count { !it.isHeading } >= maxLines) break
        }
        while (result.isNotEmpty() && result.last().isHeading) {
            result.removeAt(result.lastIndex)
        }
        return result.ifEmpty {
            listOf(ReleaseMarkdownLine("Cập nhật cải tiến độ ổn định và trải nghiệm."))
        }
    }

    fun summarizeReleaseNotes(rawBody: String): String {
        val compact = formatCompactReleaseNotes(rawBody, maxLines = 8)
            .joinToString("\n") { item ->
                when {
                    item.isHeading -> "### ${item.text}"
                    item.isBullet -> "- ${item.text}"
                    else -> item.text
                }
            }
        return compact.ifBlank { "Cập nhật cải tiến độ ổn định và trải nghiệm." }.take(900)
    }

    /**
     * Xác minh 3 yếu tố trên gói APK sau khi tải xong:
     * 1) SHA-256 khớp tuyệt đối với `.sha256` phát hành.
     * 2) Đúng `packageName` và `versionCode > currentVersionCode` (chống hạ cấp / cài nhầm gói).
     * 3) Chữ ký APK nằm trong tập chứng thư phát hành đáng tin cậy ([trustedCerts]).
     *    Nếu bản đang cài trên máy trùng chứng thư này => nâng cấp đè giữ nguyên 100% dữ liệu.
     */
    fun verifyDownloadedArchive(
        apkFilePath: String,
        meta: ArchivePackageMetadata?,
        expectedAppId: String,
        currentVersionCode: Long,
        installedCertSha256: String?,
        computedApkSha256: String,
        expectedSha256: String,
        trustedCerts: Set<String> = DEFAULT_TRUSTED_CERTS
    ): ArchiveTrustDecision {
        val cleanComputed = computedApkSha256.trim().lowercase(Locale.US)
        val cleanExpected = expectedSha256.trim().lowercase(Locale.US)
        if (!isHex64(cleanComputed) || !isHex64(cleanExpected) || cleanComputed != cleanExpected) {
            return ArchiveTrustDecision.Rejected(
                reasonVi = "Mã kiểm tra SHA-256 của APK không khớp với tệp tham chiếu trên GitHub.",
                securityFailure = true
            )
        }
        if (meta == null) {
            return ArchiveTrustDecision.Rejected(
                reasonVi = "Không đọc được cấu trúc gói APK đã tải (gói lỗi hoặc không hoàn chỉnh).",
                securityFailure = true
            )
        }
        if (meta.packageName != expectedAppId) {
            return ArchiveTrustDecision.Rejected(
                reasonVi = "Gói APK mang mã định danh (${meta.packageName}) không khớp với $expectedAppId.",
                securityFailure = true
            )
        }
        if (meta.versionCode <= currentVersionCode) {
            return ArchiveTrustDecision.Rejected(
                reasonVi = "Phiên bản APK tải về (versionCode ${meta.versionCode}) không cao hơn bản hiện tại ($currentVersionCode).",
                securityFailure = true
            )
        }
        val archiveCerts = meta.certSha256List.map { it.trim().lowercase(Locale.US) }.filter { isHex64(it) }.toSet()
        if (archiveCerts.isEmpty()) {
            return ArchiveTrustDecision.Rejected(
                reasonVi = "APK không có chữ ký số hợp lệ để xác thực nguồn phát hành.",
                securityFailure = true
            )
        }
        val normalizedTrusted = trustedCerts.map { it.trim().lowercase(Locale.US) }.toSet()
        val matchesTrusted = archiveCerts.any { it in normalizedTrusted }
        val normalizedInstalled = installedCertSha256?.trim()?.lowercase(Locale.US)?.takeIf { isHex64(it) }
        val matchesInstalled = normalizedInstalled != null && normalizedInstalled in archiveCerts

        // Nếu đang có danh sách chứng thư phát hành tin cậy, bắt buộc APK phải khớp chứng thư đó
        // (ngăn kịch bản đánh tráo đồng thời APK + .sha256 bằng khóa ký lạ).
        if (normalizedTrusted.isNotEmpty() && !matchesTrusted) {
            return ArchiveTrustDecision.Rejected(
                reasonVi = "Chữ ký số của APK không khớp chứng thư phát hành đáng tin cậy của Time Wallet.",
                securityFailure = true
            )
        }

        val primaryCert = archiveCerts.first()
        return ArchiveTrustDecision.Valid(
            ApkVerificationSummary(
                apkFilePath = apkFilePath,
                packageName = meta.packageName,
                versionName = meta.versionName,
                versionCode = meta.versionCode,
                apkSha256 = cleanComputed,
                signingCertSha256 = primaryCert,
                matchesTrustedReleaseCert = matchesTrusted,
                matchesInstalledCert = matchesInstalled
            )
        )
    }
}

enum class AppUpdateStatus {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    AVAILABLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    ERROR
}

data class AppUpdateUiState(
    val channel: DistributionChannel = DistributionChannel.GITHUB_DIRECT,
    val status: AppUpdateStatus = AppUpdateStatus.IDLE,
    val preferences: UpdatePreferences = UpdatePreferences(),
    val installedVersionName: String = "",
    val installedVersionCode: Long = 0L,
    val availableCandidate: VerifiedReleaseCandidate? = null,
    val verification: ApkVerificationSummary? = null,
    val downloadProgressPercent: Int = 0,
    val statusMessageVi: String? = null,
    val showDialog: Boolean = false,
    val securityRejected: Boolean = false
)

