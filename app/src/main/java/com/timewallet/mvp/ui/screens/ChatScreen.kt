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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.timewallet.mvp.data.local.AiPreferences
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.AiFault
import com.timewallet.mvp.domain.usecase.CancelArming
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import com.timewallet.mvp.R

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
    onConfirmAllProposals: () -> Unit,
    lastUserMessage: String?,
    onLastUserMessageChange: (String?) -> Unit
) {
    // v1.0.11: ba gợi ý ngắn thay cho năm chip; câu đầy đủ vẫn được điền vào ô nhập để người dùng xem/sửa trước khi gửi.
    val examples = listOf(
        "Ghi hoạt động" to "Từ 8h đến 9h30 làm dự án, đọc sách 45 phút, chạy bộ 30 phút, ăn trưa 30 phút, nghỉ ngơi 20 phút",
        "Hôm nay dùng bao nhiêu?" to "Hôm nay tôi đã dùng bao nhiêu giờ?",
        "So với tuần trước" to "So sánh tuần này với tuần trước"
    )

    // Tin nhắn vừa gửi chỉ để hiển thị bong bóng (do MainActivity giữ, không lưu, không đổi nghiệp vụ); tự xóa khi không còn gì để hiển thị.
    val scroll = rememberScrollState()
    // Màn thấp (xoay ngang): ẩn đầu trang và gợi ý nhanh để vùng hội thoại còn đủ chỗ đọc và ô nhập luôn thấy được.
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    val hasReply = isAnalyzing || chatAnswer != null || chatFault != null || proposals.isNotEmpty()
    LaunchedEffect(hasReply) { if (!hasReply) onLastUserMessageChange(null) }
    // v1.0.13 (F4): nút Gửi biến thành nút Hủy tại chỗ nên cú chạm thứ hai của bấm đúp rơi vào Hủy. Hủy hiện ra nhưng khóa trong CancelArming.DEFAULT_ARM_MS
    // đầu (ViewModel/WallyChatRunner cũng bỏ qua Hủy trong cửa sổ đó); chạm sau đó hủy ngay như cũ. Ô này bắt đầu muộn hơn đồng hồ của runner ít nhất một khung hình,
    // nên khi nút sáng lên thì runner chắc chắn đã "lên đạn".
    var cancelArmed by remember { mutableStateOf(false) }
    LaunchedEffect(isAnalyzing) {
        cancelArmed = false
        if (isAnalyzing) {
            delay(CancelArming.DEFAULT_ARM_MS)
            cancelArmed = true
        }
    }
    val scrollTrigger = listOf(proposals.size, chatAnswer?.headline, chatFault, isAnalyzing, lastUserMessage)
    LaunchedEffect(scrollTrigger) {
        withFrameNanos { }
        if (hasReply) scroll.animateScrollTo(scroll.maxValue) else scroll.scrollTo(0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!compactHeight) {
        // Đầu trang: Wally + trạng thái AI (chạm để cấu hình). FlowRow: khi hẹp/chữ lớn, nhãn trạng thái tự xuống dòng thay vì bóp tên.
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_wally),
                    contentDescription = "Wally",
                    modifier = Modifier.size(42.dp)
                )
                Column {
                    Text(text = "Wally", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = WalletInk, maxLines = 1, softWrap = false)
                    Text(text = "Trợ lý thời gian", fontSize = 12.5.sp, color = WalletMuted, maxLines = 1, softWrap = false)
                }
            }
            val modeText = if (aiPreferences.optInEnabled && aiPreferences.apiKey.isNotBlank()) {
                "✦ AI đã bật · ⚙"
            } else {
                "✦ Chưa cấu hình AI · ⚙"
            }
            Box(
                modifier = Modifier
                    .widthIn(max = 190.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFEFF3EE))
                    .walletClickable(onClickLabel = "Mở cấu hình AI", onClick = onOpenAiSettings)
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Text(
                    text = modeText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF5D7064),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        }

        // Hội thoại (cuộn); ô nhập luôn ghim ở dưới.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasReply) {
                // Màn chào: Wally lớn ở giữa, lời hỏi ngắn; gợi ý nhanh nằm ngay phía dưới.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_wally),
                        contentDescription = null,
                        modifier = Modifier.size(76.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Hôm nay của bạn thế nào?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalletInk,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Kể tôi nghe, tôi sẽ giúp bạn ghi lại.",
                        fontSize = 14.sp,
                        color = WalletMuted,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                lastUserMessage?.let { UserBubble(it) }
            }

            if (isAnalyzing) {
                WallyBubble {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Wally đang trả lời…", fontSize = 13.5.sp, color = WalletMuted, modifier = Modifier.weight(1f))
                        TextButton(onClick = onCancelAnalysis, enabled = cancelArmed, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Hủy", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // WALLY: lỗi AI không được im lặng; luôn có Thử lại và đường ghi thủ công. Nội dung đã nhập được giữ.
            if (chatFault != null) {
                WallyBubble(tint = Color(0xFFFFF8E8)) {
                    val needsSetup = chatFault == AiFault.NOT_CONFIGURED || chatFault == AiFault.CONFIG || chatFault == AiFault.AUTH || chatFault == AiFault.KEY_INVALID
                    Text(
                        text = if (lastAnalysisSource == OutcomeSource.NOT_CONFIGURED) "Wally chưa được cấu hình AI" else "Wally chưa trả lời được",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WalletMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = chatFault.messageVi, fontSize = 13.5.sp, color = WalletInk, lineHeight = 19.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Nội dung bạn nhập được giữ nguyên; không có bản ghi nào bị thay đổi.",
                        fontSize = 12.sp, color = WalletMuted, lineHeight = 17.sp
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (needsSetup) {
                            TextButton(onClick = onOpenAiSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("Mở cấu hình ⚙", fontSize = 13.sp) }
                        }
                        if (chatFault.retryable) {
                            TextButton(
                                onClick = {
                                    if (chatInputText.isNotBlank() && !GuardHealth.isCancelCommand(chatInputText)) {
                                        onLastUserMessageChange(chatInputText.trim())
                                        if (chatAnswer != null) onDismissAnswer()
                                    }
                                    onAnalyzeClick()
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) { Text("Thử lại", fontSize = 13.sp) }
                        }
                        TextButton(onClick = onManualEntry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ghi thủ công", fontSize = 13.sp) }
                        TextButton(onClick = onDismissError, modifier = Modifier.heightIn(min = 48.dp)) { Text("Đóng", fontSize = 13.sp) }
                    }
                }
            }

            if (aiPreferences.keyUnavailable) {
                WallyBubble(tint = Color(0xFFFFF8E8)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .walletClickable(onClickLabel = "Nhập lại khóa API trong cấu hình", onClick = onOpenAiSettings)
                    ) {
                        Text(
                            text = "Khóa API đã lưu không đọc được trên thiết bị này. Chạm để nhập lại trong ⚙. Các tính năng còn lại vẫn dùng bình thường.",
                            fontSize = 12.5.sp, color = WalletMicroText, lineHeight = 18.sp
                        )
                    }
                }
            }

            // AI-01: câu trả lời số liệu (chỉ-đọc, tính cục bộ)
            if (chatAnswer != null) {
                WallyBubble(tint = if (chatAnswer.isClarification) Color(0xFFFFF8E8) else Color.White) {
                    ChatAnswerContent(
                        answer = chatAnswer,
                        onDismiss = onDismissAnswer,
                        onUseSuggestion = onApplyExample
                    )
                }
            }

            // Thẻ đề xuất: người dùng sửa/xác nhận/bỏ; không có gì được lưu trước khi bấm Xác nhận.
            if (proposals.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth()) {
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
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Gợi ý nhanh + ô nhập gọn ghim dưới đáy. Khi đang có đề xuất chờ xác nhận thì ẩn gợi ý để nhường chỗ cho thẻ đề xuất.
        if (proposals.isEmpty() && !compactHeight) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                examples.forEach { (label, fullSentence) ->
                    SuggestionChip(text = label) { onApplyExample(fullSentence) }
                }
                SuggestionChip(text = "＋ Ghi thủ công", onClick = onManualEntry)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = chatInputText,
                onValueChange = onChatInputChange,
                placeholder = { Text("Kể tiếp ngày của bạn…", fontSize = 14.sp) },
                minLines = 1,
                maxLines = 4,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.weight(1f)
            )
            if (isAnalyzing && !GuardHealth.isCancelCommand(chatInputText)) {
                Button(
                    onClick = onCancelAnalysis,
                    // v1.0.13 (F4): khóa trong cửa sổ bấm đúp; vẫn hiện ở đúng chỗ để người dùng thấy yêu cầu đang chạy.
                    enabled = cancelArmed,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5D7064),
                        disabledContainerColor = Color(0xFF5D7064).copy(alpha = 0.45f),
                        disabledContentColor = Color.White
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(54.dp)
                        .semantics { contentDescription = "Hủy yêu cầu đang gửi" }
                ) {
                    GlyphIcon(WalletGlyph.STOP, color = Color.White, size = 24.dp)
                }
            } else Button(
                onClick = {
                    // lệnh hủy không ghi đè tin nhắn gốc đang được phân tích
                    if (chatInputText.isNotBlank() && !GuardHealth.isCancelCommand(chatInputText)) {
                        onLastUserMessageChange(chatInputText.trim())
                        if (chatAnswer != null) onDismissAnswer()
                    }
                    onAnalyzeClick()
                },
                // AI-01 CLOSURE: cho phép gửi lệnh hủy khi đang phân tích (nút vẫn khóa với câu thường).
                enabled = !isAnalyzing || GuardHealth.isCancelCommand(chatInputText),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = if (chatInputText.isBlank()) WalletMint.copy(alpha = 0.45f) else WalletMint),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .size(54.dp)
                    .semantics { contentDescription = "Gửi cho Wally" }
            ) {
                GlyphIcon(WalletGlyph.SEND, color = Color.White, size = 24.dp)
            }
        }
    }
}

/** Bong bóng của Wally: avatar + nội dung (nền trắng hoặc nền cảnh báo). */
@Composable
private fun WallyBubble(tint: Color = Color.White, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_wally),
            contentDescription = null,
            modifier = Modifier.size(30.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp))
                .background(tint)
                .padding(14.dp)
        ) { content() }
    }
}

/** Bong bóng của người dùng (căn phải, màu thương hiệu). */
@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 20.dp))
                .background(WalletMint)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(text = text, fontSize = 14.sp, color = Color.White, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFDBE8DD), RoundedCornerShape(100.dp))
            .walletClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text = text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF3E6B56), maxLines = 1, softWrap = false)
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFBCD9CA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            // Batch Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "✦ ${proposals.size} hoạt động đề xuất · chờ bạn xác nhận",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WalletInk,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                )

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

            Text(
                text = "Chưa lưu vào sổ cho tới khi bạn bấm Xác nhận.",
                fontSize = 12.sp,
                color = WalletMuted
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            fontSize = 11.5.sp,
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
                            fontSize = 11.5.sp,
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
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF9C5D16)
                        )
                    }
                }
            }


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
                    // Hàng đề xuất 2 tầng (hợp với màn hẹp/chữ lớn): tầng 1 = biểu tượng + tên + thời lượng; tầng 2 = chi tiết + sửa/bỏ.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .semantics { role = Role.Button }
                            .heightIn(min = 48.dp)
                            .clickable { onToggleExpand(item.id) }
                            .padding(top = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(catColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = cat.emoji, fontSize = 16.sp)
                            }
                            Text(
                                text = item.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalletInk,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = 6.dp)
                            )
                            Text(
                                text = LedgerAnalyticsCalculator.formatCompactDurationSeconds(item.durationMinutes * 60L),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WalletInk,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.padding(start = 44.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${cat.displayName} · $timeBadgeText",
                                fontSize = 12.sp,
                                color = if (hasTime) Color(0xFF287356) else WalletMuted,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .semantics { contentDescription = "Chỉnh sửa hoặc thu gọn đề xuất"; role = Role.Button }
                                    .clickable { onToggleExpand(item.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isExpanded) {
                                    Text(text = "▲", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = WalletMint)
                                } else {
                                    GlyphIcon(WalletGlyph.EDIT, color = WalletMint, size = 20.dp)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .semantics { contentDescription = "Bỏ đề xuất này"; role = Role.Button }
                                    .clickable { onDiscardProposal(item.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                GlyphIcon(WalletGlyph.CLOSE, color = Color(0xFF8E9A93), size = 20.dp)
                            }
                        }
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
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WalletMint),
                contentPadding = PaddingValues(vertical = 14.dp)
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
private fun ChatAnswerContent(
    answer: ChatAnswer,
    onDismiss: () -> Unit,
    onUseSuggestion: (String) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = answer.label ?: if (answer.isClarification) "Cần hỏi lại" else "Wally",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WalletMuted,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Đóng ✕",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = WalletMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .walletClickable(onClickLabel = "Đóng câu trả lời", onClick = onDismiss)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = answer.headline,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = WalletInk,
            lineHeight = 22.sp
        )
        answer.lines.forEach { line ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = line, fontSize = 13.5.sp, color = WalletInk, lineHeight = 19.sp)
        }
        answer.notes.forEach { note ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = note, fontSize = 12.sp, color = WalletMuted, lineHeight = 17.sp)
        }
        if (answer.suggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                answer.suggestions.forEach { suggestion ->
                    SuggestionChip(text = suggestion) { onUseSuggestion(suggestion) }
                }
            }
        }
    }
}
