package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.usecase.ActivityBreakEngine
import com.timewallet.mvp.domain.usecase.BreakDecision
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.ui.PendingOverlapConflict
import com.timewallet.mvp.ui.theme.WalletCardBg
import com.timewallet.mvp.ui.theme.WalletClarificationBg
import com.timewallet.mvp.ui.theme.WalletDangerText
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintAccent
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletSoftNeutral
import com.timewallet.mvp.ui.theme.WalletStroke
import com.timewallet.mvp.ui.theme.WalletWarningBorder
import com.timewallet.mvp.ui.theme.WalletWarningText
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Hộp thoại xác nhận kết quả thực tế khi tới giờ kết thúc dự kiến hoặc khi chạm vào bản ghi PLANNED / DOING / PENDING_CONFIRMATION.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityConfirmationDialog(
    record: LedgerRecord,
    nowEpochMs: Long = System.currentTimeMillis(),
    onDismiss: () -> Unit,
    onStartDoing: () -> Unit,
    onConfirmCompleted: () -> Unit,
    onAdjust: (title: String, startHm: String, endHm: String) -> Unit,
    onInterrupt: (actualEndHm: String) -> Unit,
    onContinue: (newEndHm: String) -> Unit,
    onCancelActivity: () -> Unit
) {
    val zone = remember(record.timezone) { runCatching { ZoneId.of(record.timezone) }.getOrDefault(ZoneId.systemDefault()) }
    val hmFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val confState = remember(record, nowEpochMs) { record.resolveConfirmationStateAt(nowEpochMs) }
    val initStart = remember(record.startedAt) {
        record.startedAt?.let { Instant.ofEpochMilli(it).atZone(zone).format(hmFmt) } ?: ""
    }
    val initEnd = remember(record.endedAt) {
        record.endedAt?.let { Instant.ofEpochMilli(it).atZone(zone).format(hmFmt) } ?: ""
    }
    val nowHm = Instant.ofEpochMilli(nowEpochMs).atZone(zone).format(hmFmt)

    var subMode by remember(record.id) { mutableStateOf("MAIN") } // MAIN | ADJUST | INTERRUPT | CONTINUE
    var editTitle by remember(record.id) { mutableStateOf(record.title) }
    var editStartHm by remember(record.id) { mutableStateOf(initStart) }
    var editEndHm by remember(record.id) { mutableStateOf(initEnd) }
    var earlyEndHm by remember(record.id) { mutableStateOf(nowHm) }
    var continueEndHm by remember(record.id) {
        val next = Instant.ofEpochMilli(maxOf(nowEpochMs, record.endedAt ?: nowEpochMs) + 30 * 60_000L).atZone(zone).format(hmFmt)
        mutableStateOf(next)
    }

    val rangeText = if (initStart.isNotBlank() && initEnd.isNotBlank()) "$initStart–$initEnd (${record.date})" else record.date
    val hasStarted = record.startedAt == null || record.startedAt <= nowEpochMs
    val actualElapsed = record.actualElapsedSecondsAt(nowEpochMs)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WalletCardBg,
        title = {
            Text(
                text = when (confState) {
                    ConfirmationState.PENDING_CONFIRMATION -> "Đã tới giờ xác nhận"
                    ConfirmationState.DOING -> "Hoạt động đang diễn ra"
                    ConfirmationState.PLANNED -> "Hoạt động dự kiến"
                    else -> "Xác nhận hoạt động"
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = WalletInk
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WalletSoftNeutral)
                        .border(1.dp, WalletStroke, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryBadge(category = record.category, size = 28.dp)
                        Text(record.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WalletInk)
                    }
                    Text("Khung giờ: $rangeText · ${confState.labelVi}", fontSize = 12.sp, color = WalletMuted)
                    Text(
                        "Đã tính thực tế hiện tại: ${LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(actualElapsed)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WalletMintAccent
                    )
                }

                when (subMode) {
                    "MAIN" -> {
                        if (confState == ConfirmationState.PLANNED && hasStarted) {
                            Button(
                                onClick = onStartDoing,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF185FA5), contentColor = Color.White),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) { Text("Bắt đầu thực hiện (Đang diễn ra)", fontWeight = FontWeight.Bold) }
                        }
                        if (hasStarted) {
                            Button(
                                onClick = onConfirmCompleted,
                                colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) { Text("Xác nhận hoàn thành", fontWeight = FontWeight.Bold) }
                        }
                        OutlinedButton(
                            onClick = { subMode = "ADJUST" },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text("Điều chỉnh giờ / kết quả thực tế") }
                        if (hasStarted && record.startedAt != null) {
                            OutlinedButton(
                                onClick = { subMode = "INTERRUPT" },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) { Text("Gián đoạn / Kết thúc sớm") }
                        }
                        if (record.startedAt != null) {
                            OutlinedButton(
                                onClick = { subMode = "CONTINUE" },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) { Text("Tiếp tục thêm thời gian") }
                        }
                        OutlinedButton(
                            onClick = onCancelActivity,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text("Hủy (không diễn ra)", color = WalletDangerText) }
                    }

                    "ADJUST" -> {
                        Text("Điều chỉnh thời gian hoặc tên hoạt động (giữ lịch sử kế hoạch ban đầu):", fontSize = 12.sp, color = WalletMuted)
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it.take(110) },
                            label = { Text("Tên hoạt động") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (record.startedAt != null || initStart.isNotBlank()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = editStartHm,
                                    onValueChange = { editStartHm = it.trim() },
                                    label = { Text("Bắt đầu (HH:mm)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = editEndHm,
                                    onValueChange = { editEndHm = it.trim() },
                                    label = { Text("Kết thúc (HH:mm)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { subMode = "MAIN" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Quay lại") }
                            Button(
                                onClick = { onAdjust(editTitle, editStartHm, editEndHm) },
                                colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text("Lưu điều chỉnh", fontWeight = FontWeight.Bold) }
                        }
                    }

                    "INTERRUPT" -> {
                        Text("Chọn mốc giờ đã dừng thực tế (giữ phần đã làm tới giờ dừng):", fontSize = 12.sp, color = WalletMuted)
                        OutlinedTextField(
                            value = earlyEndHm,
                            onValueChange = { earlyEndHm = it.trim() },
                            label = { Text("Giờ dừng thực tế (HH:mm)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { subMode = "MAIN" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Quay lại") }
                            Button(
                                onClick = { onInterrupt(earlyEndHm) },
                                colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text("Chốt gián đoạn", fontWeight = FontWeight.Bold) }
                        }
                    }

                    "CONTINUE" -> {
                        Text("Đặt giờ kết thúc dự kiến mới (hoạt động tiếp tục ở trạng thái Đang diễn ra):", fontSize = 12.sp, color = WalletMuted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(30, 60, 120).forEach { addMins ->
                                val nextHm = Instant.ofEpochMilli(maxOf(nowEpochMs, record.endedAt ?: nowEpochMs) + addMins * 60_000L)
                                    .atZone(zone).format(hmFmt)
                                ChoiceChip("+$addMins phút ($nextHm)", continueEndHm == nextHm, "Thêm $addMins phút") {
                                    continueEndHm = nextHm
                                }
                            }
                        }
                        OutlinedTextField(
                            value = continueEndHm,
                            onValueChange = { continueEndHm = it.trim() },
                            label = { Text("Giờ kết thúc mới (HH:mm)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { subMode = "MAIN" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Quay lại") }
                            Button(
                                onClick = { onContinue(continueEndHm) },
                                colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text("Tiếp tục", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Để sau", color = WalletMuted) }
        }
    )
}

/**
 * Hộp thoại xử lý chồng lấn / Activity Break khi phát hiện hai hoạt động giao nhau:
 * BREAK_CONFIRMED, PARALLEL_CONFIRMED, TIME_ADJUSTED, BREAK_REJECTED, PENDING.
 */
@Composable
fun OverlapResolutionDialog(
    conflict: PendingOverlapConflict,
    onDismissDiscard: () -> Unit,
    onResolve: (decision: BreakDecision, adjustedStartHm: String?, adjustedEndHm: String?) -> Unit
) {
    val incoming = conflict.incoming
    val zone = remember(incoming.timezone) { runCatching { ZoneId.of(incoming.timezone) }.getOrDefault(ZoneId.systemDefault()) }
    val hmFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val inStartStr = remember(incoming.startedAt) {
        incoming.startedAt?.let { Instant.ofEpochMilli(it).atZone(zone).format(hmFmt) } ?: ""
    }
    val inEndStr = remember(incoming.endedAt) {
        incoming.endedAt?.let { Instant.ofEpochMilli(it).atZone(zone).format(hmFmt) } ?: ""
    }
    var adjustingTime by remember(conflict) { mutableStateOf(false) }
    var adjStart by remember(conflict) { mutableStateOf(inStartStr) }
    var adjEnd by remember(conflict) { mutableStateOf(inEndStr) }

    val hasActiveTimerConflict = conflict.conflicts.any { it.id == ActivityBreakEngine.ACTIVE_TIMER_CONFLICT_ID }
    val breakPreview = remember(conflict) { ActivityBreakEngine.previewBreakSummaryVi(incoming, conflict.conflicts) }

    AlertDialog(
        onDismissRequest = onDismissDiscard,
        containerColor = WalletCardBg,
        title = {
            Text("Phát hiện chồng lấn thời gian", fontSize = 16.5.sp, fontWeight = FontWeight.ExtraBold, color = WalletInk)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WalletClarificationBg)
                        .border(1.dp, WalletWarningBorder, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Hoạt động mới: ${incoming.title} ($inStartStr–$inEndStr)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WalletInk)
                    conflict.conflicts.take(3).forEach { c ->
                        val cs = c.startedAt?.let { Instant.ofEpochMilli(it).atZone(zone).format(hmFmt) } ?: "?"
                        val ce = c.endedAt?.let { Instant.ofEpochMilli(it).atZone(zone).format(hmFmt) } ?: "?"
                        Text("Trùng với: ${c.title} ($cs–$ce)", fontSize = 12.sp, color = WalletWarningText)
                    }
                    if (conflict.remainingChatDraftIds.isNotEmpty()) {
                        Text(
                            "Còn ${conflict.remainingChatDraftIds.size} đề xuất kế tiếp trong lượt chat đang chờ xử lý sau bước này.",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = WalletMuted
                        )
                    }
                }

                if (!adjustingTime) {
                    if (!hasActiveTimerConflict) {
                        Button(
                            onClick = { onResolve(BreakDecision.BREAK_CONFIRMED, null, null) },
                            colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("Đây là khoảng nghỉ xen giữa", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(breakPreview, fontSize = 11.sp, color = Color.White.copy(alpha = 0.9f))
                            }
                        }
                        OutlinedButton(
                            onClick = { onResolve(BreakDecision.PARALLEL_CONFIRMED, null, null) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text("Diễn ra song song (giữ đủ giờ mỗi việc, không cộng trùng quỹ ngày)", fontSize = 12.sp)
                        }
                    }
                    OutlinedButton(
                        onClick = { adjustingTime = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("Chỉnh lại khoảng giờ") }
                    OutlinedButton(
                        onClick = { onResolve(BreakDecision.BREAK_REJECTED, null, null) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("Không phải khoảng nghỉ (cần làm rõ sau)", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = { onResolve(BreakDecision.PENDING, null, null) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("Để sau (giữ ở trạng thái chờ đối soát)", fontSize = 12.sp) }
                } else {
                    Text("Chỉnh lại giờ bắt đầu / kết thúc cho \"${incoming.title}\":", fontSize = 12.sp, color = WalletMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = adjStart,
                            onValueChange = { adjStart = it.trim() },
                            label = { Text("Bắt đầu (HH:mm)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = adjEnd,
                            onValueChange = { adjEnd = it.trim() },
                            label = { Text("Kết thúc (HH:mm)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { adjustingTime = false }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Quay lại") }
                        Button(
                            onClick = { onResolve(BreakDecision.TIME_ADJUSTED, adjStart, adjEnd) },
                            colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("Áp dụng giờ mới", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissDiscard) { Text("Hủy bỏ (không lưu)", color = WalletMuted) }
        }
    )
}
