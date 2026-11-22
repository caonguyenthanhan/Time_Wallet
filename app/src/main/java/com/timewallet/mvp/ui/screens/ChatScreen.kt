package com.timewallet.mvp.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.timewallet.mvp.domain.model.AiReadPermissions
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import com.timewallet.mvp.ui.theme.WalletDisabled
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
import com.timewallet.mvp.data.local.WallyModelPreset
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.AiFault
import com.timewallet.mvp.domain.usecase.CancelArming
import com.timewallet.mvp.data.ai.GuardHealth
import com.timewallet.mvp.domain.usecase.OutcomeSource
import com.timewallet.mvp.domain.usecase.ChatAnswer
import com.timewallet.mvp.domain.usecase.ChatHistoryEntry
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.ui.EditableProposalDraft
import com.timewallet.mvp.ui.theme.WalletBg
import com.timewallet.mvp.ui.theme.WalletCardBg
import com.timewallet.mvp.ui.theme.WalletClarificationBg
import com.timewallet.mvp.ui.theme.WalletDangerBg
import com.timewallet.mvp.ui.theme.WalletDangerSolid
import com.timewallet.mvp.ui.theme.WalletDangerText
import com.timewallet.mvp.ui.theme.WalletDivider
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMicroBg
import com.timewallet.mvp.ui.theme.WalletMicroText
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintAccent
import com.timewallet.mvp.ui.theme.WalletMintDark
import com.timewallet.mvp.ui.theme.WalletMintSoft
import com.timewallet.mvp.ui.theme.WalletMintTint
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletSoftNeutral
import com.timewallet.mvp.ui.theme.WalletStroke
import com.timewallet.mvp.ui.theme.WalletWarningBg
import com.timewallet.mvp.ui.theme.WalletWarningText
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import com.timewallet.mvp.ui.theme.WalletBg
import com.timewallet.mvp.ui.theme.WalletCardBg
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
/**
 * Khi người dùng bấm Gửi và Wally đang phân tích (`isAnalyzing`), ẩn phần chữ đã gửi khỏi ô nhập
 * để trải nghiệm giống khung chat thật; nếu hủy hoặc lỗi thì nội dung vẫn còn nguyên ở State để hiện lại.
 */
internal fun visibleChatComposerText(
    chatInputText: String,
    isAnalyzing: Boolean,
    suppressedInFlightRaw: String?
): String = if (isAnalyzing && suppressedInFlightRaw != null && chatInputText == suppressedInFlightRaw) "" else chatInputText

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
    onSelectWallyPreset: (WallyModelPreset) -> Unit = {},
    onToggleExpandProposal: (String) -> Unit,
    onUpdateProposal: (String, (EditableProposalDraft) -> EditableProposalDraft) -> Unit,
    onDiscardProposal: (String) -> Unit,
    onClearAllProposals: () -> Unit,
    onConfirmSingleProposal: (String) -> Unit,
    onConfirmAllProposals: () -> Unit,
    lastUserMessage: String?,
    onLastUserMessageChange: (String?) -> Unit,
    chatHistory: List<ChatHistoryEntry> = emptyList(),
    onClearChatHistory: () -> Unit = {},
    onCleanChatInput: () -> Unit = {},
    onCopyPromptToast: () -> Unit = {},
    onEditAndResubmitPrompt: (String) -> Unit = { edited ->
        onChatInputChange(edited)
        onAnalyzeClick()
    },
    onOpenAppIntro: () -> Unit = {},
    embeddedInOverlay: Boolean = false
) {
    // v1.0.11: ba gợi ý ngắn thay cho năm chip; câu đầy đủ vẫn được điền vào ô nhập để người dùng xem/sửa trước khi gửi.
    val examples = listOf(
        "Ghi hoạt động" to "Từ 8h đến 9h30 làm dự án, đọc sách 45 phút, chạy bộ 30 phút, ăn trưa 30 phút, nghỉ ngơi 20 phút",
        "Hôm nay dùng bao nhiêu?" to "Hôm nay tôi đã dùng bao nhiêu giờ?",
        "So với tuần trước" to "So sánh tuần này với tuần trước"
    )
    val context = LocalContext.current

    // Tin nhắn vừa gửi chỉ để hiển thị bong bóng (do MainActivity giữ); lịch sử đã hoàn tất được lưu bền vững qua chatHistory.
    val scroll = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current
    var suppressedInFlightRaw by rememberSaveable { mutableStateOf<String?>(null) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(isAnalyzing) {
        if (!isAnalyzing) suppressedInFlightRaw = null
    }
    val composerText = visibleChatComposerText(chatInputText, isAnalyzing, suppressedInFlightRaw)

    // Màn thấp (xoay ngang): ẩn đầu trang và gợi ý nhanh để vùng hội thoại còn đủ chỗ đọc và ô nhập luôn thấy được.
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    val maxBubbleWidth = ((LocalConfiguration.current.screenWidthDp.dp - 32.dp) * 0.75f).coerceIn(220.dp, 360.dp)
    val hasReply = chatHistory.isNotEmpty() || isAnalyzing || chatAnswer != null || chatFault != null || proposals.isNotEmpty()
    LaunchedEffect(hasReply) { if (!hasReply) onLastUserMessageChange(null) }
    LaunchedEffect(isAnalyzing, chatFault, proposals.size, chatAnswer?.headline) {
        val lastHistoryUser = chatHistory.lastOrNull { it.isUser }?.headline
        if (!isAnalyzing && chatFault == null && proposals.isEmpty() && chatAnswer == null &&
            chatHistory.isNotEmpty() && lastUserMessage != null && lastUserMessage != lastHistoryUser
        ) {
            onLastUserMessageChange(null)
        }
    }
    var lastUserTimeText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(lastUserMessage) {
        lastUserTimeText = if (lastUserMessage != null) {
            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        } else {
            ""
        }
    }
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
    val scrollTrigger = listOf(chatHistory.size, proposals.size, chatAnswer?.headline, chatFault, isAnalyzing, lastUserMessage)
    LaunchedEffect(scrollTrigger) {
        withFrameNanos { }
        if (hasReply) scroll.animateScrollTo(scroll.maxValue) else scroll.scrollTo(0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!compactHeight) {
        if (!embeddedInOverlay || chatHistory.isNotEmpty() || !hasReply) {
        // Đầu trang: khi nhúng trong WallyAiOverlay giữ thật gọn (trạng thái AI đã đặt inline trên thanh tiêu đề overlay).
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (embeddedInOverlay) 0.dp else 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!embeddedInOverlay) {
                Row(
                    modifier = Modifier.padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WallyMascotAvatar(
                        size = 40.dp,
                        contentDescription = "Wally"
                    )
                    Column {
                        Text(text = "Wally", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = WalletInk, maxLines = 1, softWrap = false)
                        Text(text = "Trợ lý thời gian", fontSize = 12.sp, color = WalletMuted, maxLines = 1, softWrap = false)
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (chatHistory.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(WalletDangerBg)
                            .walletClickable(onClickLabel = "Xóa lịch sử trò chuyện") {
                                showClearHistoryConfirm = true
                            }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Xóa lịch sử",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WalletDangerText,
                            maxLines = 1
                        )
                    }
                }
                if (!embeddedInOverlay) {
                    val modeText = if (aiPreferences.optInEnabled && aiPreferences.apiKey.isNotBlank()) {
                        "AI sẵn sàng"
                    } else {
                        "Chưa cấu hình AI"
                    }
                    Box(
                        modifier = Modifier
                            .widthIn(max = 160.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(WalletSoftNeutral)
                            .border(1.dp, WalletStroke, RoundedCornerShape(14.dp))
                            .walletClickable(onClickLabel = "Mở cấu hình AI", onClick = onOpenAiSettings)
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = modeText,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = WalletMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            GlyphIcon(WalletGlyph.SETTINGS, color = WalletMuted, size = 12.dp)
                        }
                    }
                }
                if (embeddedInOverlay && !hasReply) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(WalletMintTint)
                            .walletClickable(onClickLabel = "Xem giới thiệu Time Wallet") { onOpenAppIntro() }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Hướng dẫn",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WalletMintAccent,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        if (!embeddedInOverlay) {
            WallyModelModeSelector(
                currentModelName = aiPreferences.modelName,
                onSelectPreset = onSelectWallyPreset,
                compact = true,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        }
        }

        // Hội thoại (cuộn, role log / live region); ô nhập luôn ghim ở dưới.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasReply) {
                // Trạng thái trống tự nhiên: lời chào ngắn gọn + một hàng gợi ý nhẹ, nhường chỗ cho trò chuyện.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WallyMascotAvatar(
                        size = 64.dp,
                        contentDescription = null
                    )
                    Text(
                        text = "Hôm nay của bạn thế nào?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalletInk,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Nhắn tự nhiên để hỏi lịch, nhờ gợi ý hoặc ghi hoạt động — bạn luôn duyệt trước khi lưu.",
                        fontSize = 12.5.sp,
                        color = WalletMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        examples.forEach { (shortTitle, fullPrompt) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(WalletMintTint)
                                    .border(1.dp, WalletStroke, RoundedCornerShape(100.dp))
                                    .walletClickable(onClickLabel = "Dùng mẫu $shortTitle") {
                                        suppressedInFlightRaw = null
                                        onApplyExample(fullPrompt)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = shortTitle,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WalletMintAccent,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(WalletCardBg)
                                .border(1.dp, WalletStroke, RoundedCornerShape(100.dp))
                                .walletClickable(onClickLabel = "Ghi thủ công", onClick = onManualEntry)
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "+ Ghi thủ công",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = WalletInk,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            } else {
                val activeAnswerHeadline = chatAnswer?.takeUnless { it.isTransactionStatus }?.headline
                chatHistory.forEachIndexed { idx, entry ->
                    val duplicatedByLiveAnswer = idx == chatHistory.lastIndex &&
                        !entry.isUser &&
                        activeAnswerHeadline != null &&
                        entry.headline == activeAnswerHeadline
                    if (!duplicatedByLiveAnswer) {
                        if (entry.isUser) {
                            UserBubble(
                                bubbleKey = "hist_${entry.id}",
                                text = entry.headline,
                                maxWidth = maxBubbleWidth,
                                timeText = entry.timeText.ifBlank { null },
                                statusText = entry.label ?: "Wally đã xem ✓✓",
                                isRead = true,
                                canResubmit = !isAnalyzing,
                                onCopy = { textToCopy ->
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("wally-prompt", textToCopy))
                                    onCopyPromptToast()
                                },
                                onSubmitEdited = { edited ->
                                    onLastUserMessageChange(edited)
                                    if (chatAnswer != null) onDismissAnswer()
                                    suppressedInFlightRaw = edited
                                    keyboardController?.hide()
                                    onEditAndResubmitPrompt(edited)
                                }
                            )
                        } else {
                            WallyBubble(
                                tint = if (entry.isClarification) WalletClarificationBg else WalletCardBg,
                                maxWidth = maxBubbleWidth
                            ) {
                                Text(
                                    text = entry.headline,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalletInk,
                                    lineHeight = 20.sp
                                )
                                if (entry.lines.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        entry.lines.forEach { line ->
                                            Text(text = line, fontSize = 13.sp, color = WalletInk, lineHeight = 18.sp)
                                        }
                                    }
                                }
                                if (entry.notes.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        entry.notes.forEach { note ->
                                            Text(text = note, fontSize = 11.5.sp, color = WalletMuted, lineHeight = 16.sp)
                                        }
                                    }
                                }
                                if (entry.timeText.isNotBlank()) {
                                    Text(
                                        text = entry.timeText,
                                        fontSize = 10.5.sp,
                                        color = WalletMuted,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                val lastHistoryUser = chatHistory.lastOrNull { it.isUser }?.headline
                val showLiveUserBubble = lastUserMessage != null && (
                    isAnalyzing || chatFault != null || lastUserMessage != lastHistoryUser
                )
                if (showLiveUserBubble) {
                    lastUserMessage?.let { sentText ->
                        val statusLabel = when {
                            isAnalyzing -> "Đã gửi ✓"
                            chatAnswer != null || proposals.isNotEmpty() -> "Wally đã xem ✓✓"
                            chatFault != null -> "Đã gửi"
                            else -> "Đã gửi ✓"
                        }
                        UserBubble(
                            bubbleKey = "live_${sentText.hashCode()}",
                            text = sentText,
                            maxWidth = maxBubbleWidth,
                            timeText = lastUserTimeText.ifBlank { null },
                            statusText = statusLabel,
                            isRead = !isAnalyzing && (chatAnswer != null || proposals.isNotEmpty()),
                            canResubmit = !isAnalyzing,
                            onCopy = { textToCopy ->
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("wally-prompt", textToCopy))
                                onCopyPromptToast()
                            },
                            onSubmitEdited = { edited ->
                                onLastUserMessageChange(edited)
                                if (chatAnswer != null) onDismissAnswer()
                                suppressedInFlightRaw = edited
                                keyboardController?.hide()
                                onEditAndResubmitPrompt(edited)
                            }
                        )
                    }
                }
            }

            if (isAnalyzing) {
                WallyBubble(maxWidth = maxBubbleWidth) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WallyTypingDots()
                        Text(
                            text = "Wally đang soạn…",
                            fontSize = 12.5.sp,
                            color = WalletMuted,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        TextButton(onClick = onCancelAnalysis, enabled = cancelArmed, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Hủy", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // WALLY: lỗi AI không được im lặng; luôn có Thử lại và đường ghi thủ công. Nội dung đã nhập được giữ.
            if (chatFault != null) {
                WallyBubble(tint = WalletClarificationBg, maxWidth = maxBubbleWidth) {
                    val needsSetup = chatFault == AiFault.NOT_CONFIGURED ||
                        chatFault == AiFault.CONFIG ||
                        chatFault == AiFault.AUTH ||
                        chatFault == AiFault.KEY_INVALID ||
                        chatFault == AiFault.NOT_FOUND ||
                        chatFault == AiFault.SERVER
                    val isSetupWarn = chatFault == AiFault.NOT_CONFIGURED
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GlyphIcon(
                            glyph = if (isSetupWarn) WalletGlyph.WARNING else WalletGlyph.ERROR,
                            color = if (isSetupWarn) WalletWarningText else WalletDangerText,
                            size = 16.dp
                        )
                        Text(
                            text = if (lastAnalysisSource == OutcomeSource.NOT_CONFIGURED) "Wally chưa được cấu hình AI" else "Wally chưa trả lời được",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WalletMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = chatFault.messageVi, fontSize = 13.5.sp, color = WalletInk, lineHeight = 19.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Nội dung bạn nhập được giữ nguyên; không có bản ghi nào bị thay đổi.",
                        fontSize = 12.sp, color = WalletMuted, lineHeight = 17.sp
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (needsSetup) {
                            TextButton(
                                onClick = onOpenAiSettings,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .semantics { contentDescription = "Mở cấu hình ⚙" }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    GlyphIcon(WalletGlyph.SETTINGS, color = WalletMint, size = 14.dp)
                                    Text("Mở cấu hình", fontSize = 13.sp)
                                }
                            }
                        }
                        if (chatFault.retryable) {
                            TextButton(
                                onClick = {
                                    if (chatInputText.isNotBlank() && !GuardHealth.isCancelCommand(chatInputText)) {
                                        onLastUserMessageChange(chatInputText.trim())
                                        if (chatAnswer != null) onDismissAnswer()
                                        suppressedInFlightRaw = chatInputText
                                    }
                                    keyboardController?.hide()
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
                WallyBubble(tint = WalletClarificationBg, maxWidth = maxBubbleWidth) {
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
                WallyBubble(
                    tint = if (chatAnswer.isClarification) WalletClarificationBg else WalletCardBg,
                    maxWidth = maxBubbleWidth
                ) {
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

        // Gợi ý nhanh khi đang trò chuyện (khi trống đã hiển thị ngay dưới lời chào để tránh lặp nút).
        if (hasReply && proposals.isEmpty() && !compactHeight) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                examples.forEach { (label, fullSentence) ->
                    SuggestionChip(text = label) {
                        suppressedInFlightRaw = null
                        onApplyExample(fullSentence)
                    }
                }
                SuggestionChip(text = "＋ Ghi thủ công", onClick = onManualEntry)
            }
        }

        var showCleanChatConfirm by remember { mutableStateOf(false) }
        val canSubmitChat = composerText.isNotBlank() || (isAnalyzing && GuardHealth.isCancelCommand(chatInputText))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = WalletCardBg,
            border = BorderStroke(
                width = if (canSubmitChat || isAnalyzing) 1.3.dp else 1.dp,
                color = if (canSubmitChat || isAnalyzing) WalletMint.copy(alpha = 0.55f) else WalletStroke
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp, max = 112.dp)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (composerText.isEmpty()) {
                        Text(
                            text = "Kể tiếp ngày của bạn…",
                            fontSize = 14.sp,
                            color = WalletMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = composerText,
                        onValueChange = {
                            suppressedInFlightRaw = null
                            onChatInputChange(it)
                        },
                        textStyle = TextStyle(color = WalletInk, fontSize = 14.sp, lineHeight = 20.sp),
                        cursorBrush = SolidColor(WalletMint),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (composerText.isNotBlank() && !isAnalyzing) {
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .semantics { contentDescription = "Clean · Xóa nhập liệu"; role = Role.Button }
                            .clickable(onClickLabel = "Xóa nội dung đang nhập") { showCleanChatConfirm = true },
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(WalletGlyph.ERASER, color = WalletMuted, size = 18.dp)
                    }
                }
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
                            .size(44.dp)
                            .semantics { contentDescription = "Hủy yêu cầu đang gửi" }
                    ) {
                        GlyphIcon(WalletGlyph.STOP, color = Color.White, size = 20.dp)
                    }
                } else {
                    Button(
                        onClick = {
                            if (chatInputText.isNotBlank() && !GuardHealth.isCancelCommand(chatInputText)) {
                                onLastUserMessageChange(chatInputText.trim())
                                if (chatAnswer != null) onDismissAnswer()
                                suppressedInFlightRaw = chatInputText
                            }
                            keyboardController?.hide()
                            onAnalyzeClick()
                        },
                        // AI-01 CLOSURE: giữ đúng biểu thức enabled cho lệnh hủy khi đang phân tích; hiển thị trạng thái sẵn sàng gửi trực quan qua màu nền/viền/icon.
                        enabled = !isAnalyzing || GuardHealth.isCancelCommand(chatInputText),
                        shape = CircleShape,
                        border = if (canSubmitChat) null else BorderStroke(1.dp, WalletStroke),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canSubmitChat) WalletMint else WalletSoftNeutral,
                            contentColor = if (canSubmitChat) Color.White else WalletDisabled,
                            disabledContainerColor = WalletSoftNeutral,
                            disabledContentColor = WalletDisabled
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .size(44.dp)
                            .semantics { contentDescription = "Gửi cho Wally" }
                    ) {
                        GlyphIcon(
                            glyph = WalletGlyph.SEND,
                            color = if (canSubmitChat) Color.White else WalletDisabled,
                            size = 20.dp
                        )
                    }
                }
            }
        }
        if (showClearHistoryConfirm) {
            AlertDialog(
                onDismissRequest = { showClearHistoryConfirm = false },
                containerColor = WalletCardBg,
                title = { Text("Xóa lịch sử trò chuyện?", fontWeight = FontWeight.ExtraBold, color = WalletInk) },
                text = {
                    Text(
                        "Các bong bóng hội thoại trước đây với Wally sẽ được dọn sạch. Dữ liệu hoạt động đã lưu trong sổ cái không bị ảnh hưởng.",
                        fontSize = 12.5.sp,
                        color = WalletMuted
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showClearHistoryConfirm = false
                            onClearChatHistory()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WalletDangerSolid, contentColor = Color.White)
                    ) {
                        Text("Xóa lịch sử", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearHistoryConfirm = false }) {
                        Text("Giữ lại", color = WalletMuted)
                    }
                }
            )
        }
        if (showCleanChatConfirm) {
            AlertDialog(
                onDismissRequest = { showCleanChatConfirm = false },
                containerColor = WalletCardBg,
                title = { Text("Xóa nội dung đang nhập?", fontWeight = FontWeight.ExtraBold, color = WalletInk) },
                text = {
                    Text(
                        "Câu nháp đang gõ ở Wally sẽ bị xóa sạch để bắt đầu nhập mới.",
                        fontSize = 12.5.sp,
                        color = WalletMuted
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showCleanChatConfirm = false
                            onCleanChatInput()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WalletDangerSolid, contentColor = Color.White)
                    ) { Text("Xóa nhập liệu", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showCleanChatConfirm = false }) {
                        Text("Giữ lại", color = WalletMuted)
                    }
                }
            )
        }
    }
}

/** Bong bóng của Wally (chat-start): avatar + nội dung bo rộng với đuôi góc dưới-trái, rộng tối đa ~75% cột. */
@Composable
private fun WallyBubble(
    tint: Color = WalletCardBg,
    maxWidth: androidx.compose.ui.unit.Dp = 300.dp,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        WallyMascotAvatar(
            size = 32.dp,
            contentDescription = null
        )
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 5.dp))
                .background(tint)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) { content() }
    }
}

/** Chỉ báo đang gõ: 3 chấm nảy nối tiếp nhau trong bong bóng của Wally. */
@Composable
private fun WallyTypingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wallyTypingDots")
    Row(
        modifier = modifier
            .padding(vertical = 4.dp)
            .semantics { contentDescription = "Wally đang soạn câu trả lời" },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        (0..2).forEach { idx ->
            val bounce by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 520,
                        delayMillis = idx * 140,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotBounce_$idx"
            )
            Box(
                modifier = Modifier
                    .offset(y = (-5f * bounce).dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(WalletMint.copy(alpha = 0.45f + 0.55f * bounce))
            )
        }
    }
}

/** Bong bóng của người dùng (chat-end): căn phải, kèm Copy & Sửa prompt (có Revert changes). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserBubble(
    bubbleKey: String,
    text: String,
    maxWidth: androidx.compose.ui.unit.Dp = 300.dp,
    timeText: String? = null,
    statusText: String? = null,
    isRead: Boolean = false,
    canResubmit: Boolean = true,
    onCopy: (String) -> Unit = {},
    onSubmitEdited: (String) -> Unit = {}
) {
    var editing by remember(bubbleKey, text) { mutableStateOf(false) }
    var draftText by remember(bubbleKey, text) { mutableStateOf(text) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        if (!editing) {
            Box(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 5.dp, bottomStart = 20.dp))
                    .background(WalletMint)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(text = text, fontSize = 14.sp, color = Color.White, lineHeight = 20.sp)
            }
            Row(
                modifier = Modifier.padding(top = 2.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val meta = listOfNotNull(timeText?.takeIf { it.isNotBlank() }, statusText?.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        fontSize = 11.sp,
                        fontWeight = if (isRead) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isRead) WalletMintAccent else WalletMuted
                    )
                }
                Text(
                    text = "Copy",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WalletMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .semantics { contentDescription = "Sao chép prompt" }
                        .walletClickable(onClickLabel = "Sao chép prompt") { onCopy(text) }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
                if (canResubmit) {
                    Text(
                        text = "Sửa",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WalletMintAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .semantics { contentDescription = "Chỉnh sửa prompt" }
                            .walletClickable(onClickLabel = "Chỉnh sửa prompt") {
                                draftText = text
                                editing = true
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.widthIn(max = maxWidth),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WalletCardBg),
                border = BorderStroke(1.2.dp, WalletMint)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = draftText,
                        onValueChange = { draftText = it },
                        label = { Text("Sửa prompt", fontSize = 11.5.sp) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (draftText != text) {
                            OutlinedButton(
                                onClick = { draftText = text },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Revert changes", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = WalletMuted)
                            }
                        }
                        TextButton(
                            onClick = {
                                draftText = text
                                editing = false
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Hủy", fontSize = 11.5.sp, color = WalletMuted)
                        }
                        Button(
                            onClick = {
                                val next = draftText.trim()
                                if (next.isNotEmpty()) {
                                    editing = false
                                    onSubmitEdited(next)
                                }
                            },
                            enabled = draftText.isNotBlank() && canResubmit,
                            colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Phân tích lại", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(WalletMintTint)
            .border(1.dp, WalletStroke, RoundedCornerShape(100.dp))
            .walletClickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(
            text = "→ $text",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = WalletMintAccent,
            maxLines = 1,
            softWrap = false
        )
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
        colors = CardDefaults.cardColors(containerColor = WalletCardBg),
        border = BorderStroke(1.dp, WalletStroke),
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
                            .background(WalletMintSoft)
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
                            .background(WalletSoftNeutral)
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "Chưa gắn giờ: ${LedgerAnalyticsCalculator.formatCompactDurationSeconds(untimedMinutes * 60L)}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletMuted
                        )
                    }
                }
                if (plannedMinutes > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(WalletWarningBg)
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "Dự kiến: ${LedgerAnalyticsCalculator.formatCompactDurationSeconds(plannedMinutes * 60L)}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletWarningText
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
                val hasTime = item.startLocal.isNotBlank() && item.endLocal.isNotBlank()
                val durationStr = LedgerAnalyticsCalculator.formatCompactDurationSeconds(item.durationMinutes * 60L)
                // Thông tin tĩnh ("thông tin chết") đặt liền sau thời lượng, tách bạch khỏi cụm 3 nút icon hành động.
                val deadStatusLabel = when {
                    item.isFuturePlanned -> "Đã nhận dự kiến"
                    hasTime -> "Đã nhận có giờ"
                    else -> "Chưa gắn giờ"
                }
                val timeWindowText = when {
                    hasTime -> "${item.startLocal}–${item.endLocal}"
                    item.isFuturePlanned -> item.dateLocal
                    else -> null
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryBadge(category = cat, size = 34.dp)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = item.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalletInk,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = WalletInk)) {
                                        append(durationStr)
                                    }
                                    withStyle(
                                        SpanStyle(
                                            fontWeight = FontWeight.Medium,
                                            color = if (item.isFuturePlanned) WalletWarningText else WalletMuted
                                        )
                                    ) {
                                        append(" · $deadStatusLabel")
                                    }
                                },
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                // v1.1.0: 🏷 = đề xuất đã khớp một Tag của bạn (khớp xác định theo tên, không do AI); nhóm theo Tag.
                                text = buildString {
                                    append(cat.displayName)
                                    if (item.tagId != null) append(" 🏷")
                                    if (timeWindowText != null) append(" · $timeWindowText")
                                    if (item.isMarkedWaste) append(" · Lãng phí")
                                },
                                fontSize = 11.5.sp,
                                color = if (hasTime) WalletMintAccent else WalletMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // Cụm 3 nút icon hành động: Xác nhận (✓) | Sửa (✎) | Từ chối (✕)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(WalletMintSoft)
                                    .semantics { contentDescription = "Xác nhận đề xuất này"; role = Role.Button }
                                    .clickable { onConfirmSingle(item.id) }
                                    .padding(7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                GlyphIcon(WalletGlyph.CONFIRM, color = WalletMintAccent, size = 18.dp)
                            }
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .semantics { contentDescription = "Chỉnh sửa hoặc thu gọn đề xuất"; role = Role.Button }
                                    .clickable { onToggleExpand(item.id) }
                                    .padding(7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isExpanded) {
                                    Text(text = "▲", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = WalletMint)
                                } else {
                                    GlyphIcon(WalletGlyph.EDIT, color = WalletMint, size = 18.dp)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .semantics { contentDescription = "Bỏ đề xuất này"; role = Role.Button }
                                    .clickable { onDiscardProposal(item.id) }
                                    .padding(7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                GlyphIcon(WalletGlyph.CLOSE, color = Color(0xFF8E9A93), size = 18.dp)
                            }
                        }
                    }

                    // On-demand Detail Editor (chỉ xuất hiện khi người dùng yêu cầu)
                    if (isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(11.dp))
                                .background(WalletSoftNeutral)
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
                                        onUpdateProposal(item.id) {
                                            it.copy(
                                                isFuturePlanned = checked,
                                                isMarkedWaste = if (checked) false else it.isMarkedWaste
                                            )
                                        }
                                    }
                                )
                            }

                            if (!item.isFuturePlanned) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Đánh dấu lãng phí (biểu đồ 24h)",
                                        fontSize = 11.sp,
                                        color = WalletInk
                                    )
                                    Switch(
                                        checked = item.isMarkedWaste,
                                        onCheckedChange = { checked ->
                                            onUpdateProposal(item.id) { it.copy(isMarkedWaste = checked) }
                                        }
                                    )
                                }
                            }

                            Button(
                                onClick = { onConfirmSingle(item.id) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text("✓ Lưu riêng mục này", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (index < proposals.lastIndex) {
                        HorizontalDivider(color = WalletDivider)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Batch Confirm All Button
            Button(
                onClick = onConfirmAll,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlyphIcon(WalletGlyph.CONFIRM, color = Color.White, size = 16.dp)
                    Text(
                        text = "Xác nhận toàn bộ (${proposals.size} hoạt động)",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (answer.isClarification) WalletWarningBg else WalletMintTint)
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "✦",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (answer.isClarification) WalletWarningText else WalletMintAccent
                    )
                    Text(
                        text = answer.label ?: if (answer.isClarification) "Cần hỏi lại" else "Wally",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (answer.isClarification) WalletWarningText else WalletMintAccent
                    )
                }
            }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .size(width = 3.5.dp, height = 20.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (answer.isClarification) Color(0xFFD97706) else WalletMint)
            )
            Text(
                text = answer.headline,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = WalletInk,
                lineHeight = 21.sp,
                modifier = Modifier.weight(1f)
            )
        }
        if (answer.lines.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                answer.lines.forEach { line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(WalletMint)
                        )
                        Text(
                            text = line,
                            fontSize = 13.5.sp,
                            color = WalletInk,
                            lineHeight = 19.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        answer.notes.forEach { note ->
            Text(text = note, fontSize = 12.sp, color = WalletMuted, lineHeight = 17.sp)
        }
        if (answer.suggestions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "◆ BƯỚC TIẾP THEO",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WalletMintAccent
                )
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
}

@Composable
fun WallyAiOverlay(
    aiReadPermissions: AiReadPermissions,
    overlayHint: String?,
    inboxTaskCount: Int,
    onRequestReplanInbox: () -> Unit,
    onDismiss: () -> Unit,
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
    onSelectWallyPreset: (WallyModelPreset) -> Unit = {},
    onToggleExpandProposal: (String) -> Unit,
    onUpdateProposal: (String, (EditableProposalDraft) -> EditableProposalDraft) -> Unit,
    onDiscardProposal: (String) -> Unit,
    onClearAllProposals: () -> Unit,
    onConfirmSingleProposal: (String) -> Unit,
    onConfirmAllProposals: () -> Unit,
    lastUserMessage: String?,
    onLastUserMessageChange: (String?) -> Unit,
    chatHistory: List<ChatHistoryEntry> = emptyList(),
    onClearChatHistory: () -> Unit = {},
    onCleanChatInput: () -> Unit = {},
    onCopyPromptToast: () -> Unit = {},
    onEditAndResubmitPrompt: (String) -> Unit,
    onOpenAppIntro: () -> Unit = {}
) {
    val activeScopes = remember(aiReadPermissions) {
        buildList {
            if (aiReadPermissions.readLedger) add("Sổ cái")
            if (aiReadPermissions.readTasks) add("Việc")
            if (aiReadPermissions.readHabits) add("Thói quen")
            if (aiReadPermissions.readGoals) add("Mục tiêu")
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = WalletBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "✦ Trợ lý Wally",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalletInk,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        val aiReady = aiPreferences.optInEnabled && aiPreferences.apiKey.isNotBlank()
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(WalletSoftNeutral)
                                .border(1.dp, WalletStroke, RoundedCornerShape(100.dp))
                                .walletClickable(onClickLabel = "Mở cấu hình AI", onClick = onOpenAiSettings)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (aiReady) "AI bật" else "Chưa bật AI",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (aiReady) WalletMintAccent else WalletMuted,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                GlyphIcon(WalletGlyph.SETTINGS, color = WalletMuted, size = 11.dp)
                            }
                        }
                        if (inboxTaskCount > 0) {
                            WalletPill(
                                onClickLabel = "Gợi ý xếp lịch Inbox",
                                containerColor = WalletMintTint,
                                borderColor = WalletStroke,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                onClick = onRequestReplanInbox
                            ) {
                                Text(
                                    text = "Inbox ($inboxTaskCount)",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalletMintAccent,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(WalletCardBg)
                                .border(1.dp, WalletStroke, CircleShape)
                                .semantics { contentDescription = "Đóng trợ lý Wally" }
                                .walletClickable(onClickLabel = "Đóng trợ lý Wally") { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphIcon(WalletGlyph.CLOSE, color = WalletInk, size = 15.dp)
                        }
                    }
                }
                Text(
                    text = if (activeScopes.isEmpty()) {
                        "Quyền đọc AI: Đang tắt toàn bộ"
                    } else {
                        "Quyền đọc: ${activeScopes.joinToString(" · ")}"
                    },
                    fontSize = 11.sp,
                    color = if (activeScopes.isEmpty()) WalletDangerText else WalletMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                WallyModelModeSelector(
                    currentModelName = aiPreferences.modelName,
                    onSelectPreset = onSelectWallyPreset,
                    compact = true
                )
                if (!overlayHint.isNullOrBlank()) {
                    Text(
                        text = overlayHint,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WalletMintAccent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(WalletMintTint)
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    ChatScreen(
                        chatInputText = chatInputText,
                        isAnalyzing = isAnalyzing,
                        chatFault = chatFault,
                        lastAnalysisSource = lastAnalysisSource,
                        onCancelAnalysis = onCancelAnalysis,
                        onDismissError = onDismissError,
                        onManualEntry = {
                            onDismiss()
                            onManualEntry()
                        },
                        chatAnswer = chatAnswer,
                        onDismissAnswer = onDismissAnswer,
                        proposals = proposals,
                        expandedProposalId = expandedProposalId,
                        aiPreferences = aiPreferences,
                        onChatInputChange = onChatInputChange,
                        onApplyExample = onApplyExample,
                        onAnalyzeClick = onAnalyzeClick,
                        onOpenAiSettings = onOpenAiSettings,
                        onSelectWallyPreset = onSelectWallyPreset,
                        onToggleExpandProposal = onToggleExpandProposal,
                        onUpdateProposal = onUpdateProposal,
                        onDiscardProposal = onDiscardProposal,
                        onClearAllProposals = onClearAllProposals,
                        onConfirmSingleProposal = onConfirmSingleProposal,
                        onConfirmAllProposals = onConfirmAllProposals,
                        lastUserMessage = lastUserMessage,
                        onLastUserMessageChange = onLastUserMessageChange,
                        chatHistory = chatHistory,
                        onClearChatHistory = onClearChatHistory,
                        onCleanChatInput = onCleanChatInput,
                        onCopyPromptToast = onCopyPromptToast,
                        onEditAndResubmitPrompt = onEditAndResubmitPrompt,
                        onOpenAppIntro = onOpenAppIntro,
                        embeddedInOverlay = true
                    )
                }
            }
        }
    }
}

/**
 * Bộ chọn chế độ Wally Pro / Wally Flash dùng chung cho Chat, Overlay, Hồ sơ & Cấu hình AI.
 * Luôn hiển thị tên model Claude thực tế bên dưới để tránh nhầm lẫn Flash với Gemini Flash.
 */
@Composable
internal fun WallyModelModeSelector(
    currentModelName: String,
    onSelectPreset: (WallyModelPreset) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showAutoFutureHint: Boolean = false
) {
    val activePreset = WallyModelPreset.fromModelName(currentModelName)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WallyModelPreset.entries.forEach { preset ->
                val isSelected = activePreset == preset
                val radius = if (compact) 12.dp else 10.dp
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = if (compact) 44.dp else 48.dp)
                        .clip(RoundedCornerShape(radius))
                        .background(if (isSelected) WalletMintSoft else WalletSoftNeutral)
                        .border(
                            width = if (isSelected) 1.3.dp else 1.dp,
                            color = if (isSelected) WalletMint else WalletStroke,
                            shape = RoundedCornerShape(radius)
                        )
                        .semantics {
                            selected = isSelected
                            contentDescription = "${preset.displayTitle} (${preset.actualModelName})"
                        }
                        .walletClickable(
                            onClickLabel = "Chọn ${preset.displayTitle} (${preset.actualModelName})",
                            role = Role.RadioButton
                        ) { onSelectPreset(preset) }
                        .padding(
                            horizontal = if (compact) 10.dp else 10.dp,
                            vertical = if (compact) 6.dp else 8.dp
                        )
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = preset.displayTitle,
                                fontSize = if (compact) 12.sp else 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                color = if (isSelected) WalletMintDark else WalletInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!compact && preset == WallyModelPreset.PRO) {
                                Text(
                                    text = "Mặc định",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WalletMintAccent,
                                    maxLines = 1
                                )
                            }
                        }
                        Text(
                            text = preset.actualModelName,
                            fontSize = if (compact) 10.sp else 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) WalletMintAccent else WalletMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!compact) {
                            Text(
                                text = preset.modelId,
                                fontSize = 9.sp,
                                color = WalletMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = preset.descriptionVi,
                                fontSize = 10.sp,
                                color = WalletMuted,
                                lineHeight = 13.5.sp
                            )
                        }
                    }
                }
            }
        }
        val cleanCustom = currentModelName.trim()
        if (activePreset == null && cleanCustom.isNotEmpty()) {
            Text(
                text = "Đang dùng model tùy chỉnh: $cleanCustom",
                fontSize = 10.sp,
                color = WalletMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showAutoFutureHint) {
            Text(
                text = "Auto (Tự chọn Pro/Flash): Tạm khóa, chỉ bật khi bộ định tuyến chứng minh được hiệu quả.",
                fontSize = 10.sp,
                color = WalletMuted,
                lineHeight = 14.sp
            )
        }
    }
}
