package com.timewallet.mvp.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.PausedTimerContinuation
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.CognitiveState
import com.timewallet.mvp.domain.usecase.DiceActionKind
import com.timewallet.mvp.domain.usecase.DiceRollResult
import com.timewallet.mvp.domain.usecase.FaceProbabilityItem
import com.timewallet.mvp.domain.model.SubtaskItem
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.usecase.FocusPinPolicy
import com.timewallet.mvp.domain.usecase.FocusPinState
import com.timewallet.mvp.domain.usecase.TimeRange
import com.timewallet.mvp.ui.theme.WalletBg
import com.timewallet.mvp.ui.theme.WalletCardBg
import com.timewallet.mvp.ui.theme.WalletDangerSolid
import com.timewallet.mvp.ui.theme.WalletInfoBg
import com.timewallet.mvp.ui.theme.WalletInfoText
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintAccent
import com.timewallet.mvp.ui.theme.WalletMintDark
import com.timewallet.mvp.ui.theme.WalletMintSoft
import com.timewallet.mvp.ui.theme.WalletMintTint
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletRing
import com.timewallet.mvp.ui.theme.WalletSoftNeutral
import com.timewallet.mvp.ui.theme.WalletStroke
import com.timewallet.mvp.ui.theme.WalletWarningBg
import com.timewallet.mvp.ui.theme.WalletWarningText
import kotlin.math.max

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    activeTimer: ActiveTimerSession?,
    elapsedSeconds: Long,
    clockSkewDetected: Boolean,
    inputTitle: String,
    inputCategoryKey: String,
    diceCognitiveState: CognitiveState,
    diceFaceProbabilities: List<FaceProbabilityItem>,
    isRollingDice: Boolean,
    rollingPreviewFaceNumber: Int,
    lastDiceRoll: DiceRollResult?,
    onTitleChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onRollDice: () -> Unit,
    onApplyDiceAndStartTimer: () -> Unit,
    onStartTimer: () -> Unit,
    onStopAndSaveTimer: () -> Unit,
    onCancelTimer: () -> Unit,
    pausedTimer: PausedTimerContinuation? = null,
    onPauseTimer: () -> Unit = {},
    onResumePausedTimer: () -> Unit = {},
    onDiscardPausedTimer: () -> Unit = {},
    // v1.1.0: Tag cá nhân cho phiên đo (gợi ý gần đây/thường dùng; bấm lại để bỏ chọn).
    tagSuggestions: List<com.timewallet.mvp.domain.model.Tag> = emptyList(),
    selectedTagId: String? = null,
    onSelectTag: (String?) -> Unit = {},
    onCleanDraft: () -> Unit = {},
    focusPinEnabled: Boolean = false,
    focusPinPreview: FocusPinState = FocusPinPolicy.resolve(emptyList(), null, System.currentTimeMillis()),
    onToggleFocusPin: (Boolean) -> Unit = {},
    onApplyFocusSuggestion: (String?, String?, String?) -> Unit = { _, _, _ -> },
    pomodoroMode: Boolean = false,
    pomodoroTargetMinutes: Int = 25,
    enablePomodoro: Boolean = true,
    enableDice: Boolean = true,
    onSetPomodoroMode: (Boolean, Int) -> Unit = { _, _ -> },
    activeTask: TaskItem? = null,
    onToggleSubtaskDone: (SubtaskItem) -> Unit = {},
    focusBreakNudgeVisible: Boolean = false,
    idleQuickStartTasks: List<TaskItem> = emptyList(),
    onQuickStartTask: (TaskItem) -> Unit = {}
) {
    val isRunning = activeTimer != null
    val hours = elapsedSeconds / 3600L
    val minutes = (elapsedSeconds % 3600L) / 60L
    val seconds = elapsedSeconds % 60L
    // v1.0.11: dưới 1 giờ chỉ hiện MM:SS để số lớn, đọc nhanh và không bị cắt; từ 1 giờ trở lên hiện H:MM:SS.
    val mm = minutes.toString().padStart(2, '0')
    val ss = seconds.toString().padStart(2, '0')
    val formattedClock = if (hours > 0L) "$hours:$mm:$ss" else "$mm:$ss"
    // Thu gọn vòng đồng hồ để màn Đồng hồ (kể cả 320x568dp) thấy ngay nút chính không cần cuộn.
    val cfg = LocalConfiguration.current
    val tinyViewport = cfg.screenHeightDp < 640 || cfg.screenWidthDp < 360
    val compactOrb = LocalDensity.current.fontScale > 1.15f || cfg.screenHeightDp < 720
    val orbSize = when {
        tinyViewport -> 106.dp
        compactOrb -> 150.dp
        else -> 170.dp
    }
    val clockDp = when {
        tinyViewport -> if (hours > 0L) 19.dp else 25.dp
        compactOrb -> if (hours > 0L) 30.dp else 42.dp
        else -> if (hours > 0L) 34.dp else 46.dp
    }
    val clockSize = with(LocalDensity.current) { clockDp.toSp() }

    val targetSeconds = activeTimer?.targetDurationSeconds
        ?: if (enablePomodoro && pomodoroMode) pomodoroTargetMinutes.coerceIn(5, 120) * 60L else null
    val sweepDegrees = if (targetSeconds != null && targetSeconds > 0L) {
        ((elapsedSeconds.toFloat() / targetSeconds.toFloat()).coerceIn(0f, 1f)) * 360f
    } else {
        ((elapsedSeconds % 3600L) / 3600f) * 360f
    }
    val reduceMotion = com.timewallet.mvp.ui.theme.WalletReduceMotion
    val animatedSweep by animateFloatAsState(
        targetValue = sweepDegrees,
        animationSpec = if (reduceMotion) tween(0) else tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "timerRingSweep"
    )
    val pulseAlpha = if (reduceMotion) {
        0.22f
    } else {
        val pulseTransition = rememberInfiniteTransition(label = "timerOrbPulse")
        val a by pulseTransition.animateFloat(
            initialValue = 0.14f,
            targetValue = 0.34f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "timerOrbGlowAlpha"
        )
        a
    }

    var showCleanTimerConfirm by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDiceSheet by remember { mutableStateOf(lastDiceRoll != null && !isRunning) }

    val activeCategoryKey = if (isRunning) activeTimer?.categoryKey ?: inputCategoryKey else inputCategoryKey
    val activeCategory = TimeCategory.fromKeyOrDefault(activeCategoryKey)
    val isDirtyDraft = !isRunning && (inputTitle.isNotBlank() || selectedTagId != null || inputCategoryKey != "work")

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (tinyViewport) 6.dp else 12.dp)
    ) {
        if (tinyViewport) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Tập trung",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WalletInk,
                    maxLines = 1
                )
                if (isRunning) {
                    Text(
                        text = "● Đang đo",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletMintAccent
                    )
                }
            }
        } else {
            ScreenTitle("Tập trung")
        }

        if (!isRunning && enablePomodoro) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    Triple(false, 25, "Đếm xuôi"),
                    Triple(true, 25, "Pomodoro 25p"),
                    Triple(true, 45, "Sâu 45p")
                ).forEach { (isPomo, mins, label) ->
                    val selected = if (!isPomo) !pomodoroMode else (pomodoroMode && pomodoroTargetMinutes == mins)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (selected) WalletMint else WalletSoftNeutral)
                            .walletClickable(onClickLabel = "Chế độ $label") { onSetPomodoroMode(isPomo, mins) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else WalletInk,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // Đồng hồ tròn trung tâm với hiệu ứng vòng đo chuyển động mượt + nhấn mạnh số liệu hiển thị lớn.
        val orbRingBg = WalletRing
        val orbAccentStart = WalletMint
        val orbAccentMid = Color(0xFF39B98A)
        val orbAccentEnd = Color(0xFF66E2B4)
        val ringStrokeDp = if (tinyViewport) 9.dp else 11.dp
        Box(
            modifier = Modifier.size(orbSize),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = ringStrokeDp.toPx()
                val inset = strokeWidth / 2f
                val arcSize = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                if (isRunning) {
                    drawCircle(
                        color = orbAccentMid.copy(alpha = pulseAlpha),
                        radius = size.minDimension / 2f,
                        style = Stroke(width = strokeWidth + 6.dp.toPx())
                    )
                }
                drawArc(
                    color = orbRingBg, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(width = strokeWidth)
                )
                if (animatedSweep > 0f || isRunning) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(orbAccentStart, orbAccentMid, orbAccentEnd, orbAccentStart)),
                        startAngle = -90f,
                        sweepAngle = animatedSweep.coerceAtLeast(if (isRunning) 6f else 0f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = if (tinyViewport) 6.dp else if (compactOrb) 10.dp else 18.dp)
            ) {
                Text(
                    text = if (targetSeconds != null) "MỤC TIÊU ${targetSeconds / 60} PHÚT" else "THỜI GIAN THỰC",
                    fontSize = if (tinyViewport) 8.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = if (tinyViewport) 0.35.sp else 1.0.sp,
                    color = WalletMuted,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(if (tinyViewport) 2.dp else 3.dp))
                Text(
                    text = formattedClock,
                    fontSize = clockSize,
                    letterSpacing = (-1).sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = WalletInk,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(3.dp))
                if (isRunning && targetSeconds != null && targetSeconds > 0L) {
                    val remaining = max(0L, targetSeconds - elapsedSeconds)
                    val remMin = (remaining / 60L).toString().padStart(2, '0')
                    val remSec = (remaining % 60L).toString().padStart(2, '0')
                    val pct = ((elapsedSeconds * 100L) / targetSeconds).coerceIn(0L, 100L)
                    Text(
                        text = if (remaining > 0L) "Còn $remMin:$remSec · $pct%" else "Đạt mục tiêu · 100%",
                        fontSize = if (tinyViewport) 11.sp else 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletMintAccent,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = if (isRunning) "● Đang ghi nhận" else "Sẵn sàng",
                        fontSize = if (tinyViewport) 11.sp else 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isRunning) WalletMintAccent else WalletMuted
                    )
                }
            }
        }

        // Hoạt động đang làm với ô nhập surface gọn, nhấn viền xanh chỉ khi focus.
        OutlinedTextField(
            value = if (isRunning) activeTimer?.title ?: inputTitle else inputTitle,
            onValueChange = onTitleChange,
            enabled = !isRunning,
            placeholder = {
                Text(
                    text = if (tinyViewport) "Đang làm gì? (VD: Đọc sách)" else "Bạn đang làm gì? (VD: Đọc tài liệu)",
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = WalletCardBg,
                unfocusedContainerColor = WalletCardBg,
                disabledContainerColor = WalletSoftNeutral,
                focusedBorderColor = WalletMint,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent
            ),
            trailingIcon = if (isDirtyDraft) {
                {
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .semantics { contentDescription = "Clean · Xóa nhập liệu" }
                            .walletClickable(onClickLabel = "Xóa nhập liệu đồng hồ") { showCleanTimerConfirm = true }
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(WalletGlyph.ERASER, color = Color(0xFFB3261E), size = 18.dp)
                    }
                }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Hàng 1 dòng gọn: chip "Nhóm: ... ▾" + gợi ý Tag cá nhân.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (showCategoryPicker) WalletMint else WalletSoftNeutral)
                    .semantics { contentDescription = "Nhóm ${activeCategory.displayName}, chạm để đổi" }
                    .walletClickable(enabled = !isRunning, onClickLabel = "Chọn nhóm hoạt động") {
                        showCategoryPicker = !showCategoryPicker
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                CategoryIcon(activeCategory, size = 14.dp, tint = if (showCategoryPicker) Color.White else WalletMintAccent)
                Text(
                    text = "Nhóm: ${activeCategory.displayName} ${if (showCategoryPicker) "▴" else "▾"}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (showCategoryPicker) Color.White else WalletInk,
                    maxLines = 1,
                    softWrap = false
                )
            }
            if (!isRunning) {
                tagSuggestions.forEach { t ->
                    ChoiceChip(t.name, selectedTagId == t.id, "Tag ${t.name}", leadingCategory = t.category) {
                        onSelectTag(t.id)
                    }
                }
            } else if (activeTimer?.tagId != null) {
                val name = tagSuggestions.firstOrNull { it.id == activeTimer.tagId }?.name
                if (name != null) Text("Tag: $name", fontSize = 12.5.sp, color = WalletMuted, maxLines = 1, softWrap = false)
            }
        }

        if (!isRunning && showCategoryPicker) {
            CategoryChipRow(
                selectedKey = activeCategoryKey,
                enabled = true,
                onSelectCategory = { key ->
                    onCategoryChange(key)
                    showCategoryPicker = false
                }
            )
        }

        if (clockSkewDetected) {
            NoticeBox(
                text = "Giờ hệ thống thay đổi trong khi đo. Đồng hồ đã tự bù lệch.",
                bg = WalletWarningBg,
                fg = WalletWarningText
            )
        }

        if (focusBreakNudgeVisible && isRunning) {
            NoticeBox(
                text = "Đã tập trung hơn 90 phút liên tục — cân nhắc tạm dừng lưu phiên và nghỉ ngắn 5–10 phút để giữ hiệu suất.",
                bg = WalletMintSoft,
                fg = WalletMintAccent
            )
        }

        if (activeTask != null) {
            SoftCard(padding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Đang gắn với việc: ${activeTask.title}",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalletMintAccent
                    )
                    if (activeTask.subtasks.isEmpty()) {
                        Text("Chưa có bước con.", fontSize = 11.sp, color = WalletMuted)
                    } else {
                        activeTask.subtasks.forEach { sub ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .walletClickable(onClickLabel = "Bước ${sub.title}") {
                                        onToggleSubtaskDone(sub)
                                    }
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(if (sub.isDone) "☑" else "☐", fontSize = 13.sp, color = if (sub.isDone) WalletMintAccent else WalletMuted)
                                Text(sub.title, fontSize = 12.sp, color = if (sub.isDone) WalletMuted else WalletInk)
                            }
                        }
                    }
                }
            }
        }
        if (isRunning && elapsedSeconds > 86_400L) {
            NoticeBox(
                text = "Phiên đã chạy hơn 24 giờ. Khi kết thúc, thời gian được lưu thành các đoạn tối đa 24 giờ, không bị mất. Nếu báo trùng giờ, hãy sửa hoặc xóa bản ghi trùng rồi lưu lại.",
                bg = WalletInfoBg,
                fg = WalletInfoText
            )
        }

        if (!isRunning) {
            if (pausedTimer != null) {
                val savedLabel = if (pausedTimer.accumulatedSeconds >= 60L) {
                    "Đã lưu ${TimeRange.formatDuration(((pausedTimer.accumulatedSeconds + 30L) / 60L).toInt().coerceAtLeast(1))}"
                } else if (pausedTimer.accumulatedSeconds > 0L) {
                    "Đã lưu ${pausedTimer.accumulatedSeconds}s"
                } else {
                    "Đã tạm dừng"
                }
                SoftCard(padding = if (tinyViewport) 8.dp else 12.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(if (tinyViewport) 4.dp else 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = if (tinyViewport) "⏸ ${pausedTimer.title} · $savedLabel" else "⏸ Tạm dừng: ${pausedTimer.title}",
                                    fontSize = if (tinyViewport) 11.5.sp else 12.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = WalletInk,
                                    maxLines = if (tinyViewport) 1 else 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                if (!tinyViewport) {
                                    Text(
                                        text = "$savedLabel · Tiếp tục để nối lại \"${pausedTimer.title}\"",
                                        fontSize = 11.sp,
                                        color = WalletMuted,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                            WalletPill(
                                onClickLabel = "Đóng thẻ tạm dừng",
                                containerColor = WalletCardBg,
                                borderColor = WalletStroke,
                                contentPadding = PaddingValues(horizontal = if (tinyViewport) 7.dp else 9.dp, vertical = if (tinyViewport) 4.dp else 7.dp),
                                onClick = onDiscardPausedTimer
                            ) {
                                Text("✕", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WalletMuted)
                            }
                        }
                        WalletPill(
                            onClickLabel = "Tiếp tục đo ${pausedTimer.title}",
                            containerColor = WalletMintDark,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = if (tinyViewport) 5.dp else 7.dp),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onResumePausedTimer
                        ) {
                            Text(
                                text = "▶ Tiếp tục \"${pausedTimer.title}\"",
                                fontSize = if (tinyViewport) 11.5.sp else 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onStartTimer,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = if (tinyViewport) 44.dp else 54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                    contentPadding = PaddingValues(vertical = if (tinyViewport) 9.dp else 14.dp)
                ) {
                    Text(text = "▶ Bắt đầu đo", fontSize = if (tinyViewport) 14.sp else 15.5.sp, fontWeight = FontWeight.ExtraBold)
                }
                if (enableDice) {
                    Box(
                        modifier = Modifier
                            .size(if (tinyViewport) 44.dp else 54.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (lastDiceRoll != null) WalletMintSoft else WalletMintTint)
                            .border(1.dp, if (lastDiceRoll != null) WalletMintAccent else WalletStroke, RoundedCornerShape(18.dp))
                            .semantics { contentDescription = "Mở Xúc xắc thời gian" }
                            .walletClickable(onClickLabel = "Mở Xúc xắc thời gian") { showDiceSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(WalletGlyph.DICE, color = WalletMintAccent, size = 24.dp)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DangerOutlineButton(
                    text = "Hủy",
                    modifier = Modifier
                        .weight(0.7f)
                        .heightIn(min = 54.dp),
                    onClick = onCancelTimer
                )
                OutlinedButton(
                    onClick = onPauseTimer,
                    modifier = Modifier
                        .weight(0.95f)
                        .heightIn(min = 54.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.2.dp, WalletMintAccent),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = "⏸ Tạm dừng",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletMintAccent,
                        maxLines = 1
                    )
                }
                Button(
                    onClick = onStopAndSaveTimer,
                    modifier = Modifier
                        .weight(1.2f)
                        .heightIn(min = 54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp)
                ) {
                    Text(text = "■ Lưu", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
        }

        // Ghim thông báo tập trung: phân cấp bề mặt sạch không viền dày; khi chưa đo gợi ý tối đa 2 việc ưu tiên.
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (focusPinEnabled) WalletMintSoft else WalletCardBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = if (focusPinEnabled) "Đang ghim tập trung" else "Ghim thông báo tập trung",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (focusPinEnabled) WalletMintAccent else WalletInk
                    )
                    Text(
                        text = focusPinPreview.headline,
                        fontSize = 11.sp,
                        color = WalletMuted,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (!isRunning && !focusPinPreview.suggestedTitle.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(WalletSoftNeutral)
                            .walletClickable(onClickLabel = "Điền hoạt động gợi ý vào đồng hồ") {
                                onApplyFocusSuggestion(
                                    focusPinPreview.suggestedTitle,
                                    focusPinPreview.suggestedCategory?.key,
                                    focusPinPreview.suggestedTagId
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Chọn",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletMintAccent,
                            softWrap = false
                        )
                    }
                }
                Switch(
                    checked = focusPinEnabled,
                    onCheckedChange = onToggleFocusPin,
                    colors = walletSwitchColors(WalletMint)
                )
            }
        }

        // Chỉ hiện việc ưu tiên khởi động nhanh / gợi ý trước khi đo; lúc đang tập trung giữ màn hình sạch, tránh phân tâm.
        if (!isRunning && idleQuickStartTasks.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = WalletCardBg,
                border = BorderStroke(1.dp, WalletStroke),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Gợi ý trước khi đo",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletMuted
                    )
                    idleQuickStartTasks.take(2).forEach { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(WalletBg)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WalletInk,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                val meta = buildList {
                                    add(task.priority.badgeText)
                                    task.estimatedMinutes?.let { add("~${it}p") }
                                    task.dueTimeLocal?.let { add("Hạn $it") }
                                }.joinToString(" · ")
                                Text(
                                    text = meta,
                                    fontSize = 10.5.sp,
                                    color = WalletMuted,
                                    maxLines = 1
                                )
                            }
                            WalletPill(
                                onClickLabel = "Bắt đầu tập trung ${task.title}",
                                containerColor = WalletMintDark,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                onClick = { onQuickStartTask(task) }
                            ) {
                                Text("▶ Đo", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
    }

    if (enableDice && showDiceSheet) {
        val diceSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDiceSheet = false },
            sheetState = diceSheetState,
            containerColor = WalletCardBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .padding(bottom = 18.dp)
            ) {
                DiceSection(
                    cognitiveState = diceCognitiveState,
                    faceProbabilities = diceFaceProbabilities,
                    isRolling = isRollingDice,
                    rollingPreviewFaceNumber = rollingPreviewFaceNumber,
                    lastRoll = lastDiceRoll,
                    isTimerRunning = isRunning,
                    initiallyExpanded = true,
                    onRollDice = onRollDice,
                    onApplyAndStart = {
                        showDiceSheet = false
                        onApplyDiceAndStartTimer()
                    }
                )
            }
        }
    }

    if (showCleanTimerConfirm) {
        AlertDialog(
            onDismissRequest = { showCleanTimerConfirm = false },
            containerColor = WalletCardBg,
            title = { Text("Xóa nhập liệu đồng hồ?", fontWeight = FontWeight.ExtraBold, color = WalletInk) },
            text = {
                Text(
                    "Tên hoạt động, danh mục và tag đang chọn cho đồng hồ sẽ được đặt lại từ đầu.",
                    fontSize = 12.5.sp,
                    color = WalletMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCleanTimerConfirm = false
                        onCleanDraft()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WalletDangerSolid, contentColor = Color.White)
                ) { Text("Xóa nhập liệu", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showCleanTimerConfirm = false }) {
                    Text("Giữ lại", color = WalletMuted)
                }
            }
        )
    }
}

@Composable
private fun NoticeBox(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(12.dp)
    ) {
        Text(text = text, fontSize = 12.sp, color = fg, lineHeight = 17.sp)
    }
}

/** Hàng danh mục cuộn ngang (gọn hơn lưới nhiều dòng); cùng ngữ nghĩa chọn một như CategorySelectorGrid. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CategoryChipRow(selectedKey: String, enabled: Boolean = true, onSelectCategory: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimeCategory.entries.forEach { category ->
            val selected = category.key == selectedKey
            val requester = remember { BringIntoViewRequester() }
            var skipInitialBringIntoView by remember { mutableStateOf(true) }
            LaunchedEffect(selected) {
                if (skipInitialBringIntoView) {
                    skipInitialBringIntoView = false
                } else if (selected) {
                    requester.bringIntoView()
                }
            }
            Row(
                modifier = Modifier
                    .bringIntoViewRequester(requester)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) WalletMint else WalletCardBg)
                    .border(1.dp, if (selected) WalletMint else WalletStroke, RoundedCornerShape(14.dp))
                    .semantics {
                        contentDescription = "Danh mục ${category.displayName}"
                        this.selected = selected
                    }
                    .walletClickable(role = Role.RadioButton, enabled = enabled) { onSelectCategory(category.key) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CategoryIcon(
                    category = category,
                    size = 15.dp,
                    tint = if (selected) Color.White else WalletMintAccent
                )
                Text(
                    text = category.displayName,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color.White else WalletInk,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiceSection(
    cognitiveState: CognitiveState,
    faceProbabilities: List<FaceProbabilityItem>,
    isRolling: Boolean,
    rollingPreviewFaceNumber: Int,
    lastRoll: DiceRollResult?,
    isTimerRunning: Boolean,
    initiallyExpanded: Boolean = true,
    onRollDice: () -> Unit,
    onApplyAndStart: () -> Unit
) {
    var open by remember { mutableStateOf(initiallyExpanded || lastRoll != null) }
    val buttonsRequester = remember { BringIntoViewRequester() }
    // Chỉ cuộn khi một lượt lắc VỪA kết thúc trong lần hiển thị này (không cuộn khi quay lại tab đã có kết quả cũ).
    var wasRolling by remember { mutableStateOf(false) }
    LaunchedEffect(isRolling) {
        if (isRolling) {
            wasRolling = true
        } else if (wasRolling) {
            wasRolling = false
            if (lastRoll != null) {
                withFrameNanos { }
                buttonsRequester.bringIntoView()
            }
        }
    }
    val diceSymbols = listOf("⚀", "⚁", "⚂", "⚃", "⚄", "⚅")
    val displayedSymbol = when {
        isRolling -> diceSymbols[(rollingPreviewFaceNumber - 1).coerceIn(0, 5)]
        lastRoll != null -> lastRoll.selectedFace.diceSymbol
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(WalletMintTint)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .walletClickable(onClickLabel = if (open) "Thu gọn xúc xắc" else "Mở xúc xắc") { open = !open }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(WalletCardBg),
                contentAlignment = Alignment.Center
            ) {
                if (displayedSymbol != null) {
                    Text(text = displayedSymbol, fontSize = 24.sp, color = WalletMint)
                } else {
                    GlyphIcon(WalletGlyph.DICE, color = WalletMint, size = 24.dp)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Chưa biết làm gì?", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = WalletInk)
                Text(text = "Để xúc xắc chọn phiên tiếp theo", fontSize = 12.5.sp, color = WalletMuted)
            }
            Text(text = if (open) "▴" else "▾", fontSize = 14.sp, color = WalletMuted)
        }

        if (open) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (lastRoll != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(WalletCardBg)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(text = lastRoll.selectedFace.headline, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = WalletInk)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            CategoryIcon(category = lastRoll.selectedFace.category, size = 15.dp, tint = WalletMint)
                            Text(
                                text = "${lastRoll.selectedFace.category.displayName} · ${lastRoll.selectedFace.durationMinutes} phút",
                                fontSize = 13.sp,
                                color = WalletMuted
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(buttonsRequester),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRollDice,
                        enabled = !isRolling,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, WalletMint),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = if (isRolling) "Đang lắc…" else "🎲 Lắc xúc xắc",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletMintAccent,
                            maxLines = 2,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (lastRoll != null && !isTimerRunning) {
                        Button(
                            onClick = onApplyAndStart,
                            modifier = Modifier
                                .weight(1.2f)
                                .heightIn(min = 52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text(
                                text = "▶ Chạy ngay (${lastRoll.selectedFace.durationMinutes}p)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                InfoToggle(label = "Cách xúc xắc hoạt động") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Gợi ý nhịp tập trung dựa trên sổ thời gian hôm nay (không phải chẩn đoán).",
                            fontSize = 12.sp,
                            color = WalletMuted,
                            lineHeight = 17.sp
                        )
                        Text(text = cognitiveState.titleVi, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = WalletInk)
                        Text(text = cognitiveState.descriptionVi, fontSize = 12.sp, color = WalletInk, lineHeight = 17.sp)
                        faceProbabilities.forEach { item ->
                            val shortDesc = when (item.face.faceNumber) {
                                1 -> "Làm 25p"
                                2 -> "Làm 2 tiếng"
                                3 -> "Nghỉ 30p"
                                4 -> "Làm 50p"
                                5 -> "Giải trí 45p"
                                else -> "Vận động 20p"
                            }
                            val isSelected = lastRoll?.selectedFace?.faceNumber == item.face.faceNumber && !isRolling
                            val isWork = item.face.actionKind == DiceActionKind.WORK_FOCUS
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) WalletCardBg else Color.Transparent)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "${item.face.diceSymbol} Số ${item.face.faceNumber} · $shortDesc", fontSize = 12.sp, color = WalletInk)
                                Text(
                                    text = "${item.probabilityPercent}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isWork) Color(0xFF4C9DF0) else Color(0xFFD99432)
                                )
                            }
                        }
                        if (lastRoll != null) {
                            Text(
                                text = "Cơ sở: ${lastRoll.selectedFace.scienceReason}",
                                fontSize = 12.sp,
                                color = WalletMuted,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
