package com.timewallet.mvp.ui

import android.content.Context
import com.timewallet.mvp.data.local.TimerAndSettingsStore
import com.timewallet.mvp.data.update.AndroidApkVerifier
import com.timewallet.mvp.data.update.UpdateChannelResolver
import com.timewallet.mvp.domain.usecase.AppUpdateChannel
import com.timewallet.mvp.domain.usecase.AppUpdateStatus
import com.timewallet.mvp.domain.usecase.AppUpdateUiState
import com.timewallet.mvp.domain.usecase.DistributionChannel
import com.timewallet.mvp.domain.usecase.GitHubReleaseTrustPolicy
import com.timewallet.mvp.domain.usecase.UpdateCheckInterval
import com.timewallet.mvp.domain.usecase.UpdateCheckOutcome
import com.timewallet.mvp.domain.usecase.UpdatePrepareOutcome
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Điều phối kiểm tra, xác minh 3 lớp và cài đặt cập nhật theo Phương án A
 * (tách khỏi God-ViewModel, không làm chặn khởi động ứng dụng).
 */
class AppUpdateCoordinator(
    private val appContext: Context,
    private val settingsStore: TimerAndSettingsStore,
    private val state: MutableStateFlow<TimeWalletUiState>,
    private val scope: CoroutineScope,
    private val launchContext: CoroutineExceptionHandler,
    private val toast: (String) -> Unit,
    private val updateChannel: AppUpdateChannel = UpdateChannelResolver.create(DistributionChannel.GITHUB_DIRECT),
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private val verifier = AndroidApkVerifier(appContext)
    private var activeJob: Job? = null

    fun initialUiState(): AppUpdateUiState {
        val (verName, verCode) = verifier.readInstalledVersion()
        val prefs = runCatching { settingsStore.loadUpdatePreferences() }.getOrDefault(
            com.timewallet.mvp.domain.usecase.UpdatePreferences()
        )
        return AppUpdateUiState(
            channel = updateChannel.channel,
            preferences = prefs,
            installedVersionName = verName,
            installedVersionCode = verCode
        )
    }

    fun checkOnStartupIfDue() {
        val current = state.value.appUpdate
        if (current.channel != DistributionChannel.GITHUB_DIRECT) return
        if (!current.preferences.autoCheckEnabled) return
        val elapsed = nowMs() - current.preferences.lastCheckEpochMs
        val cooldownMs = GitHubReleaseTrustPolicy.effectiveCooldownMs(current.preferences, current.availableCandidate)
        if (current.preferences.lastCheckEpochMs > 0L && elapsed < cooldownMs) {
            return
        }
        checkForUpdate(manual = false)
    }

    fun setCheckInterval(interval: UpdateCheckInterval) {
        val nextPrefs = state.value.appUpdate.preferences.copy(checkInterval = interval)
        settingsStore.saveUpdatePreferences(nextPrefs, immediate = false)
        state.update { it.copy(appUpdate = it.appUpdate.copy(preferences = nextPrefs)) }
    }

    fun openDialog() {
        val cur = state.value.appUpdate
        state.update { it.copy(appUpdate = it.appUpdate.copy(showDialog = true)) }
        if (cur.status == AppUpdateStatus.IDLE || cur.status == AppUpdateStatus.ERROR) {
            checkForUpdate(manual = true)
        }
    }

    fun dismissDialog() {
        state.update { it.copy(appUpdate = it.appUpdate.copy(showDialog = false)) }
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        val nextPrefs = state.value.appUpdate.preferences.copy(autoCheckEnabled = enabled)
        settingsStore.saveUpdatePreferences(nextPrefs, immediate = false)
        state.update { it.copy(appUpdate = it.appUpdate.copy(preferences = nextPrefs)) }
    }

    fun skipAvailableCandidate() {
        val cur = state.value.appUpdate
        val codeToSkip = cur.availableCandidate?.embeddedVersionCode ?: (cur.installedVersionCode + 1L)
        val nextPrefs = cur.preferences.copy(
            skippedVersionCode = codeToSkip,
            promptedVersionCode = maxOf(cur.preferences.promptedVersionCode, codeToSkip)
        )
        settingsStore.saveUpdatePreferences(nextPrefs, immediate = false)
        state.update {
            it.copy(
                appUpdate = it.appUpdate.copy(
                    preferences = nextPrefs,
                    availableCandidate = null,
                    verification = null,
                    status = AppUpdateStatus.UP_TO_DATE,
                    showDialog = false,
                    statusMessageVi = "Đã bỏ qua thông báo phiên bản này."
                )
            )
        }
    }

    fun checkForUpdate(manual: Boolean = true) {
        if (activeJob?.isActive == true) return
        val before = state.value.appUpdate
        if (manual) {
            state.update {
                it.copy(
                    appUpdate = it.appUpdate.copy(
                        status = AppUpdateStatus.CHECKING,
                        statusMessageVi = null,
                        securityRejected = false
                    )
                )
            }
        }
        activeJob = scope.launch(launchContext) {
            val (verName, verCode) = verifier.readInstalledVersion()
            val outcome = updateChannel.checkForUpdate(verName, verCode)
            val checkedPrefs = state.value.appUpdate.preferences.copy(lastCheckEpochMs = nowMs())
            settingsStore.saveUpdatePreferences(checkedPrefs, immediate = false)

            when (outcome) {
                is UpdateCheckOutcome.UpToDate -> {
                    state.update {
                        it.copy(
                            appUpdate = it.appUpdate.copy(
                                preferences = checkedPrefs,
                                availableCandidate = null,
                                verification = null,
                                status = AppUpdateStatus.UP_TO_DATE,
                                statusMessageVi = if (manual) "Bạn đang sử dụng phiên bản mới nhất (v$verName)." else null,
                                securityRejected = false
                            )
                        )
                    }
                }
                is UpdateCheckOutcome.Available -> {
                    val skippedCode = checkedPrefs.skippedVersionCode
                    val candCode = outcome.candidate.embeddedVersionCode
                    if (!manual && candCode != null && candCode <= skippedCode) {
                        state.update {
                            it.copy(appUpdate = it.appUpdate.copy(preferences = checkedPrefs, status = AppUpdateStatus.IDLE))
                        }
                        return@launch
                    }
                    val candVerKey = candCode ?: (outcome.candidate.versionName.hashCode().toLong() and 0x7FFFFFFFL).coerceAtLeast(1L)
                    val shouldAutoPopup = !manual && candVerKey > checkedPrefs.promptedVersionCode && candVerKey > skippedCode
                    val finalPrefs = if (shouldAutoPopup) {
                        checkedPrefs.copy(promptedVersionCode = candVerKey).also {
                            settingsStore.saveUpdatePreferences(it, immediate = false)
                        }
                    } else {
                        checkedPrefs
                    }
                    state.update {
                        it.copy(
                            appUpdate = it.appUpdate.copy(
                                preferences = finalPrefs,
                                availableCandidate = outcome.candidate,
                                verification = null,
                                status = AppUpdateStatus.AVAILABLE,
                                statusMessageVi = null,
                                securityRejected = false,
                                showDialog = if (manual || shouldAutoPopup) true else it.appUpdate.showDialog
                            )
                        )
                    }
                    if (!manual && !shouldAutoPopup && !before.showDialog && before.availableCandidate?.tagName != outcome.candidate.tagName) {
                        toast("Có bản cập nhật ${outcome.candidate.tagName} trong Cài đặt.")
                    }
                }
                is UpdateCheckOutcome.Unavailable -> {
                    state.update {
                        it.copy(
                            appUpdate = it.appUpdate.copy(
                                preferences = checkedPrefs,
                                status = if (manual) AppUpdateStatus.ERROR else AppUpdateStatus.IDLE,
                                statusMessageVi = if (manual) outcome.reasonVi else null,
                                securityRejected = false
                            )
                        )
                    }
                }
            }
        }
    }

    fun downloadAndVerify() {
        val cur = state.value.appUpdate
        val candidate = cur.availableCandidate ?: return
        if (activeJob?.isActive == true) return

        state.update {
            it.copy(
                appUpdate = it.appUpdate.copy(
                    status = AppUpdateStatus.DOWNLOADING,
                    downloadProgressPercent = 0,
                    statusMessageVi = null,
                    verification = null,
                    securityRejected = false
                )
            )
        }

        activeJob = scope.launch(launchContext) {
            val (_, verCode) = verifier.readInstalledVersion()
            val installedCert = verifier.readInstalledCertSha256()
            val outcome = updateChannel.prepareInstall(
                candidate = candidate,
                cacheDirPath = appContext.cacheDir.absolutePath,
                expectedAppId = appContext.packageName,
                currentVersionCode = verCode,
                installedCertSha256 = installedCert,
                verifier = verifier,
                onProgress = { pct ->
                    state.update { st ->
                        if (st.appUpdate.downloadProgressPercent == pct) st
                        else st.copy(appUpdate = st.appUpdate.copy(downloadProgressPercent = pct))
                    }
                }
            )
            when (outcome) {
                is UpdatePrepareOutcome.ReadyToInstall -> {
                    val v = outcome.verification
                    state.update {
                        it.copy(
                            appUpdate = it.appUpdate.copy(
                                status = AppUpdateStatus.READY_TO_INSTALL,
                                verification = v,
                                downloadProgressPercent = 100,
                                statusMessageVi = null,
                                securityRejected = false
                            ),
                            // Chỉ tự mở trình cài đặt ngay sau khi người dùng đã bấm Tải & chứng thư khớp hoàn toàn (bảo toàn dữ liệu).
                            pendingApkInstallPath = if (v.matchesInstalledCert) v.apkFilePath else null
                        )
                    }
                }
                is UpdatePrepareOutcome.Rejected -> {
                    state.update {
                        it.copy(
                            appUpdate = it.appUpdate.copy(
                                status = AppUpdateStatus.ERROR,
                                verification = null,
                                statusMessageVi = outcome.reasonVi,
                                securityRejected = outcome.securityFailure
                            )
                        )
                    }
                }
                is UpdatePrepareOutcome.ChannelRedirect -> {
                    state.update {
                        it.copy(
                            appUpdate = it.appUpdate.copy(
                                status = AppUpdateStatus.UP_TO_DATE,
                                statusMessageVi = outcome.instructionsVi
                            )
                        )
                    }
                }
            }
        }
    }

    fun launchVerifiedInstaller() {
        val path = state.value.appUpdate.verification?.apkFilePath ?: return
        state.update { it.copy(pendingApkInstallPath = path) }
    }

    fun consumePendingInstall() {
        state.update { it.copy(pendingApkInstallPath = null) }
    }
}
