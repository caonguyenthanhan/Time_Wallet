package com.timewallet.mvp.ui.screens

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.CognitiveState
import com.timewallet.mvp.domain.usecase.DiceActionKind
import com.timewallet.mvp.domain.usecase.DiceRollResult
import com.timewallet.mvp.domain.usecase.FaceProbabilityItem
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintTint
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletRing
import com.timewallet.mvp.ui.theme.WalletStroke
import kotlin.math.max

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    // v1.1.0: Tag cá nhân cho phiên đo (gợi ý gần đây/thường dùng; bấm lại để bỏ chọn).
    tagSuggestions: List<com.timewallet.mvp.domain.model.Tag> = emptyList(),
    selectedTagId: String? = null,
    onSelectTag: (String?) -> Unit = {},
    onCleanDraft: () -> Unit = {}
) {
    val isRunning = activeTimer != null
    val hours = elapsedSeconds / 3600L
    val minutes = (elapsedSeconds % 3600L) / 60L
    val seconds = elapsedSeconds % 60L
    // v1.0.11: dưới 1 giờ chỉ hiện MM:SS để số lớn, đọc nhanh và không bị cắt; từ 1 giờ trở lên hiện H:MM:SS.
    val mm = minutes.toString().padStart(2, '0')
    val ss = seconds.toString().padStart(2, '0')
    val formattedClock = if (hours > 0L) "$hours:$mm:$ss" else "$mm:$ss"
    // Cỡ chữ đồng hồ cố định theo dp (không phình theo cỡ chữ hệ thống) để luôn nằm gọn trong vòng tròn.
    val compactOrb = LocalDensity.current.fontScale > 1.15f || LocalConfiguration.current.screenHeightDp < 720
    val orbSize = if (compactOrb) 176.dp else 224.dp
    val clockDp = if (compactOrb) (if (hours > 0L) 30.dp else 44.dp) else (if (hours > 0L) 36.dp else 52.dp)
    val clockSize = with(LocalDensity.current) { clockDp.toSp() }

    val targetSeconds = activeTimer?.targetDurationSeconds
    val sweepDegrees = if (targetSeconds != null && targetSeconds > 0L) {
        ((elapsedSeconds.toFloat() / targetSeconds.toFloat()).coerceIn(0f, 1f)) * 360f
    } else {
        ((elapsedSeconds % 3600L) / 3600f) * 360f
    }

    var showCleanTimerConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenTitle("Đồng hồ")

        // Đồng hồ tròn: thành phần chính, nằm ngay vùng nhìn đầu tiên.
        Box(
            modifier = Modifier.size(orbSize),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 12.dp.toPx()
                val inset = strokeWidth / 2f
                val arcSize = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(
                    color = Color(0xFFDDEBE2), startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(width = strokeWidth)
                )
                if (sweepDegrees > 0f || isRunning) {
                    drawArc(
                        color = Color(0xFF3BAA82), startAngle = -90f,
                        sweepAngle = sweepDegrees.coerceAtLeast(if (isRunning) 4f else 0f), useCenter = false,
                        topLeft = topLeft, size = arcSize, style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = if (compactOrb) 12.dp else 24.dp)
            ) {
                Text(
                    text = if (targetSeconds != null) "MỤC TIÊU ${targetSeconds / 60} PHÚT" else "THỜI GIAN THỰC",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = WalletMuted,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formattedClock,
                    fontSize = clockSize,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = WalletInk,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (isRunning && targetSeconds != null) {
                    val remaining = max(0L, targetSeconds - elapsedSeconds)
                    val remMin = (remaining / 60L).toString().padStart(2, '0')
                    val remSec = (remaining % 60L).toString().padStart(2, '0')
                    Text(
                        text = if (remaining > 0L) "Còn lại $remMin:$remSec" else "Đã đạt mục tiêu!",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F8066),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = if (isRunning) "● Đang ghi nhận" else "Sẵn sàng",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isRunning) Color(0xFF2E8F69) else WalletMuted
                    )
                }
            }
        }

        // Hoạt động đang làm + danh mục (một dòng cuộn ngang).
        OutlinedTextField(
            value = if (isRunning) activeTimer?.title ?: inputTitle else inputTitle,
            onValueChange = onTitleChange,
            enabled = !isRunning,
            placeholder = { Text("Bạn đang làm gì? (VD: Đọc tài liệu)") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        CategoryChipRow(
            selectedKey = if (isRunning) activeTimer?.categoryKey ?: inputCategoryKey else inputCategoryKey,
            enabled = !isRunning,
            onSelectCategory = onCategoryChange
        )
        if (!isRunning && tagSuggestions.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                tagSuggestions.forEach { t ->
                    ChoiceChip(t.name, selectedTagId == t.id, "Tag ${t.name}", leadingCategory = t.category) { onSelectTag(t.id) }
                }
            }
        } else if (isRunning && activeTimer?.tagId != null) {
            val name = tagSuggestions.firstOrNull { it.id == activeTimer.tagId }?.name
            if (name != null) Text("Tag: $name", fontSize = 12.5.sp, color = WalletMuted)
        }

        if (clockSkewDetected) {
            NoticeBox(
                text = "Giờ hệ thống thay đổi trong khi đo. Đồng hồ đã tự bù lệch.",
                bg = Color(0xFFFFF4E5),
                fg = Color(0xFF8A5300)
            )
        }
        if (isRunning && elapsedSeconds > 86_400L) {
            // v1.0.6 (R01): phiên > 24h vẫn lưu được; báo ngắn gọn cách ứng dụng xử lý.
            NoticeBox(
                text = "Phiên đã chạy hơn 24 giờ. Khi kết thúc, thời gian được lưu thành các đoạn tối đa 24 giờ, không bị mất. Nếu báo trùng giờ, hãy sửa hoặc xóa bản ghi trùng rồi lưu lại.",
                bg = Color(0xFFE9F3FB),
                fg = Color(0xFF245A82)
            )
        }

        if (!isRunning) {
            Button(
                onClick = onStartTimer,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WalletMint),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Text(text = "▶ Bắt đầu đo", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
            if (inputTitle.isNotBlank() || selectedTagId != null || inputCategoryKey != "work") {
                TextButton(
                    onClick = { showCleanTimerConfirm = true },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Clean · Xóa nhập liệu đồng hồ" }
                ) {
                    Text("Clean · Xóa nhập liệu", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB3261E))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DangerOutlineButton(
                    text = "Hủy phiên",
                    modifier = Modifier
                        .weight(0.9f)
                        .heightIn(min = 56.dp),
                    onClick = onCancelTimer
                )
                Button(
                    onClick = onStopAndSaveTimer,
                    modifier = Modifier
                        .weight(1.4f)
                        .heightIn(min = 56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WalletMint),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text(text = "■ Kết thúc & lưu", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        // Xúc xắc: tính năng phụ, mở khi cần.
        DiceSection(
            cognitiveState = diceCognitiveState,
            faceProbabilities = diceFaceProbabilities,
            isRolling = isRollingDice,
            rollingPreviewFaceNumber = rollingPreviewFaceNumber,
            lastRoll = lastDiceRoll,
            isTimerRunning = isRunning,
            onRollDice = onRollDice,
            onApplyAndStart = onApplyDiceAndStartTimer
        )
    }

    if (showCleanTimerConfirm) {
        AlertDialog(
            onDismissRequest = { showCleanTimerConfirm = false },
            containerColor = Color.White,
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
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
private fun CategoryChipRow(selectedKey: String, enabled: Boolean, onSelectCategory: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimeCategory.entries.forEach { category ->
            val selected = category.key == selectedKey
            val requester = remember { BringIntoViewRequester() }
            LaunchedEffect(selected) { if (selected) requester.bringIntoView() }
            Row(
                modifier = Modifier
                    .bringIntoViewRequester(requester)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) WalletMint else Color.White)
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
                    tint = if (selected) Color.White else WalletMint
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
    onRollDice: () -> Unit,
    onApplyAndStart: () -> Unit
) {
    var open by remember { mutableStateOf(lastRoll != null) }
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
                    .background(Color.White),
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
                            .background(Color.White)
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
                            color = WalletMint,
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
                            colors = ButtonDefaults.buttonColors(containerColor = WalletMint),
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
                                    .background(if (isSelected) Color.White else Color.Transparent)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "${item.face.diceSymbol} Số ${item.face.faceNumber} · $shortDesc", fontSize = 12.sp, color = WalletInk)
                                Text(
                                    text = "${item.probabilityPercent}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isWork) Color(0xFF287BCB) else Color(0xFFBD7B22)
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
