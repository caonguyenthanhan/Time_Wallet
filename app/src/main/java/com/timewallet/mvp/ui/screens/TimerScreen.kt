package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.usecase.CognitiveState
import com.timewallet.mvp.domain.usecase.DiceActionKind
import com.timewallet.mvp.domain.usecase.DiceRollResult
import com.timewallet.mvp.domain.usecase.FaceProbabilityItem
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMicroBg
import com.timewallet.mvp.ui.theme.WalletMicroText
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintDark
import com.timewallet.mvp.ui.theme.WalletMintMid
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletStroke
import kotlin.math.max

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
    onCancelTimer: () -> Unit
) {
    val isRunning = activeTimer != null
    val hours = elapsedSeconds / 3600L
    val minutes = (elapsedSeconds % 3600L) / 60L
    val seconds = elapsedSeconds % 60L
    val formattedClock =
        "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"

    val targetSeconds = activeTimer?.targetDurationSeconds
    val sweepDegrees = if (targetSeconds != null && targetSeconds > 0L) {
        ((elapsedSeconds.toFloat() / targetSeconds.toFloat()).coerceIn(0f, 1f)) * 360f
    } else {
        ((elapsedSeconds % 3600L) / 3600f) * 360f
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        PageIntroHeader(
            eyebrow = "ĐỒNG HỒ & XÚC XẮC DOPAMINE",
            title = "Bắt đầu tập trung.",
            subtitle = "Đo thời gian thực hoặc để Xúc xắc Dopamine & Pomodoro quyết định phiên tiếp theo."
        )

        // ------------------- THẺ XÚC XẮC DOPAMINE & POMODORO -------------------
        DopamineDiceSectionCard(
            cognitiveState = diceCognitiveState,
            faceProbabilities = diceFaceProbabilities,
            isRolling = isRollingDice,
            rollingPreviewFaceNumber = rollingPreviewFaceNumber,
            lastRoll = lastDiceRoll,
            isTimerRunning = isRunning,
            onRollDice = onRollDice,
            onApplyAndStart = onApplyDiceAndStartTimer
        )

        Spacer(modifier = Modifier.height(14.dp))

        WalletSurfaceCard {
            Text(
                text = "Bạn đang làm gì?",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64766C)
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = if (isRunning) activeTimer?.title ?: inputTitle else inputTitle,
                onValueChange = onTitleChange,
                enabled = !isRunning,
                placeholder = { Text("Ví dụ: Đọc tài liệu AI") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Nhóm hoạt động",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64766C)
            )
            Spacer(modifier = Modifier.height(8.dp))
            CategorySelectorGrid(
                selectedKey = if (isRunning) activeTimer?.categoryKey ?: inputCategoryKey else inputCategoryKey,
                enabled = !isRunning,
                onSelectCategory = onCategoryChange
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Circular Timer Orb
        Box(
            modifier = Modifier
                .size(232.dp)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 11.dp.toPx()
                drawArc(
                    color = Color(0xFFD5E6DB),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth)
                )
                if (sweepDegrees > 0f || isRunning) {
                    drawArc(
                        color = Color(0xFF6ABF91),
                        startAngle = -90f,
                        sweepAngle = sweepDegrees.coerceAtLeast(if (isRunning) 4f else 0f),
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(206.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = if (targetSeconds != null) {
                            "MỤC TIÊU ${targetSeconds / 60} PHÚT"
                        } else {
                            "THỜI GIAN THỰC"
                        },
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = WalletMuted
                    )
                    Spacer(modifier = Modifier.height(7.dp))
                    Text(
                        text = formattedClock,
                        fontSize = 36.sp,
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
                        val targetText = if (remaining > 0L) {
                            "🎲 Còn lại $remMin:$remSec"
                        } else {
                            "🎉 Đã đạt mục tiêu Xúc xắc!"
                        }
                        Text(
                            text = targetText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F8066),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = if (isRunning) "● Đang ghi nhận (MEASURED)" else "Sẵn sàng",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isRunning) Color(0xFF4C9675) else WalletMuted
                        )
                    }
                }
            }
        }

        if (clockSkewDetected) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFF4E5))
                    .padding(12.dp)
            ) {
                Text(
                    text = "⚠ Phát hiện giờ hệ thống thay đổi trong khi đo. Đồng hồ đã tự động bù lệch bằng bộ đếm monotonic của thiết bị.",
                    fontSize = 11.sp,
                    color = Color(0xFF8A5300),
                    lineHeight = 16.sp
                )
            }
        }

        if (isRunning && elapsedSeconds > 86_400L) {
            // v1.0.6 (R01): phiên > 24h vẫn lưu được; báo rõ cách ứng dụng xử lý để người dùng không phải đoán hay bấm Hủy.
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE9F3FB))
                    .padding(12.dp)
            ) {
                Text(
                    text = "ℹ Phiên đã chạy hơn 24 giờ. Khi bấm Kết thúc & lưu, thời gian được lưu thành các đoạn liên tiếp (mỗi đoạn tối đa 24 giờ), không bị mất. Nếu báo trùng giờ, phiên vẫn được giữ: hãy sửa hoặc xóa bản ghi trùng rồi lưu lại.",
                    fontSize = 11.sp,
                    color = Color(0xFF245A82),
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (!isRunning) {
            Button(
                onClick = onStartTimer,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WalletMint),
                contentPadding = PaddingValues(vertical = 15.dp)
            ) {
                Text(
                    text = "▶ Bắt đầu đo",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DangerOutlineButton(
                    text = "Hủy phiên",
                    modifier = Modifier.weight(0.9f),
                    onClick = onCancelTimer
                )
                Button(
                    onClick = onStopAndSaveTimer,
                    modifier = Modifier.weight(1.4f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WalletMint),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text(
                        text = "■ Kết thúc & lưu",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Đồng hồ vẫn tính khi chuyển màn hình hoặc đóng ứng dụng. Không tự suy luận thời gian của các hoạt động khác.",
            fontSize = 11.sp,
            color = Color(0xFF7D8E83),
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DopamineDiceSectionCard(
    cognitiveState: CognitiveState,
    faceProbabilities: List<FaceProbabilityItem>,
    isRolling: Boolean,
    rollingPreviewFaceNumber: Int,
    lastRoll: DiceRollResult?,
    isTimerRunning: Boolean,
    onRollDice: () -> Unit,
    onApplyAndStart: () -> Unit
) {
    val diceSymbols = listOf("⚀", "⚁", "⚂", "⚃", "⚄", "⚅")
    val displayedSymbol = when {
        isRolling -> diceSymbols[(rollingPreviewFaceNumber - 1).coerceIn(0, 5)]
        lastRoll != null -> lastRoll.selectedFace.diceSymbol
        else -> "🎲"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFBCD9CA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row with Cognitive State Badge
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
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(WalletMintDark, WalletMintMid)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayedSymbol,
                            fontSize = 22.sp,
                            color = Color.White
                        )
                    }

                    Column {
                        Text(
                            text = "Xúc Xắc Quyết Định Thời Gian",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WalletInk
                        )
                        Text(
                            text = "Thuật toán Dopamine (RPE) & Pomodoro Ultradian",
                            fontSize = 10.5.sp,
                            color = WalletMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Cognitive State Pill Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color(cognitiveState.badgeColorHex).copy(alpha = 0.10f))
                    .border(
                        1.dp,
                        Color(cognitiveState.badgeColorHex).copy(alpha = 0.30f),
                        RoundedCornerShape(11.dp)
                    )
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        text = "🧠 ${cognitiveState.titleVi}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(cognitiveState.badgeColorHex)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = cognitiveState.descriptionVi,
                        fontSize = 10.5.sp,
                        color = WalletInk,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 6 Faces Live Adaptive Probability Grid (2 rows x 3 columns)
            Text(
                text = "Trọng số xác suất 6 mặt hiện tại (tự thích ứng theo sổ cái hôm nay):",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = WalletMuted
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                faceProbabilities.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowItems.forEach { item ->
                            val isSelected = lastRoll?.selectedFace?.faceNumber == item.face.faceNumber && !isRolling
                            val isWork = item.face.actionKind == DiceActionKind.WORK_FOCUS
                            val pillBg = when {
                                isSelected -> Color(0xFFDDF3E8)
                                isWork -> Color(0xFFF4F7F9)
                                else -> Color(0xFFFDF8EE)
                            }
                            val pillBorder = if (isSelected) WalletMint else WalletStroke

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(pillBg)
                                    .border(1.dp, pillBorder, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 7.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${item.face.diceSymbol} Số ${item.face.faceNumber}",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = WalletInk
                                    )
                                    Text(
                                        text = "${item.probabilityPercent}%",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isWork) Color(0xFF287BCB) else Color(0xFFBD7B22)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                val shortDesc = when (item.face.faceNumber) {
                                    1 -> "Làm 25p"
                                    2 -> "Làm 2 tiếng"
                                    3 -> "Nghỉ 30p"
                                    4 -> "Làm 50p"
                                    5 -> "Giải trí 45p"
                                    else -> "Vận động 20p"
                                }
                                Text(
                                    text = shortDesc,
                                    fontSize = 10.sp,
                                    color = WalletMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Rolled Outcome Card if user rolled
            if (lastRoll != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = WalletStroke)
                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WalletMicroBg)
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = lastRoll.selectedFace.diceSymbol,
                                fontSize = 26.sp,
                                color = WalletMintDark
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lastRoll.selectedFace.headline,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = WalletInk
                                )
                                Text(
                                    text = "Danh mục: ${lastRoll.selectedFace.category.emoji} ${lastRoll.selectedFace.category.displayName} · Xác suất trúng: ${lastRoll.probabilityPercent}%",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WalletMicroText
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "🔬 Cơ chế khoa học: ${lastRoll.selectedFace.scienceReason}",
                            fontSize = 10.5.sp,
                            color = WalletInk,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRollDice,
                    enabled = !isRolling,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, WalletMint),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        text = if (isRolling) "Đang lắc..." else "🎲 Lắc xúc xắc",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletMint
                    )
                }

                if (lastRoll != null && !isTimerRunning) {
                    Button(
                        onClick = onApplyAndStart,
                        modifier = Modifier.weight(1.35f),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WalletMint),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text(
                            text = "▶ Chạy ngay (${lastRoll.selectedFace.durationMinutes}p)",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
