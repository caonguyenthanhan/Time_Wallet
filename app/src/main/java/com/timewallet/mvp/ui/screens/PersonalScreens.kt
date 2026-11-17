package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import com.timewallet.mvp.domain.model.GoalGroupState
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.CoachSuggestion
import com.timewallet.mvp.domain.usecase.GoalRules
import com.timewallet.mvp.domain.usecase.TagRules
import com.timewallet.mvp.domain.usecase.TimeRange
import com.timewallet.mvp.ui.CoachUiState
import com.timewallet.mvp.ui.GoalEditorState
import com.timewallet.mvp.ui.TagManagerState
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletStroke

private val ErrorRed = Color(0xFFB3261E)

/** Chip chọn có vùng chạm >= 48dp, có mô tả và trạng thái chọn cho TalkBack (không chỉ dựa vào màu). */
@Composable
fun ChoiceChip(text: String, selected: Boolean, description: String, onClick: () -> Unit) {
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
    ) {
        Text(
            text = if (selected) "✓ $text" else text,
            fontSize = 13.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else WalletInk
        )
    }
}

@Composable
fun SmallLabel(text: String) {
    Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = WalletMuted)
}

/** Dãy Category 9 nhóm (dùng khi tạo Tag, mục tiêu theo nhóm, đổi nhóm). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChoiceRow(selectedKey: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TimeCategory.entries.forEach { c ->
            ChoiceChip("${c.emoji} ${c.displayName}", selectedKey == c.key, "Nhóm ${c.displayName}") { onSelect(c.key) }
        }
    }
}

/**
 * Chọn Tag ít thao tác: Tag gần đây / thường dùng trước (do [TagRules.suggestions]), ô tìm chỉ hiện khi có nhiều Tag.
 * Bấm lại Tag đang chọn = bỏ chọn.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPicker(
    suggestions: List<Tag>,
    allActiveTags: List<Tag>,
    selectedTagId: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (String?) -> Unit,
    onCreateClick: (() -> Unit)?
) {
    val searching = query.isNotBlank()
    val shown = if (searching) TagRules.search(allActiveTags, query) else suggestions
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        shown.forEach { t ->
            ChoiceChip("${t.category.emoji} ${t.name}", selectedTagId == t.id, "Tag ${t.name}") { onSelect(t.id) }
        }
        if (onCreateClick != null) {
            ChoiceChip("＋ Tag mới", false, "Tạo Tag mới", onCreateClick)
        }
    }
    if (allActiveTags.size > suggestions.size || searching) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Tìm Tag") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (searching && shown.isEmpty()) {
            Text("Không thấy Tag nào. Bấm \"＋ Tag mới\" để tạo.", fontSize = 12.sp, color = WalletMuted)
        }
    }
}

// ------------------------------------------------------------------------------------------------ thẻ Mục tiêu & Coach (Tổng quan)

@Composable
fun GoalProgressRow(summary: GoalRules.Summary) {
    val p = summary.progress
    val g = summary.latest
    val per = if (g.period == GoalPeriod.DAY) "ngày" else "tuần"
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(summary.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WalletInk, modifier = Modifier.weight(1f))
            Text(
                (if (g.kind == GoalKind.MIN) "≥ " else "≤ ") + TimeRange.formatDuration((g.targetSeconds / 60).toInt()) + "/$per",
                fontSize = 12.sp, color = WalletMuted
            )
        }
        if (p != null) {
            val used = TimeRange.formatDuration((p.usedSeconds / 60).toInt())
            val tgt = TimeRange.formatDuration((p.targetSeconds / 60).toInt())
            val status = when {
                g.kind == GoalKind.MIN && p.met -> "Đã đạt ✓"
                g.kind == GoalKind.MIN -> "Còn thiếu ${TimeRange.formatDuration((p.remainingSeconds / 60).toInt())}"
                p.met -> "Còn ${TimeRange.formatDuration((p.remainingSeconds / 60).toInt())} trong giới hạn"
                else -> "Vượt ${TimeRange.formatDuration((p.overSeconds / 60).toInt())}"
            }
            LinearProgressIndicator(
                progress = { p.fraction },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    .semantics { contentDescription = "${summary.name}: $used trên $tgt. $status" },
                color = if (g.kind == GoalKind.MAX && !p.met) Color(0xFFD97706) else WalletMint,
                trackColor = Color(0xFFE6EEE9)
            )
            Text("$used / $tgt · $status", fontSize = 12.sp, color = WalletMuted)
        } else {
            Text(if (summary.state == GoalGroupState.PAUSED) "Đang tạm dừng" else "Đã kết thúc", fontSize = 12.sp, color = WalletMuted)
        }
    }
}

@Composable
fun PersonalHubCard(
    goalSummaries: List<GoalRules.Summary>,
    coach: CoachUiState,
    aiEnabled: Boolean,
    onOpenGoals: () -> Unit,
    onOpenTags: () -> Unit,
    onAskCoach: (Int) -> Unit,
    onDismissCoach: () -> Unit,
    onConfirmSuggestion: (Int) -> Unit,
    onDismissSuggestion: (Int) -> Unit
) {
    SoftCard(modifier = Modifier.padding(top = 12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(text = "Mục tiêu của bạn", actionText = "Quản lý", onAction = onOpenGoals)
            if (goalSummaries.isEmpty()) {
                Text(
                    "Đặt mục tiêu theo hoạt động (Tag) hoặc nhóm, ví dụ \"Chạy bộ ít nhất 30 phút mỗi ngày\". Wally sẽ theo dõi tiến độ giúp bạn.",
                    fontSize = 12.5.sp, color = WalletMuted
                )
                Button(
                    onClick = onOpenGoals,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WalletMint),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("＋ Tạo mục tiêu", fontWeight = FontWeight.Bold) }
            } else {
                goalSummaries.take(3).forEach { GoalProgressRow(it) }
            }

            SectionTitle(text = "Wally Coach")
            FlowRow2 {
                Button(
                    onClick = { onAskCoach(1) },
                    enabled = !coach.loading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WalletMint),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Hôm nay", fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = { onAskCoach(7) },
                    enabled = !coach.loading,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("7 ngày qua") }
                TextButton(onClick = onOpenTags, modifier = Modifier.heightIn(min = 48.dp)) { Text("Quản lý Tag") }
            }
            if (!aiEnabled && !coach.requested) {
                Text("Số liệu do ứng dụng tính. Bật AI để Wally kể câu chuyện từ dữ liệu.", fontSize = 12.sp, color = WalletMuted)
            }
            if (coach.requested || coach.loading) {
                CoachPanel(coach, onDismissCoach, onConfirmSuggestion, onDismissSuggestion)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow2(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
}

@Composable
fun CoachPanel(
    coach: CoachUiState,
    onDismiss: () -> Unit,
    onConfirmSuggestion: (Int) -> Unit,
    onDismissSuggestion: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF3F7F4)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (coach.rangeDays == 1) "Hôm nay" else "${coach.rangeDays} ngày qua", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WalletMuted, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Đóng", fontSize = 12.sp) }
        }
        coach.facts.forEach { Text("• $it", fontSize = 13.sp, color = WalletInk) }
        if (coach.loading) Text("Wally đang đọc số liệu của bạn…", fontSize = 13.sp, color = WalletMuted)
        coach.story?.let { story ->
            Text("Wally (AI)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WalletMint)
            Text(story, fontSize = 14.sp, color = WalletInk)
        }
        coach.note?.let { Text(it, fontSize = 12.5.sp, color = WalletMuted) }
        coach.suggestions.forEachIndexed { i, s ->
            SuggestionCard(s, onConfirm = { onConfirmSuggestion(i) }, onDismiss = { onDismissSuggestion(i) })
        }
    }
}

@Composable
private fun SuggestionCard(s: CoachSuggestion, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val per = if (s.period == GoalPeriod.DAY) "ngày" else "tuần"
    val kind = if (s.kind == GoalKind.MIN) "ít nhất" else "tối đa"
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(BorderStroke(1.dp, WalletStroke), RoundedCornerShape(14.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Gợi ý mục tiêu (chưa tạo)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = WalletMuted)
        Text("${s.targetLabel}: $kind ${TimeRange.formatDuration((s.targetSeconds / 60).toInt())}/$per", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WalletInk)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onConfirm, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = WalletMint), modifier = Modifier.heightIn(min = 48.dp)) { Text("Tạo mục tiêu") }
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Bỏ qua") }
        }
    }
}

// ------------------------------------------------------------------------------------------------ Quản lý Tag

@Composable
fun TagManagerDialog(
    state: TagManagerState,
    tags: List<Tag>,
    onChange: ((TagManagerState) -> TagManagerState) -> Unit,
    onCreate: () -> Unit,
    onRename: (String) -> Unit,
    onSetCategory: (String, String) -> Unit,
    onArchive: (String) -> Unit,
    onRestore: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val active = tags.filter { !it.isArchived }.sortedBy { it.nameKey }
    val archived = tags.filter { it.isArchived }.sortedBy { it.nameKey }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Tag của bạn", fontWeight = FontWeight.ExtraBold, color = WalletInk) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Nhóm là loại chung; Tag là hoạt động cụ thể của bạn (Chạy bộ, Đọc sách, Chơi game). Đổi tên hoặc lưu trữ Tag không làm mất lịch sử.",
                    fontSize = 12.sp, color = WalletMuted
                )
                SmallLabel("Tag mới")
                OutlinedTextField(
                    value = state.newName,
                    onValueChange = { v -> onChange { it.copy(newName = v.take(TagRules.MAX_NAME_LENGTH)) } },
                    label = { Text("Tên Tag") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CategoryChoiceRow(state.newCategoryKey) { k -> onChange { it.copy(newCategoryKey = k) } }
                Button(
                    onClick = onCreate,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WalletMint),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Tạo Tag", fontWeight = FontWeight.Bold) }
                state.error?.let { Text(it, color = ErrorRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { contentDescription = "Lỗi: $it" }) }

                SmallLabel("Đang dùng (${active.size})")
                if (active.isEmpty()) Text("Chưa có Tag nào.", fontSize = 12.5.sp, color = WalletMuted)
                active.forEach { t ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF3F7F4)).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("${t.category.emoji} ${t.name}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WalletInk)
                        Text("Nhóm: ${t.category.displayName}", fontSize = 11.5.sp, color = WalletMuted)
                        if (state.renamingId == t.id) {
                            OutlinedTextField(
                                value = state.renameText,
                                onValueChange = { v -> onChange { it.copy(renameText = v.take(TagRules.MAX_NAME_LENGTH)) } },
                                label = { Text("Tên mới") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onRename(t.id) }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = WalletMint), modifier = Modifier.heightIn(min = 48.dp)) { Text("Lưu tên") }
                                TextButton(onClick = { onChange { it.copy(renamingId = null) } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Hủy") }
                            }
                        } else if (state.recategorizingId == t.id) {
                            CategoryChoiceRow(t.category.key) { k -> onSetCategory(t.id, k) }
                            TextButton(onClick = { onChange { it.copy(recategorizingId = null) } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Hủy") }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                TextButton(onClick = { onChange { it.copy(renamingId = t.id, renameText = t.name, recategorizingId = null) } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Đổi tên", fontSize = 12.5.sp) }
                                TextButton(onClick = { onChange { it.copy(recategorizingId = t.id, renamingId = null) } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Đổi nhóm", fontSize = 12.5.sp) }
                                TextButton(onClick = { onArchive(t.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Lưu trữ", fontSize = 12.5.sp) }
                            }
                        }
                    }
                }
                if (archived.isNotEmpty()) {
                    TextButton(onClick = { onChange { it.copy(showArchived = !it.showArchived) } }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text((if (state.showArchived) "Ẩn" else "Hiện") + " Tag đã lưu trữ (${archived.size})")
                    }
                    if (state.showArchived) {
                        archived.forEach { t ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${t.category.emoji} ${t.name}", fontSize = 13.sp, color = WalletMuted, modifier = Modifier.weight(1f))
                                TextButton(onClick = { onRestore(t.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Khôi phục") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Xong", fontWeight = FontWeight.Bold) } }
    )
}

// ------------------------------------------------------------------------------------------------ Mục tiêu

@Composable
fun GoalsDialog(
    summaries: List<GoalRules.Summary>,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onEnd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var confirmEnd by remember { mutableStateOf<String?>(null) }
    confirmEnd?.let { gid ->
        AlertDialog(
            onDismissRequest = { confirmEnd = null },
            containerColor = Color.White,
            title = { Text("Kết thúc mục tiêu?", fontWeight = FontWeight.ExtraBold, color = WalletInk) },
            text = { Text("Mục tiêu sẽ dừng từ hôm nay. Dữ liệu hoạt động và tiến độ trước đó được giữ nguyên.", fontSize = 12.sp, color = WalletMuted) },
            confirmButton = { Button(onClick = { onEnd(gid); confirmEnd = null }) { Text("Kết thúc", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmEnd = null }) { Text("Hủy") } }
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Mục tiêu thời gian", fontWeight = FontWeight.ExtraBold, color = WalletInk) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (summaries.isEmpty()) Text("Chưa có mục tiêu nào.", fontSize = 13.sp, color = WalletMuted)
                summaries.forEach { s ->
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF3F7F4)).padding(10.dp)) {
                        GoalProgressRow(s)
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextButton(onClick = { onEdit(s.groupId) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Sửa", fontSize = 12.5.sp) }
                            if (s.state == GoalGroupState.PAUSED) TextButton(onClick = { onResume(s.groupId) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Tiếp tục", fontSize = 12.5.sp) }
                            else TextButton(onClick = { onPause(s.groupId) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Tạm dừng", fontSize = 12.5.sp) }
                            TextButton(onClick = { confirmEnd = s.groupId }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Kết thúc", fontSize = 12.5.sp) }
                        }
                    }
                }
                Button(
                    onClick = onCreate,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WalletMint),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("＋ Mục tiêu mới", fontWeight = FontWeight.Bold) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng", fontWeight = FontWeight.Bold) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalEditorDialog(
    state: GoalEditorState,
    activeTags: List<Tag>,
    onChange: ((GoalEditorState) -> GoalEditorState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val editing = state.editingGroupId != null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(if (editing) "Sửa mục tiêu" else "Mục tiêu mới", fontWeight = FontWeight.ExtraBold, color = WalletInk) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!editing) {
                    SmallLabel("Áp dụng cho")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChoiceChip("Hoạt động (Tag)", state.targetType == GoalTargetType.TAG, "Mục tiêu theo Tag") { onChange { it.copy(targetType = GoalTargetType.TAG, targetKey = null) } }
                        ChoiceChip("Nhóm", state.targetType == GoalTargetType.CATEGORY, "Mục tiêu theo nhóm") { onChange { it.copy(targetType = GoalTargetType.CATEGORY, targetKey = null) } }
                    }
                    if (state.targetType == GoalTargetType.TAG) {
                        if (activeTags.isEmpty()) Text("Chưa có Tag. Hãy tạo Tag (ví dụ \"Chạy bộ\") trong Quản lý Tag trước.", fontSize = 12.5.sp, color = WalletMuted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            activeTags.forEach { t -> ChoiceChip("${t.category.emoji} ${t.name}", state.targetKey == t.id, "Tag ${t.name}") { onChange { it.copy(targetKey = t.id) } } }
                        }
                    } else {
                        CategoryChoiceRow(state.targetKey) { k -> onChange { it.copy(targetKey = k) } }
                    }
                }
                SmallLabel("Loại mục tiêu")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceChip("Ít nhất", state.kind == GoalKind.MIN, "Đạt tối thiểu") { onChange { it.copy(kind = GoalKind.MIN) } }
                    ChoiceChip("Tối đa", state.kind == GoalKind.MAX, "Giới hạn tối đa") { onChange { it.copy(kind = GoalKind.MAX) } }
                }
                SmallLabel("Kỳ")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceChip("Mỗi ngày", state.period == GoalPeriod.DAY, "Theo ngày") { onChange { it.copy(period = GoalPeriod.DAY) } }
                    ChoiceChip("Mỗi tuần", state.period == GoalPeriod.WEEK, "Theo tuần Thứ Hai đến Chủ Nhật") { onChange { it.copy(period = GoalPeriod.WEEK) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.hoursText,
                        onValueChange = { v -> onChange { it.copy(hoursText = v.filter(Char::isDigit).take(3)) } },
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
                if (editing) Text("Thay đổi áp dụng từ hôm nay; dữ liệu và tiến độ trước đó không đổi.", fontSize = 12.sp, color = WalletMuted)
                state.error?.let { Text(it, color = ErrorRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { contentDescription = "Lỗi: $it" }) }
            }
        },
        confirmButton = { Button(onClick = onSave, colors = ButtonDefaults.buttonColors(containerColor = WalletMint)) { Text("Lưu", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}
