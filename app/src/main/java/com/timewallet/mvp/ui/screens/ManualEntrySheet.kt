package com.timewallet.mvp.ui.screens

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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.ManualEntry
import com.timewallet.mvp.domain.usecase.TimeRange
import com.timewallet.mvp.ui.ManualEntryState
import com.timewallet.mvp.ui.ManualMode
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMuted
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * v1.1.0 (A): màn ghi nhận thủ công thiết kế lại — "bạn đã làm gì" trước (Tag gần đây/thường dùng), rồi MỘT trong hai cách nhớ giờ:
 *  - Khoảng giờ: nhập giờ bắt đầu + giờ kết thúc (hoặc "Bây giờ"); ứng dụng tự tính số phút và hiện kết quả trước khi lưu.
 *  - Thời lượng: chỉ biết đã dành bao nhiêu phút (không bắt nhập mốc giờ, không tự gán mốc giờ).
 * Ghi chú và Nhóm đi theo Tag, chỉ hiện khi cần. Việc ghi vẫn qua đúng đường chung của sổ cái (LedgerValidator + commit nguyên tử).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ManualEntrySheet(
    state: ManualEntryState,
    suggestions: List<Tag>,
    allActiveTags: List<Tag>,
    onChange: ((ManualEntryState) -> ManualEntryState) -> Unit,
    onSelectTag: (String?) -> Unit,
    onCreateTag: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(state.note.isNotBlank()) }
    var showCategories by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val selectedDate = runCatching { LocalDate.parse(state.dateLocal) }.getOrDefault(today)
    val isToday = selectedDate == today
    val totalMinutes = (state.hoursText.trim().toIntOrNull() ?: 0) * 60 + (state.minutesText.trim().toIntOrNull() ?: 0)
    val nowHm = LocalTime.now().let { TimeRange.formatHm(it.hour * 60 + it.minute) }
    val rangePreview = if (state.mode == ManualMode.RANGE && state.startText.isNotBlank() && (state.endIsNow || state.endText.isNotBlank())) {
        TimeRange.compute(state.startText, if (state.endIsNow) TimeRange.End.Now else TimeRange.End.At(state.endText), nowHm, isToday)
    } else null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Bạn đã làm gì?", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = WalletInk)

            TagPicker(
                suggestions = suggestions,
                allActiveTags = allActiveTags,
                selectedTagId = state.tagId,
                query = state.tagQuery,
                onQueryChange = { q -> onChange { it.copy(tagQuery = q) } },
                onSelect = onSelectTag,
                onCreateClick = { onChange { it.copy(creatingTag = !it.creatingTag) } }
            )
            if (state.creatingTag) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.newTagName,
                        onValueChange = { v -> onChange { it.copy(newTagName = v.take(40)) } },
                        label = { Text("Tên Tag mới") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onCreateTag,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WalletMint),
                        modifier = Modifier.heightIn(min = 56.dp)
                    ) { Text("Tạo") }
                }
                Text("Tag mới thuộc nhóm đang chọn bên dưới.", fontSize = 11.5.sp, color = WalletMuted)
            }

            // Nhóm: đi theo Tag; hiện gọn một dòng, bấm để đổi (hoặc chọn nhóm khi không dùng Tag).
            val cat = TimeCategory.fromKeyOrNull(state.categoryKey)
            if (state.tagId == null || showCategories || cat == null || state.creatingTag) {
                SmallLabel(if (state.tagId == null) "Nhóm" else "Đổi nhóm")
                CategoryChoiceRow(state.categoryKey) { k -> onChange { it.copy(categoryKey = k) } }
            } else {
                TextButton(onClick = { showCategories = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Nhóm: ${cat.emoji} ${cat.displayName} · đổi", fontSize = 12.5.sp)
                }
            }

            SmallLabel("Bạn nhớ theo cách nào?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChip("Khoảng giờ", state.mode == ManualMode.RANGE, "Nhập giờ bắt đầu và kết thúc") { onChange { it.copy(mode = ManualMode.RANGE) } }
                ChoiceChip("Thời lượng", state.mode == ManualMode.DURATION, "Chỉ nhập số phút đã dùng") { onChange { it.copy(mode = ManualMode.DURATION) } }
            }

            if (state.mode == ManualMode.RANGE) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.startText,
                        onValueChange = { v -> onChange { it.copy(startText = v.filter(Char::isDigit).take(4)) } },
                        label = { Text("Bắt đầu") },
                        placeholder = { Text("14:00") },
                        singleLine = true,
                        visualTransformation = TimeDigitsTransformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = if (state.endIsNow) "Bây giờ ($nowHm)" else state.endText,
                        onValueChange = { v -> onChange { it.copy(endText = v.filter(Char::isDigit).take(4), endIsNow = false) } },
                        label = { Text("Kết thúc") },
                        placeholder = { Text("15:12") },
                        singleLine = true,
                        readOnly = state.endIsNow,
                        visualTransformation = if (state.endIsNow) VisualTransformation.None else TimeDigitsTransformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (isToday) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChoiceChip("Kết thúc: Bây giờ", state.endIsNow, "Giờ kết thúc là bây giờ") { onChange { it.copy(endIsNow = !it.endIsNow, endText = "") } }
                    }
                }
                when (rangePreview) {
                    is TimeRange.Result.Ok -> Text(
                        "= ${TimeRange.formatDuration(rangePreview.minutes)} (${rangePreview.startHm}–${rangePreview.endHm}${if (rangePreview.crossesMidnight) ", qua nửa đêm" else ""})",
                        fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = WalletMint,
                        modifier = Modifier.semantics { contentDescription = "Thời lượng tự tính ${TimeRange.formatDuration(rangePreview.minutes)}" }
                    )
                    is TimeRange.Result.Invalid -> Text(rangePreview.message, fontSize = 12.sp, color = Color(0xFFB3261E))
                    null -> Text("Nhập hai mốc giờ, Wally tự tính số phút.", fontSize = 12.sp, color = WalletMuted)
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ManualEntry.PRESET_MINUTES.forEach { m ->
                        ChoiceChip(ManualEntry.presetLabel(m), totalMinutes == m, "Thời lượng ${ManualEntry.presetLabel(m)}") {
                            val (h, min) = ManualEntry.split(m)
                            onChange { it.copy(hoursText = h.toString(), minutesText = min.toString()) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.hoursText,
                        onValueChange = { v -> onChange { it.copy(hoursText = v.filter(Char::isDigit).take(2)) } },
                        label = { Text("Giờ") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.minutesText,
                        onValueChange = { v -> onChange { it.copy(minutesText = v.filter(Char::isDigit).take(2)) } },
                        label = { Text("Phút") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ChoiceChip("Hôm nay", selectedDate == today, "Ngày hôm nay") { onChange { it.copy(dateLocal = today.toString(), endIsNow = it.endIsNow) } }
                ChoiceChip("Hôm qua", selectedDate == today.minusDays(1), "Ngày hôm qua") { onChange { it.copy(dateLocal = today.minusDays(1).toString(), endIsNow = false) } }
                ChoiceChip(
                    text = "Ngày khác: " + selectedDate.format(DateTimeFormatter.ofPattern("dd/MM")),
                    selected = selectedDate != today && selectedDate != today.minusDays(1),
                    description = "Chọn ngày khác",
                    onClick = { showDatePicker = true }
                )
            }

            if (showNote) {
                OutlinedTextField(
                    value = state.note,
                    onValueChange = { v -> onChange { it.copy(note = v.take(ManualEntry.MAX_NOTE_LENGTH)) } },
                    label = { Text("Ghi chú (tùy chọn)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                TextButton(onClick = { showNote = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("＋ Ghi chú", fontSize = 12.5.sp) }
            }

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
                        onChange { it.copy(dateLocal = d.toString(), endIsNow = if (d == today) it.endIsNow else false) }
                    }
                    showDatePicker = false
                }) { Text("Chọn") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Hủy") } }
        ) { DatePicker(state = pickerState) }
    }
}

/**
 * Ô giờ gõ nhanh: ô CHỈ giữ chữ số (tối đa 4); dấu ":" chỉ để HIỂN THỊ ("1415" → 14:15, "930" → 9:30) nên con trỏ không bị xê dịch khi gõ.
 * Cách hiểu chữ số nằm ở `TimeRange.parseMinuteOfDay`.
 */
internal object TimeDigitsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val d = text.text
        val colonAt = when {
            d.length == 3 && d[0] >= '3' -> 1
            d.length >= 3 -> 2
            else -> -1
        }
        if (colonAt < 0) return TransformedText(text, OffsetMapping.Identity)
        val shown = d.substring(0, colonAt) + ":" + d.substring(colonAt)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = if (offset >= colonAt) offset + 1 else offset
            override fun transformedToOriginal(offset: Int): Int = if (offset > colonAt) offset - 1 else offset
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}
