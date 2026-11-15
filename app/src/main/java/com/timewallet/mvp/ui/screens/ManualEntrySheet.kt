package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.ManualEntry
import com.timewallet.mvp.ui.ManualEntryState
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletStroke
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * WALLY P1: form nhập giờ thủ công (bottom sheet). Chỉ có thời lượng, KHÔNG tự gán giờ bắt đầu/kết thúc.
 * Đường nhanh: mở -> chọn danh mục -> chọn mốc (15p/30p/1h/2h) -> Lưu (4 thao tác).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ManualEntrySheet(
    state: ManualEntryState,
    onChange: ((ManualEntryState) -> ManualEntryState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val selectedDate = runCatching { LocalDate.parse(state.dateLocal) }.getOrDefault(today)
    val totalMinutes = (state.hoursText.trim().toIntOrNull() ?: 0) * 60 + (state.minutesText.trim().toIntOrNull() ?: 0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Thêm giờ đã dùng", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = WalletInk)
            Text(
                "Chỉ ghi thời lượng, không tự gán giờ bắt đầu hay kết thúc.",
                fontSize = 12.sp,
                color = WalletMuted
            )

            SectionLabel("Danh mục")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TimeCategory.entries.forEach { c ->
                    SheetChip(
                        text = "${c.emoji} ${c.displayName}",
                        selected = state.categoryKey == c.key,
                        description = "Danh mục ${c.displayName}",
                        onClick = { onChange { it.copy(categoryKey = c.key) } }
                    )
                }
            }

            SectionLabel("Chọn nhanh")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ManualEntry.PRESET_MINUTES.forEach { m ->
                    SheetChip(
                        text = ManualEntry.presetLabel(m),
                        selected = totalMinutes == m,
                        description = "Thời lượng ${ManualEntry.presetLabel(m)}",
                        onClick = {
                            val (h, min) = ManualEntry.split(m)
                            onChange { it.copy(hoursText = h.toString(), minutesText = min.toString()) }
                        }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.hoursText,
                    onValueChange = { v -> onChange { it.copy(hoursText = v.filter(Char::isDigit).take(2)) } },
                    label = { Text("Giờ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.minutesText,
                    onValueChange = { v -> onChange { it.copy(minutesText = v.filter(Char::isDigit).take(2)) } },
                    label = { Text("Phút") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            SectionLabel("Ngày")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SheetChip("Hôm nay", selectedDate == today, "Ngày hôm nay") { onChange { it.copy(dateLocal = today.toString()) } }
                SheetChip("Hôm qua", selectedDate == today.minusDays(1), "Ngày hôm qua") {
                    onChange { it.copy(dateLocal = today.minusDays(1).toString()) }
                }
                SheetChip(
                    text = "Chọn ngày: " + selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    selected = selectedDate != today && selectedDate != today.minusDays(1),
                    description = "Chọn ngày khác",
                    onClick = { showDatePicker = true }
                )
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = { v -> onChange { it.copy(note = v.take(ManualEntry.MAX_NOTE_LENGTH)) } },
                label = { Text("Ghi chú (tùy chọn)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (state.error != null) {
                Text(
                    text = state.error,
                    color = Color(0xFFB3261E),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { contentDescription = "Lỗi: ${state.error}" }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !state.saving,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) { Text("Hủy") }
                Button(
                    onClick = onSave,
                    enabled = !state.saving,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WalletMint),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) { Text(if (state.saving) "Đang lưu…" else "Lưu", fontWeight = FontWeight.Bold) }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showDatePicker) {
        val todayUtcMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtcMillis
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange { it.copy(dateLocal = d.toString()) }
                    }
                    showDatePicker = false
                }) { Text("Chọn") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Hủy") } }
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = WalletMuted)
}

/** Chip có vùng chạm tối thiểu 48dp, có mô tả và trạng thái chọn cho TalkBack (không chỉ dựa vào màu). */
@Composable
private fun SheetChip(text: String, selected: Boolean, description: String, onClick: () -> Unit) {
    Box48(selected = selected, description = description, onClick = onClick) {
        Text(
            text = if (selected) "✓ $text" else text,
            fontSize = 13.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else WalletInk
        )
    }
}

@Composable
private fun Box48(selected: Boolean, description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) WalletMint else Color(0xFFF3F6F3))
            .border(BorderStroke(1.dp, if (selected) WalletMint else WalletStroke), RoundedCornerShape(14.dp))
            .semantics {
                contentDescription = description
                role = Role.RadioButton
                this.selected = selected
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}
