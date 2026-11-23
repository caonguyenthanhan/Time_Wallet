package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timewallet.mvp.domain.usecase.AppUpdateStatus
import com.timewallet.mvp.domain.usecase.AppUpdateUiState
import com.timewallet.mvp.domain.usecase.DistributionChannel
import com.timewallet.mvp.domain.usecase.GitHubReleaseTrustPolicy
import com.timewallet.mvp.domain.usecase.UpdateCheckInterval
import com.timewallet.mvp.ui.theme.WalletCardBg
import com.timewallet.mvp.ui.theme.WalletDangerBg
import com.timewallet.mvp.ui.theme.WalletDangerBorder
import com.timewallet.mvp.ui.theme.WalletDangerText
import com.timewallet.mvp.ui.theme.WalletDivider
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMicroText
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintDark
import com.timewallet.mvp.ui.theme.WalletMintSoft
import com.timewallet.mvp.ui.theme.WalletMintAccent
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletStroke
import com.timewallet.mvp.ui.theme.WalletWarningBg
import com.timewallet.mvp.ui.theme.WalletWarningText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppUpdateDialog(
    state: AppUpdateUiState,
    onCheckAgain: () -> Unit,
    onDownloadAndInstall: () -> Unit,
    onLaunchVerifiedInstaller: () -> Unit,
    onSkipCandidate: () -> Unit,
    onToggleAutoCheck: (Boolean) -> Unit,
    onSelectCheckInterval: (UpdateCheckInterval) -> Unit = {},
    onDismiss: () -> Unit
) {
    val candidate = state.availableCandidate
    val verification = state.verification
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WalletCardBg,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (candidate != null) "Có bản mới ${candidate.tagName}" else "Cập nhật ứng dụng",
                    fontWeight = FontWeight.ExtraBold,
                    color = WalletInk,
                    fontSize = 16.5.sp
                )
                Text(
                    text = "Đang cài: v${state.installedVersionName.ifBlank { "—" }} (build ${state.installedVersionCode})",
                    fontSize = 11.5.sp,
                    color = WalletMuted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                if (candidate != null) {
                    val formattedLines = remember(candidate.releaseNotes) {
                        GitHubReleaseTrustPolicy.formatCompactReleaseNotes(candidate.releaseNotes)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = WalletMintSoft,
                        border = BorderStroke(1.dp, WalletMint),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = candidate.apkFileName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalletMintAccent,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${(candidate.apkSizeBytes / (1024 * 1024f)).let { java.lang.String.format(java.util.Locale.US, "%.1f MB", it) }}",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = WalletMintAccent
                                )
                            }
                            HorizontalDivider(color = WalletDivider)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                formattedLines.forEach { item ->
                                    when {
                                        item.isHeading -> {
                                            Text(
                                                text = item.text,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = WalletMintAccent
                                            )
                                        }
                                        item.isBullet -> {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Text("•", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WalletMintAccent)
                                                Text(
                                                    text = markdownInlineAnnotatedString(item.text),
                                                    fontSize = 11.5.sp,
                                                    color = WalletInk,
                                                    lineHeight = 16.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                        else -> {
                                            Text(
                                                text = markdownInlineAnnotatedString(item.text),
                                                fontSize = 11.5.sp,
                                                color = WalletInk,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                text = "GitHub HTTPS · SHA-256 · Khớp chữ ký giữ nguyên dữ liệu",
                                fontSize = 10.sp,
                                color = WalletMicroText
                            )
                        }
                    }
                } else {
                    Text(
                        text = "${state.channel.labelVi} · Giữ nguyên sổ cái & khóa AI.",
                        fontSize = 11.sp,
                        color = WalletMuted
                    )
                }

                if (state.status == AppUpdateStatus.DOWNLOADING) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { (state.downloadProgressPercent.coerceIn(0, 100)) / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = WalletMintDark
                        )
                        Text(
                            text = "Đang tải và xác minh APK (${state.downloadProgressPercent}%)…",
                            fontSize = 12.sp,
                            color = WalletMuted
                        )
                    }
                }

                if (verification != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (verification.matchesInstalledCert) WalletMintSoft else WalletWarningBg,
                        border = BorderStroke(1.dp, WalletStroke),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "✓ Đã xác minh SHA-256 & chữ ký số (build ${verification.versionCode})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (verification.matchesInstalledCert) WalletMintAccent else WalletWarningText
                            )
                            if (!verification.matchesInstalledCert) {
                                Text(
                                    text = "Lưu ý: Bản đang cài trên máy mang chứng thư khác (ví dụ bản Debug). Android không cho cài đè khác chữ ký — hãy Xuất JSON sao lưu dữ liệu trước khi gỡ bản cũ và cài bản mới.",
                                    fontSize = 11.sp,
                                    color = WalletWarningText,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                state.statusMessageVi?.takeIf { it.isNotBlank() }?.let { msg ->
                    val surfaceBg = if (state.securityRejected) WalletDangerBg else WalletCardBg
                    val borderClr = if (state.securityRejected) WalletDangerBorder else WalletStroke
                    val textClr = if (state.securityRejected) WalletDangerText else WalletInk
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = surfaceBg,
                        border = BorderStroke(1.dp, borderClr),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            fontSize = 11.5.sp,
                            color = textClr,
                            modifier = Modifier.padding(10.dp),
                            lineHeight = 16.sp
                        )
                    }
                }

                if (state.channel == DistributionChannel.GITHUB_DIRECT) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Tự kiểm tra khi mở app", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WalletInk)
                            Text(state.preferences.checkInterval.labelVi, fontSize = 10.5.sp, color = WalletMuted)
                        }
                        Switch(
                            checked = state.preferences.autoCheckEnabled,
                            onCheckedChange = onToggleAutoCheck,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = WalletMintDark
                            )
                        )
                    }
                    if (state.preferences.autoCheckEnabled) {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 34.dp) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                UpdateCheckInterval.entries.forEach { interval ->
                                    val isSelected = state.preferences.checkInterval == interval
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(if (isSelected) WalletMintDark else WalletCardBg)
                                            .border(
                                                1.dp,
                                                if (isSelected) WalletMintDark else WalletStroke,
                                                RoundedCornerShape(100.dp)
                                            )
                                            .semantics { selected = isSelected }
                                            .walletClickable(
                                                onClickLabel = "Tần suất ${interval.labelVi}",
                                                role = Role.RadioButton
                                            ) { onSelectCheckInterval(interval) }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = interval.shortLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else WalletInk,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                state.status == AppUpdateStatus.READY_TO_INSTALL && verification != null -> {
                    Button(
                        onClick = onLaunchVerifiedInstaller,
                        colors = ButtonDefaults.buttonColors(containerColor = WalletMintDark, contentColor = Color.White),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cài đặt ngay", fontWeight = FontWeight.Bold)
                    }
                }
                candidate != null && state.channel == DistributionChannel.GITHUB_DIRECT -> {
                    Button(
                        onClick = onDownloadAndInstall,
                        enabled = state.status != AppUpdateStatus.DOWNLOADING,
                        colors = ButtonDefaults.buttonColors(containerColor = WalletMintDark, contentColor = Color.White),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = if (state.status == AppUpdateStatus.DOWNLOADING) "Đang tải…" else "Tải & Cài đặt",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                else -> {
                    OutlinedButton(
                        onClick = onCheckAgain,
                        enabled = state.status != AppUpdateStatus.CHECKING,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(if (state.status == AppUpdateStatus.CHECKING) "Đang kiểm tra…" else "Kiểm tra lại")
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (candidate != null && state.status != AppUpdateStatus.DOWNLOADING) {
                    TextButton(onClick = onSkipCandidate) {
                        Text("Bỏ qua bản này", color = WalletMuted)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Đóng", color = WalletMuted)
                }
            }
        }
    )
}

internal fun markdownInlineAnnotatedString(rawText: String): AnnotatedString = buildAnnotatedString {
    var idx = 0
    while (idx < rawText.length) {
        val boldStart = rawText.indexOf("**", startIndex = idx)
        if (boldStart < 0) {
            append(rawText.substring(idx))
            break
        }
        val boldEnd = rawText.indexOf("**", startIndex = boldStart + 2)
        if (boldEnd < 0) {
            append(rawText.substring(idx))
            break
        }
        if (boldStart > idx) {
            append(rawText.substring(idx, boldStart))
        }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(rawText.substring(boldStart + 2, boldEnd))
        }
        idx = boldEnd + 2
    }
}
