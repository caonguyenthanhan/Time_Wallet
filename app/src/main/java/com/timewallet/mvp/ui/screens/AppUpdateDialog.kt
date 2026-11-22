package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timewallet.mvp.domain.usecase.AppUpdateStatus
import com.timewallet.mvp.domain.usecase.AppUpdateUiState
import com.timewallet.mvp.domain.usecase.DistributionChannel
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

@Composable
fun AppUpdateDialog(
    state: AppUpdateUiState,
    onCheckAgain: () -> Unit,
    onDownloadAndInstall: () -> Unit,
    onLaunchVerifiedInstaller: () -> Unit,
    onSkipCandidate: () -> Unit,
    onToggleAutoCheck: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val candidate = state.availableCandidate
    val verification = state.verification
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WalletCardBg,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Cập nhật ứng dụng", fontWeight = FontWeight.ExtraBold, color = WalletInk, fontSize = 17.sp)
                Text(
                    text = "Kênh: ${state.channel.labelVi}",
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = WalletMintSoft,
                    border = BorderStroke(1.dp, WalletStroke),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Phiên bản đang cài: v${state.installedVersionName.ifBlank { "—" }} (build ${state.installedVersionCode})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletInk
                        )
                        Text(
                            text = "Nâng cấp hợp lệ cùng chữ ký sẽ giữ nguyên toàn bộ Sổ cái, Mục tiêu, Tag, Task và khóa AI.",
                            fontSize = 11.sp,
                            color = WalletMuted
                        )
                    }
                }

                if (candidate != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = WalletCardBg,
                        border = BorderStroke(1.dp, WalletMint),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Có bản mới: ${candidate.tagName}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WalletMintAccent
                            )
                            Text(
                                text = "Tệp APK: ${candidate.apkFileName} (${(candidate.apkSizeBytes / (1024 * 1024f)).let { java.lang.String.format(java.util.Locale.US, "%.1f MB", it) }})",
                                fontSize = 11.sp,
                                color = WalletMuted
                            )
                            HorizontalDivider(color = WalletDivider)
                            Text(
                                text = candidate.releaseNotes,
                                fontSize = 12.sp,
                                color = WalletInk,
                                lineHeight = 17.sp
                            )
                            Text(
                                text = "Bảo mật 3 lớp: Nguồn GitHub HTTPS + SHA-256 + Xác minh chữ ký APK trước khi mở trình cài đặt.",
                                fontSize = 10.5.sp,
                                color = WalletMicroText,
                                lineHeight = 15.sp
                            )
                        }
                    }
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
                            Text("Tự kiểm tra khi mở app", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = WalletInk)
                            Text("Tối đa 12 giờ/lần ở nền, không tự ý cài", fontSize = 10.5.sp, color = WalletMuted)
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
