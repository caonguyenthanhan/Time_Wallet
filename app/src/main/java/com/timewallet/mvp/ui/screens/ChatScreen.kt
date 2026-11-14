package com.timewallet.mvp.ui.screens

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timewallet.mvp.data.local.AiPreferences
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.AiFault
import com.timewallet.mvp.data.ai.GuardHealth
import com.timewallet.mvp.domain.usecase.OutcomeSource
import com.timewallet.mvp.domain.usecase.ChatAnswer
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.ui.EditableProposalDraft
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMicroBg
import com.timewallet.mvp.ui.theme.WalletMicroText
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletStroke

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    chatInputText: String,
    isAnalyzing: Boolean,
    chatFault: AiFault?,
    lastAnalysisSource: OutcomeSource?,
    onCancelAnalysis: () -> Unit,
    onDismissError: () -> Unit,
    onManualEntry: () -> Unit,
    chatAnswer: ChatAnswer?,
    onDismissAnswer: () -> Unit,
    proposals: List<EditableProposalDraft>,
    expandedProposalId: String?,
    aiPreferences: AiPreferences,
    onChatInputChange: (String) -> Unit,
    onApplyExample: (String) -> Unit,
    onAnalyzeClick: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onToggleExpandProposal: (String) -> Unit,
    onUpdateProposal: (String, (EditableProposalDraft) -> EditableProposalDraft) -> Unit,
    onDiscardProposal: (String) -> Unit,
    onClearAllProposals: () -> Unit,
    onConfirmSingleProposal: (String) -> Unit,
    onConfirmAllProposals: () -> Unit
) {
    val examples = listOf(
        "Ghi 5 hoạt động" to "Từ 8h đến 9h30 làm dự án, đọc sách 45 phút, chạy bộ 30 phút, ăn trưa 30 phút, nghỉ ngơi 20 phút",
        "13:00–18:00 làm việc" to "Từ 13:00 đến 18:00 làm việc tập trung",
        "Học 45p (chưa gắn giờ)" to "Hôm nay học kỹ thuật Feynman 45 phút",
        "❓ Hôm nay dùng bao nhiêu?" to "Hôm nay tôi đã dùng bao nhiêu giờ?",
        "❓ Tuần này vs tuần trước" to "So sánh tuần này với tuần trước"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        PageIntroHeader(
            eyebrow = "WALLY · TRỢ LÝ THỜI GIAN",
            title = "Kể lại ngày của bạn.",
            subtitle = "Kể lại ngày của bạn hoặc hỏi số liệu. Wally dùng AI để hiểu; mọi bản ghi vẫn cần bạn xác nhận."
        )

        // Example Chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            examples.forEach { (label, fullSentence) ->
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFDBE8DD), RoundedCornerShape(100.dp))
                        .semantics { role = Role.Button }
                        .clickable { onApplyExample(fullSentence) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF3E6B56)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input Composer Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE1E9E0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(11.dp)) {
                OutlinedTextField(
                    value = chatInputText,
                    onValueChange = onChatInputChange,
                    placeholder = {
                        Text(
                            "Nhập 1 hoặc nhiều hoạt động (VD: 8h-9h30 làm dự án, đọc sách 45p, chạy bộ 30p...)",
                            fontSize = 12.sp
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(11.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val modeText = if (aiPreferences.optInEnabled && aiPreferences.apiKey.isNotBlank()) {
                        "✦ AI (${aiPreferences.trustedHost}) · ⚙"
                    } else {
                        "✦ Chưa cấu hình AI · ⚙"
                    }

                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF4F5F2))
                            .semantics { role = Role.Button }
                            .clickable(onClickLabel = "Mở cấu hình AI", onClick = onOpenAiSettings)
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = modeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF5D7064)
                        )
                    }

                    if (isAnalyzing && !GuardHealth.isCancelCommand(chatInputText)) {
                        OutlinedButton(
                            onClick = onCancelAnalysis,
                            shape = RoundedCornerShape(11.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(text = "Đang hỏi Wally… Hủy ✕", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else Button(
                        onClick = onAnalyzeClick,
                        modifier = Modifier.heightIn(min = 48.dp),
                        // AI-01 CLOSURE: cho phép gửi lệnh hủy khi đang phân tích (nút vẫn khóa với câu thường).
                        enabled = !isAnalyzing || GuardHealth.isCancelCommand(chatInputText),
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WalletMint),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = "Tách đề xuất ↗",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // WALLY: lỗi AI không được im lặng; luôn có Thử lại và đường ghi thủ công. Nội dung đã nhập được giữ.
        if (chatFault != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E8)),
                border = BorderStroke(1.dp, Color(0xFFF0DFB4))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val needsSetup = chatFault == AiFault.NOT_CONFIGURED || chatFault == AiFault.CONFIG || chatFault == AiFault.AUTH
                    Text(
                        text = if (lastAnalysisSource == OutcomeSource.NOT_CONFIGURED) "Wally chưa được cấu hình AI" else "Wally chưa trả lời được",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WalletMuted
                    )
                    Text(text = chatFault.messageVi, fontSize = 12.sp, color = WalletInk, lineHeight = 17.sp)
                    Text(
                        text = "Nội dung bạn nhập được giữ nguyên. Không có đề xuất nào được tạo và không có bản ghi nào bị thay đổi.",
                        fontSize = 10.5.sp, color = WalletMuted, lineHeight = 15.sp
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (needsSetup) {
                            TextButton(onClick = onOpenAiSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("Mở cấu hình ⚙", fontSize = 12.sp) }
                        }
                        if (chatFault.retryable) {
                            TextButton(onClick = onAnalyzeClick, modifier = Modifier.heightIn(min = 48.dp)) { Text("Thử lại", fontSize = 12.sp) }
                        }
                        TextButton(onClick = onManualEntry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ghi thủ công", fontSize = 12.sp) }
                        TextButton(onClick = onDismissError, modifier = Modifier.heightIn(min = 48.dp)) { Text("Đóng", fontSize = 12.sp) }
                    }
                }
            }
        }

        if (aiPreferences.keyUnavailable) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color(0xFFFFF8E8))
                    .semantics { role = Role.Button }
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = "Nhập lại khóa API trong cấu hình", onClick = onOpenAiSettings)
                    .padding(11.dp)
            ) {
                Text(
                    text = "Khóa API đã lưu không đọc được trên thiết bị này. Chạm để nhập lại trong ⚙. Ghi thủ công, Đồng hồ, Nhật ký và Ngân sách vẫn dùng bình thường.",
                    fontSize = 10.5.sp, color = WalletMicroText, lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onManualEntry, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("＋ Ghi giờ thủ công (không cần AI)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }

        // AI-01: thẻ trả lời câu hỏi số liệu (chỉ-đọc, tính cục bộ)
        if (chatAnswer != null) {
            Spacer(modifier = Modifier.height(12.dp))
            ChatAnswerCard(
                answer = chatAnswer,
                onDismiss = onDismissAnswer,
                onUseSuggestion = onApplyExample
            )
        }

        // COMPACT BATCH PROPOSAL CARD (Yêu cầu 3)
        if (proposals.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            CompactProposalBatchCard(
                proposals = proposals,
                expandedProposalId = expandedProposalId,
                onToggleExpand = onToggleExpandProposal,
                onUpdateProposal = onUpdateProposal,
                onDiscardProposal = onDiscardProposal,
                onClearAll = onClearAllProposals,
                onConfirmSingle = onConfirmSingleProposal,
                onConfirmAll = onConfirmAllProposals
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(WalletMicroBg)
                .padding(11.dp)
        ) {
            Text(
                text = "Không tự suy diễn giờ bắt đầu/kết thúc khi thiếu thông tin. Hoạt động chỉ có thời lượng sẽ lưu ở trạng thái \"Chưa gắn giờ\".",
                fontSize = 10.5.sp,
                color = WalletMicroText,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun CompactProposalBatchCard(
    proposals: List<EditableProposalDraft>,
    expandedProposalId: String?,
    onToggleExpand: (String) -> Unit,
    onUpdateProposal: (String, (EditableProposalDraft) -> EditableProposalDraft) -> Unit,
    onDiscardProposal: (String) -> Unit,
    onClearAll: () -> Unit,
    onConfirmSingle: (String) -> Unit,
    onConfirmAll: () -> Unit
) {
    // Tách rõ thời lượng có mốc giờ, chưa gắn giờ và dự kiến — không gộp thành 1 tổng gây hiểu nhầm
    val timedMinutes = proposals
        .filter { !it.isFuturePlanned && it.startLocal.isNotBlank() && it.endLocal.isNotBlank() }
        .sumOf { it.durationMinutes }
    val untimedMinutes = proposals
        .filter { !it.isFuturePlanned && (it.startLocal.isBlank() || it.endLocal.isBlank()) }
        .sumOf { it.durationMinutes }
    val plannedMinutes = proposals
        .filter { it.isFuturePlanned }
        .sumOf { it.durationMinutes }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFBCD9CA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            // Batch Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "✦ ${proposals.size} hoạt động đề xuất · chờ bạn xác nhận",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalletInk
                    )
                    Text(
                        text = "Chưa lưu vào sổ cho tới khi bạn bấm Xác nhận.",
                        fontSize = 10.5.sp,
                        color = WalletMuted
                    )
                }

                Text(
                    text = "Bỏ hết",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WalletMuted,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(6.dp))
                        .semantics { role = Role.Button }
                        .clickable(onClickLabel = "Bỏ hết đề xuất đang chờ", onClick = onClearAll)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (timedMinutes > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFFE8F5EE))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "Có giờ: ${LedgerAnalyticsCalculator.formatCompactDurationSeconds(timedMinutes * 60L)}",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletMint
                        )
                    }
                }
                if (untimedMinutes > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFFEDF1EE))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "Chưa gắn giờ: ${LedgerAnalyticsCalculator.formatCompactDurationSeconds(untimedMinutes * 60L)}",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5A6B62)
                        )
                    }
                }
                if (plannedMinutes > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFFFFF3DF))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "Dự kiến: ${LedgerAnalyticsCalculator.formatCompactDurationSeconds(plannedMinutes * 60L)}",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF9C5D16)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Bấm vào dòng hoặc biểu tượng ✎ nếu cần sửa chi tiết từng mục.",
                fontSize = 10.sp,
                color = WalletMuted
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = WalletStroke)

            // Compact List of Proposals
            proposals.forEachIndexed { index, item ->
                val isExpanded = expandedProposalId == item.id
                val cat = TimeCategory.fromKeyOrDefault(item.categoryKey)
                val catColor = Color(cat.colorHex)
                val hasTime = item.startLocal.isNotBlank() && item.endLocal.isNotBlank()
                val timeBadgeText = when {
                    item.isFuturePlanned && hasTime -> "${item.startLocal}–${item.endLocal} · Dự kiến"
                    item.isFuturePlanned -> "Dự kiến (${item.dateLocal})"
                    hasTime -> "${item.startLocal}–${item.endLocal}"
                    else -> "Chưa gắn giờ"
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .fillMaxWidth()
                            .semantics { role = Role.Button }
                            .clickable { onToggleExpand(item.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(catColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = cat.emoji, fontSize = 14.sp)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalletInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${cat.displayName} · $timeBadgeText",
                                fontSize = 10.sp,
                                color = if (hasTime) Color(0xFF287356) else WalletMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = LedgerAnalyticsCalculator.formatCompactDurationSeconds(item.durationMinutes * 60L),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WalletInk
                        )

                        Text(
                            text = if (isExpanded) "▲" else "✎",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletMint,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(6.dp))
                                .semantics { contentDescription = "Chỉnh sửa hoặc thu gọn đề xuất"; role = Role.Button }
                                .clickable { onToggleExpand(item.id) }
                                .padding(horizontal = 5.dp, vertical = 3.dp)
                        )

                        Text(
                            text = "×",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA8B0AA),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(6.dp))
                                .semantics { contentDescription = "Bỏ đề xuất này"; role = Role.Button }
                                .clickable { onDiscardProposal(item.id) }
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    // On-demand Detail Editor (chỉ xuất hiện khi người dùng yêu cầu)
                    if (isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(11.dp))
                                .background(Color(0xFFF6F9F6))
                                .border(1.dp, WalletStroke, RoundedCornerShape(11.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = item.title,
                                onValueChange = { t ->
                                    onUpdateProposal(item.id) { it.copy(title = t.take(110)) }
                                },
                                label = { Text("Tên hoạt động") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            CategorySelectorGrid(
                                selectedKey = item.categoryKey,
                                onSelectCategory = { c ->
                                    onUpdateProposal(item.id) { it.copy(categoryKey = c) }
                                }
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = item.durationMinutesText,
                                    onValueChange = { v ->
                                        onUpdateProposal(item.id) {
                                            it.copy(durationMinutesText = v.filter { ch -> ch.isDigit() })
                                        }
                                    },
                                    label = { Text("Thời lượng (phút)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = item.dateLocal,
                                    onValueChange = { v ->
                                        onUpdateProposal(item.id) { it.copy(dateLocal = v.trim()) }
                                    },
                                    label = { Text("Ngày (YYYY-MM-DD)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.2f)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = item.startLocal,
                                    onValueChange = { v ->
                                        onUpdateProposal(item.id) { draft ->
                                            val nextStart = v.trim()
                                            val range = if (nextStart.isNotEmpty() && draft.endLocal.isNotEmpty()) {
                                                LedgerValidator.parseTimeRangeToEpochMillis(draft.dateLocal, nextStart, draft.endLocal)
                                            } else null
                                            val syncedMins = range?.let { ((it.second - it.first) / 60_000L).coerceAtLeast(1L).toString() }
                                            draft.copy(
                                                startLocal = nextStart,
                                                durationMinutesText = syncedMins ?: draft.durationMinutesText
                                            )
                                        }
                                    },
                                    label = { Text("Bắt đầu (HH:mm)") },
                                    placeholder = { Text("Trống = chưa gắn giờ") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = item.endLocal,
                                    onValueChange = { v ->
                                        onUpdateProposal(item.id) { draft ->
                                            val nextEnd = v.trim()
                                            val range = if (draft.startLocal.isNotEmpty() && nextEnd.isNotEmpty()) {
                                                LedgerValidator.parseTimeRangeToEpochMillis(draft.dateLocal, draft.startLocal, nextEnd)
                                            } else null
                                            val syncedMins = range?.let { ((it.second - it.first) / 60_000L).coerceAtLeast(1L).toString() }
                                            draft.copy(
                                                endLocal = nextEnd,
                                                durationMinutesText = syncedMins ?: draft.durationMinutesText
                                            )
                                        }
                                    },
                                    label = { Text("Kết thúc (HH:mm)") },
                                    placeholder = { Text("Trống = chưa gắn giờ") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Đánh dấu là Dự kiến",
                                    fontSize = 11.sp,
                                    color = WalletInk
                                )
                                Switch(
                                    checked = item.isFuturePlanned,
                                    onCheckedChange = { checked ->
                                        onUpdateProposal(item.id) { it.copy(isFuturePlanned = checked) }
                                    }
                                )
                            }

                            Button(
                                onClick = { onConfirmSingle(item.id) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = WalletMint),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text("✓ Lưu riêng mục này", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (index < proposals.lastIndex) {
                        HorizontalDivider(color = Color(0xFFEFF2EE))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Batch Confirm All Button
            Button(
                onClick = onConfirmAll,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WalletMint),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text(
                    text = "✓ Xác nhận toàn bộ (${proposals.size} hoạt động)",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatAnswerCard(
    answer: ChatAnswer,
    onDismiss: () -> Unit,
    onUseSuggestion: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (answer.isClarification) Color(0xFFFFF8E8) else Color(0xFFF1F8F3)),
        border = BorderStroke(1.dp, if (answer.isClarification) Color(0xFFF0DFB4) else Color(0xFFD3E6D8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = answer.label ?: if (answer.isClarification) "Cần hỏi lại" else "Wally",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalletMuted
                )
                Text(
                    text = "Đóng ✕",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WalletMuted,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .semantics { role = Role.Button }
                        .clickable(onClickLabel = "Đóng câu trả lời", onClick = onDismiss)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = answer.headline,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = WalletInk,
                lineHeight = 18.sp
            )
            answer.lines.forEach { line ->
                Spacer(modifier = Modifier.height(3.dp))
                Text(text = line, fontSize = 12.sp, color = WalletInk, lineHeight = 17.sp)
            }
            answer.notes.forEach { note ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = note, fontSize = 10.5.sp, color = WalletMuted, lineHeight = 15.sp)
            }
            if (answer.suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    answer.suggestions.forEach { suggestion ->
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(100.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFDBE8DD), RoundedCornerShape(100.dp))
                                .semantics { role = Role.Button }
                                .clickable { onUseSuggestion(suggestion) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(text = suggestion, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF3E6B56))
                        }
                    }
                }
            }
        }
    }
}
