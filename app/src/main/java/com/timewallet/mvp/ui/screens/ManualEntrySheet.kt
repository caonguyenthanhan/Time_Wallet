package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
import com.timewallet.mvp.ui.theme.WalletCardBg
import com.timewallet.mvp.ui.theme.WalletDangerSolid
import com.timewallet.mvp.ui.theme.WalletDangerText
import com.timewallet.mvp.ui.theme.WalletDivider
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintAccent
import com.timewallet.mvp.ui.theme.WalletMintSoft
import com.timewallet.mvp.ui.theme.WalletMintTint
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletStroke
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
    onDismiss: () -> Unit,
    onClean: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by remember(state.sessionId) { mutableStateOf(false) }
    var showCategories by remember(state.sessionId) { mutableStateOf(false) }
    var showCleanConfirm by remember(state.sessionId) { mutableStateOf(false) }
    val today = LocalDate.now()
    val isBatchMultiSlot = state.batchSlots.isNotEmpty()
    val hasDraftData = !isBatchMultiSlot && state.hasUserData(today.toString())
    val selectedDate = runCatching { LocalDate.parse(state.dateLocal) }.getOrDefault(today)
    val isToday = selectedDate == today
    val isYesterday = selectedDate == today.minusDays(1)
    val totalMinutes = (state.hoursText.trim().toIntOrNull() ?: 0) * 60 + (state.minutesText.trim().toIntOrNull() ?: 0)
    val nowHm = LocalTime.now().let { TimeRange.formatHm(it.hour * 60 + it.minute) }
    val rangePreview = if (state.mode == ManualMode.RANGE && state.startText.isNotBlank() && (state.endIsNow || state.endText.isNotBlank())) {
        TimeRange.compute(
            state.startText,
            if (state.endIsNow) TimeRange.End.Now else TimeRange.End.At(state.endText),
            nowHm,
            isToday,
            allowEndNowFromYesterday = isYesterday
        )
    } else null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = WalletCardBg) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(WalletMintSoft)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "+ Ghi mới",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WalletMintAccent
                        )
                    }
                    Text("Bạn đã làm gì?", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = WalletInk)
                }
                if (hasDraftData) {
                    Text(
                        "Tự động lưu nháp",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WalletMintAccent
                    )
                }
            }

            // Nhập tiêu đề / ghi nhanh câu ngắn như "Đọc sách 30 phút" ngay tại đầu biểu mẫu (không bắt buộc mốc giờ).
            OutlinedTextField(
                value = state.note,
                onValueChange = { v -> onChange { it.copy(note = v.take(ManualEntry.MAX_NOTE_LENGTH)) } },
                label = { Text("Tiêu đề / ghi nhanh (vd: Đọc sách 30 phút)") },
                placeholder = { Text("Đọc sách, Code UI, Nghỉ trưa…", fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            val quickSuggestion = remember(state.note, state.dateLocal, allActiveTags) {
                ManualEntry.parseQuickInput(state.note, allActiveTags, selectedDate)
            }
            if (quickSuggestion != null && quickSuggestion.durationMinutes != null) {
                val mins = quickSuggestion.durationMinutes
                val qCat = TimeCategory.fromKeyOrNull(quickSuggestion.categoryKey)
                val alreadyMatches = state.note.trim() == quickSuggestion.cleanTitle &&
                    ((quickSuggestion.startHm != null && state.mode == ManualMode.RANGE) ||
                        (quickSuggestion.startHm == null && state.mode == ManualMode.DURATION && totalMinutes == mins))
                if (!alreadyMatches) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = WalletMintTint,
                        border = BorderStroke(1.dp, WalletStroke),
                        modifier = Modifier
                            .fillMaxWidth()
                            .walletClickable(onClickLabel = "Áp nhanh ${quickSuggestion.cleanTitle}") {
                                val (h, m) = ManualEntry.split(mins)
                                val hasClockBounds = quickSuggestion.startHm != null && quickSuggestion.endHm != null
                                onChange { cur ->
                                    if (hasClockBounds) {
                                        cur.copy(
                                            note = quickSuggestion.cleanTitle,
                                            categoryKey = quickSuggestion.categoryKey ?: cur.categoryKey,
                                            tagId = quickSuggestion.tagId ?: cur.tagId,
                                            mode = ManualMode.RANGE,
                                            startText = quickSuggestion.startHm!!.filter(Char::isDigit).take(4).padStart(4, '0'),
                                            endText = quickSuggestion.endHm!!.filter(Char::isDigit).take(4).padStart(4, '0'),
                                            endIsNow = false
                                        )
                                    } else {
                                        cur.copy(
                                            note = quickSuggestion.cleanTitle,
                                            categoryKey = quickSuggestion.categoryKey ?: cur.categoryKey,
                                            tagId = quickSuggestion.tagId ?: cur.tagId,
                                            mode = ManualMode.DURATION,
                                            hoursText = h.toString(),
                                            minutesText = m.toString()
                                        )
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            qCat?.let { CategoryIcon(it, size = 16.dp, tint = WalletMintAccent) }
                            val rangeDesc = if (quickSuggestion.startHm != null && quickSuggestion.endHm != null) {
                                "${quickSuggestion.startHm}–${quickSuggestion.endHm} (${TimeRange.formatDuration(mins)})"
                            } else {
                                "${TimeRange.formatDuration(mins)} (chỉ thời lượng)"
                            }
                            Text(
                                text = "Áp nhanh: ${quickSuggestion.cleanTitle} · $rangeDesc",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalletMintAccent,
                                modifier = Modifier.weight(1f)
                            )
                            Text("Áp", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = WalletMintAccent)
                        }
                    }
                }
            }

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
                        colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                        modifier = Modifier.heightIn(min = 56.dp)
                    ) { Text("Tạo") }
                }
                Text("Tag mới thuộc nhóm đang chọn bên dưới.", fontSize = 11.5.sp, color = WalletMuted)
            }

            // Nhóm: đi theo Tag; hiện gọn một dòng, bấm để đổi (hoặc chọn nhóm khi không dùng Tag).
            val cat = TimeCategory.fromKeyOrNull(state.categoryKey)
            if (state.tagId == null || showCategories || cat == null || state.creatingTag) {
                SmallLabel(if (state.tagId == null) "Danh mục" else "Đổi nhóm")
                CategoryChoiceRow(state.categoryKey) { k -> onChange { it.copy(categoryKey = k) } }
            } else {
                TextButton(onClick = { showCategories = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        CategoryIcon(category = cat, size = 15.dp, tint = WalletMintAccent)
                        Text("Nhóm: ${cat.displayName} · đổi", fontSize = 12.5.sp)
                    }
                }
            }

            SmallLabel(if (isBatchMultiSlot) "Khung giờ đã chọn (${state.batchSlots.size} khoảng độc lập)" else "Bạn nhớ theo cách nào?")
            if (isBatchMultiSlot) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = WalletMintTint,
                    border = BorderStroke(1.dp, WalletStroke),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.batchSlots.forEach { slot ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(WalletCardBg)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${slot.dateLocal.substring(5)} · ${slot.startHm}–${if (slot.endMinute >= 1440) "24:00" else slot.endHm}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WalletMintAccent
                                    )
                                }
                            }
                        }
                        val batchMins = state.batchSlots.sumOf { it.durationMinutes }
                        Text(
                            text = "Tạo ${state.batchSlots.size} bản ghi riêng cùng tiêu đề/danh mục · Tổng ${TimeRange.formatDuration(batchMins)}",
                            fontSize = 11.5.sp,
                            color = WalletMuted
                        )
                    }
                }
            } else {
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
                if (isToday || isYesterday) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChoiceChip("Kết thúc: Bây giờ", state.endIsNow, "Giờ kết thúc là bây giờ") { onChange { it.copy(endIsNow = !it.endIsNow, endText = "") } }
                    }
                }
                when (rangePreview) {
                    is TimeRange.Result.Ok -> {
                        val nowMs = System.currentTimeMillis()
                        val epochPair = com.timewallet.mvp.domain.usecase.LedgerValidator.parseTimeRangeToEpochMillis(
                            selectedDate.toString(), rangePreview.startHm, rangePreview.endHm
                        )
                        val isFutureRange = state.saveAsPlanned || selectedDate.isAfter(today) ||
                            (epochPair != null && epochPair.first > nowMs)
                        val isOngoingRange = !isFutureRange && epochPair != null &&
                            epochPair.first <= nowMs && epochPair.second > nowMs
                        Text(
                            "= ${TimeRange.formatDuration(rangePreview.minutes)} (${rangePreview.startHm}–${rangePreview.endHm}${if (rangePreview.crossesMidnight) ", qua nửa đêm" else ""})",
                            fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = WalletMint,
                            modifier = Modifier.semantics { contentDescription = "Thời lượng tự tính ${TimeRange.formatDuration(rangePreview.minutes)}" }
                        )
                        if (isFutureRange) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                GlyphIcon(WalletGlyph.NOTIFY, color = Color(0xFFA24C18), size = 14.dp)
                                Text(
                                    "Dự kiến · chưa tính vào thời gian thực tế; sẽ nhắc xác nhận lúc ${rangePreview.endHm}.",
                                    fontSize = 11.5.sp, color = Color(0xFFA24C18)
                                )
                            }
                        } else if (isOngoingRange) {
                            val elapsedMins = ((nowMs - epochPair!!.first).coerceAtLeast(0L) / 60_000L).toInt()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                GlyphIcon(WalletGlyph.NOTIFY, color = Color(0xFF185FA5), size = 14.dp)
                                Text(
                                    "Đang diễn ra · hiện đã qua ${TimeRange.formatDuration(elapsedMins)}; sẽ nhắc xác nhận lúc ${rangePreview.endHm}.",
                                    fontSize = 11.5.sp, color = Color(0xFF185FA5)
                                )
                            }
                        }
                    }
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
            }

            if (!isBatchMultiSlot) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ChoiceChip("Hôm nay", selectedDate == today, "Ngày hôm nay") { onChange { it.copy(dateLocal = today.toString(), endIsNow = it.endIsNow) } }
                    ChoiceChip("Hôm qua", selectedDate == today.minusDays(1), "Ngày hôm qua") { onChange { it.copy(dateLocal = today.minusDays(1).toString(), endIsNow = it.endIsNow) } }
                    ChoiceChip("Ngày mai", selectedDate == today.plusDays(1), "Ngày mai (Dự kiến)") { onChange { it.copy(dateLocal = today.plusDays(1).toString(), endIsNow = false) } }
                    ChoiceChip(
                        text = "Ngày khác: " + selectedDate.format(DateTimeFormatter.ofPattern("dd/MM")),
                        selected = selectedDate != today && selectedDate != today.minusDays(1) && selectedDate != today.plusDays(1),
                        description = "Chọn ngày khác",
                        onClick = { showDatePicker = true }
                    )
                }
            }
            if (isBatchMultiSlot || !selectedDate.isAfter(today)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceChip("Lưu làm Dự kiến", state.saveAsPlanned, "Lưu ở trạng thái Dự kiến, chưa tính vào thực tế") {
                        onChange { it.copy(saveAsPlanned = !it.saveAsPlanned) }
                    }
                    if (!state.saveAsPlanned && !selectedDate.isAfter(today)) {
                        ChoiceChip("Đánh dấu lãng phí", state.isMarkedWaste, "Đưa vào tầng Lãng phí trên biểu đồ 24h") {
                            onChange { it.copy(isMarkedWaste = !it.isMarkedWaste) }
                        }
                    }
                }
            }
                Spacer(modifier = Modifier.height(6.dp))
            }

            HorizontalDivider(color = WalletDivider)
            if (state.error != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.semantics { contentDescription = "Lỗi: ${state.error}" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GlyphIcon(WalletGlyph.ERROR, color = WalletDangerText, size = 15.dp)
                    Text(
                        text = state.error,
                        color = WalletDangerText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (hasDraftData) "Thoát sẽ giữ nháp · Clean để làm mới" else "Nháp tự lưu khi bạn nhập",
                    fontSize = 11.5.sp,
                    color = WalletMuted,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { if (hasDraftData) showCleanConfirm = true else onClean() },
                    enabled = !state.saving,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Clean · Xóa nhập liệu" }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        GlyphIcon(WalletGlyph.ERASER, color = WalletDangerText, size = 15.dp)
                        Text("Clean · Xóa nhập liệu", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = WalletDangerText)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !state.saving,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .semantics { contentDescription = "Thoát và giữ bản nháp" }
                ) { Text("Thoát") }
                Button(
                    onClick = onSave,
                    enabled = !state.saving,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) { Text(if (state.saving) "Đang lưu…" else "Lưu", fontWeight = FontWeight.Bold) }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    if (showCleanConfirm) {
        AlertDialog(
            onDismissRequest = { showCleanConfirm = false },
            containerColor = WalletCardBg,
            title = { Text("Xóa nhập liệu đang có?", fontWeight = FontWeight.ExtraBold, color = WalletInk) },
            text = {
                Text(
                    "Toàn bộ nội dung nháp đang nhập (tag, nhóm, ngày, giờ, ghi chú và tùy chọn) sẽ bị xóa sạch để bắt đầu mới. Hoạt động đã lưu không bị ảnh hưởng.",
                    fontSize = 12.5.sp,
                    color = WalletMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCleanConfirm = false
                        onClean()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WalletDangerSolid, contentColor = Color.White)
                ) {
                    Text("Xóa nhập liệu", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanConfirm = false }) {
                    Text("Giữ lại", color = WalletMuted)
                }
            }
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        val allowNow = d == today || d == today.minusDays(1)
                        onChange { it.copy(dateLocal = d.toString(), endIsNow = if (allowNow) it.endIsNow else false) }
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
