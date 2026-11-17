package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timewallet.mvp.domain.model.ActivityReviewNote
import com.timewallet.mvp.domain.model.DailyReviewSnapshot
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.DailyReviewAssistant
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintDark
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletStroke
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DailyReviewScreen(
    todayDateLocal: String,
    snapshot: DailyReviewSnapshot,
    onBackToDashboard: () -> Unit,
    onSelectPreviousDay: () -> Unit,
    onSelectNextDay: () -> Unit,
    onSelectToday: () -> Unit,
    onOpenBudgetEditor: (TimeCategory?) -> Unit,
    onSaveActivityNote: (recordId: String, dateLocal: String, outcomeNote: String, feynmanExplanation: String) -> Unit,
    onSaveFullDailyReview: (
        dateLocal: String,
        summaryText: String,
        question1: String,
        question2: String,
        answer1: String,
        answer2: String,
        generalNote: String,
        pendingActivityNotes: List<ActivityReviewNote>
    ) -> Unit,
    onExportJsonClick: () -> Unit
) {
    val dateLocal = snapshot.dateLocal
    val dayStats = snapshot.dayStats
    val saved = snapshot.savedReview

    val questions = remember(dateLocal, snapshot.generatedQuestions, saved) {
        snapshot.effectiveQuestions.take(2)
    }
    val q1 = questions.getOrNull(0) ?: "Hoạt động nào hôm nay mang lại giá trị rõ nhất cho bạn?"
    val q2 = questions.getOrNull(1) ?: ""

    var summaryDraft by remember(dateLocal, saved?.updatedAt, snapshot.generatedSummary) {
        mutableStateOf(snapshot.effectiveSummary)
    }
    var answer1Draft by remember(dateLocal, saved?.updatedAt) {
        mutableStateOf(saved?.answer1 ?: "")
    }
    var answer2Draft by remember(dateLocal, saved?.updatedAt) {
        mutableStateOf(saved?.answer2 ?: "")
    }
    var generalNoteDraft by remember(dateLocal, saved?.updatedAt) {
        mutableStateOf(saved?.generalNote ?: "")
    }

    val outcomeDrafts = remember(dateLocal, snapshot.activityNotesMap) {
        mutableStateMapOf<String, String>().apply {
            snapshot.completedActivities.forEach { rec ->
                put(rec.id, snapshot.activityNotesMap[rec.id]?.outcomeNote ?: "")
            }
        }
    }
    val feynmanDrafts = remember(dateLocal, snapshot.activityNotesMap) {
        mutableStateMapOf<String, String>().apply {
            snapshot.completedActivities.forEach { rec ->
                put(rec.id, snapshot.activityNotesMap[rec.id]?.feynmanExplanation ?: "")
            }
        }
    }

    val friendlyDateTitle = remember(dateLocal, todayDateLocal) {
        try {
            val d = LocalDate.parse(dateLocal)
            val t = LocalDate.parse(todayDateLocal)
            val fmt = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale("vi", "VN"))
            val prefix = when (d) {
                t -> "Hôm nay · "
                t.minusDays(1) -> "Hôm qua · "
                else -> ""
            }
            "$prefix${d.format(fmt)}"
        } catch (_: Exception) {
            dateLocal
        }
    }

    val timedFormatted = LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(dayStats.timedSeconds)
    val untimedFormatted = LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(dayStats.untimedSeconds)
    val untrackedFormatted = LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(dayStats.untrackedPastSeconds)
    val plannedFormatted = LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(dayStats.plannedSeconds)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Back to Dashboard & Export Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .border(1.dp, WalletStroke, RoundedCornerShape(10.dp))
                    .semantics { role = Role.Button }
                    .clickable(onClick = onBackToDashboard)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "← Quay lại Tổng quan",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalletInk
                )
            }

            if (saved != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE5F5EC))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "✓ Đã lưu cục bộ",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletMintDark
                    )
                }
            }
        }

        PageIntroHeader(
            eyebrow = "DAILY REVIEW · NHÌN LẠI TRONG NGÀY",
            title = "Phản tư từ sổ cái thật.",
            subtitle = "Không chấm điểm năng suất, không tự bịa kết quả và không cộng giờ Dự kiến (PLANNED) vào thực tế."
        )

        // Date Switcher Card
        WalletSurfaceCard(paddingValues = PaddingValues(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFFF2F6F3))
                        .semantics { role = Role.Button }
                        .clickable(onClickLabel = "Xem ngày trước", onClick = onSelectPreviousDay)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "◀ Trước",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletInk
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.Button }
                        .heightIn(min = 48.dp)
                        .clickable(onClick = onSelectToday)
                        .padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = friendlyDateTitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalletInk,
                        textAlign = TextAlign.Center
                    )
                    if (dateLocal != todayDateLocal) {
                        Text(
                            text = "Bấm để về Hôm nay ($todayDateLocal)",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WalletMint
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFFF2F6F3))
                        .semantics { role = Role.Button }
                        .clickable(onClickLabel = "Xem ngày sau", onClick = onSelectNextDay)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Sau ▶",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletInk
                    )
                }
            }
        }

        // Section 1: 4 Real Ledger Metrics (Requirement 3)
        SectionHeader(
            title = "Số liệu Sổ cái ngày $dateLocal",
            subtitleOrRightText = "PLANNED tách biệt hoàn toàn"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReviewMetricBox(
                label = "Có mốc giờ (Thực tế)",
                valueText = timedFormatted,
                subLabel = "Đã khử trùng lặp",
                accentColor = WalletMint,
                modifier = Modifier.weight(1f)
            )
            ReviewMetricBox(
                label = "Chưa gắn giờ (Tách riêng)",
                valueText = untimedFormatted,
                subLabel = "Không cộng vào trục giờ",
                accentColor = Color(0xFFB46914),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReviewMetricBox(
                label = "Chưa ghi nhận (Quá khứ)",
                valueText = untrackedFormatted,
                subLabel = "Khoảng trống đã qua",
                accentColor = Color(0xFF687A70),
                modifier = Modifier.weight(1f)
            )
            ReviewMetricBox(
                label = "Dự kiến (PLANNED)",
                valueText = plannedFormatted,
                subLabel = "Không tính vào thực tế",
                accentColor = Color(0xFF7C4DDB),
                modifier = Modifier.weight(1f)
            )
        }

        // Section 2: Budget Progress (Requirement 3)
        SectionHeader(
            title = "Tiến độ Ngân sách thời gian",
            actionText = "Chỉnh sửa ✎",
            onActionClick = { onOpenBudgetEditor(null) }
        )

        WalletSurfaceCard {
            if (snapshot.budgetProgressList.isEmpty()) {
                Text(
                    text = "Chưa có mục tiêu ngân sách nào được đặt. Bạn có thể bấm \"Chỉnh sửa ✎\" để tự đặt mục tiêu theo danh mục.",
                    fontSize = 11.sp,
                    color = WalletMuted,
                    lineHeight = 15.sp
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    snapshot.budgetProgressList.forEach { progress ->
                        TimeBudgetItemCard(
                            progress = progress,
                            compactMode = true,
                            onEditClick = { cat -> onOpenBudgetEditor(cat) }
                        )
                    }
                }
            }
        }

        // Section 3: Completed Activities + Optional Outcome Note + Feynman Question for LEARN (Requirements 4 & 6)
        SectionHeader(
            title = "Hoạt động đã hoàn thành (${snapshot.completedActivities.size})",
            subtitleOrRightText = "Ghi chú kết quả tùy chọn"
        )

        WalletSurfaceCard {
            if (snapshot.completedActivities.isEmpty()) {
                Text(
                    text = "Chưa có hoạt động thực tế đã hoàn thành trong ngày $dateLocal.",
                    fontSize = 11.5.sp,
                    color = WalletMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            } else {
                val hmFmt = DateTimeFormatter.ofPattern("HH:mm")
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    snapshot.completedActivities.forEachIndexed { index, rec ->
                        val isLearn = rec.category == TimeCategory.LEARN
                        val zone = try { ZoneId.of(rec.timezone) } catch (_: Exception) { ZoneId.systemDefault() }
                        val slotLabel = if (rec.startedAt != null && rec.endedAt != null) {
                            val s = Instant.ofEpochMilli(rec.startedAt).atZone(zone).format(hmFmt)
                            val e = Instant.ofEpochMilli(rec.endedAt).atZone(zone).format(hmFmt)
                            "$s–$e · ${rec.layer.badgeShortVi}"
                        } else {
                            "Chưa gắn giờ · ${rec.layer.badgeShortVi}"
                        }
                        val durLabel = LedgerAnalyticsCalculator.formatCompactDurationSeconds(rec.durationSeconds)
                        val currentOutcome = outcomeDrafts[rec.id] ?: ""
                        val currentFeynman = feynmanDrafts[rec.id] ?: ""

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF8FAF8))
                                .border(1.dp, Color(0xFFE3ECE5), RoundedCornerShape(12.dp))
                                .padding(11.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CategoryBadge(category = rec.category, size = 30.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rec.title,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WalletInk
                                    )
                                    Text(
                                        text = "${rec.category.displayName} • $slotLabel",
                                        fontSize = 10.sp,
                                        color = WalletMuted
                                    )
                                }
                                Text(
                                    text = durLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = WalletInk
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = currentOutcome,
                                onValueChange = { outcomeDrafts[rec.id] = it },
                                label = { Text("Kết quả / Ghi chú phản tư (tùy chọn)") },
                                placeholder = { Text("Không bắt buộc — chỉ ghi khi bạn muốn lưu kết quả thực tế") },
                                singleLine = false,
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // YÊU CẦU 6: Với hoạt động Học tập, bổ sung câu hỏi Feynman
                            if (isLearn) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFEEF5FF))
                                        .border(1.dp, Color(0xFFCFE0FA), RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "💡 Câu hỏi Feynman (Học tập):",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF1E56A0)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "“${DailyReviewAssistant.FEYNMAN_QUESTION_VI}”",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = WalletInk,
                                            lineHeight = 15.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        OutlinedTextField(
                                            value = currentFeynman,
                                            onValueChange = { feynmanDrafts[rec.id] = it },
                                            label = { Text("Diễn giải ngắn gọn theo cách của bạn (tùy chọn)") },
                                            singleLine = false,
                                            maxLines = 3,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Box(
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE6F4EC))
                                        .semantics { role = Role.Button }
                                        .clickable(onClickLabel = "Lưu ghi chú kết quả") {
                                            onSaveActivityNote(
                                                rec.id,
                                                dateLocal,
                                                outcomeDrafts[rec.id].orEmpty().trim(),
                                                feynmanDrafts[rec.id].orEmpty().trim()
                                            )
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "✓ Lưu ghi chú mục này",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WalletMintDark
                                    )
                                }
                            }
                        }

                        if (index < snapshot.completedActivities.lastIndex) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
            }
        }

        // Section 4: AI Summary & Max 2 Reflection Questions from Real Ledger (Requirement 5 & 7)
        SectionHeader(
            title = "AI Tổng kết & Câu hỏi phản tư (Tối đa 2)",
            actionText = "↻ Làm mới từ Ledger",
            onActionClick = {
                summaryDraft = snapshot.generatedSummary
            }
        )

        WalletSurfaceCard {
            Text(
                text = "Bản tổng kết ngắn (chỉ từ dữ liệu Ledger thật):",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = WalletMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = summaryDraft,
                onValueChange = { summaryDraft = it },
                singleLine = false,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFEAF0E9))
            Spacer(modifier = Modifier.height(10.dp))

            // Câu hỏi phản tư 1
            Text(
                text = "Câu hỏi phản tư 1: $q1",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WalletInk,
                lineHeight = 15.5.sp
            )
            Spacer(modifier = Modifier.height(5.dp))
            OutlinedTextField(
                value = answer1Draft,
                onValueChange = { answer1Draft = it },
                label = { Text("Suy ngẫm của bạn (tùy chọn)") },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            if (q2.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Câu hỏi phản tư 2: $q2",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalletInk,
                    lineHeight = 15.5.sp
                )
                Spacer(modifier = Modifier.height(5.dp))
                OutlinedTextField(
                    value = answer2Draft,
                    onValueChange = { answer2Draft = it },
                    label = { Text("Suy ngẫm của bạn (tùy chọn)") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = generalNoteDraft,
                onValueChange = { generalNoteDraft = it },
                label = { Text("Ghi chú chung cuối ngày (tùy chọn)") },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val notesToSave = snapshot.completedActivities.map { rec ->
                        ActivityReviewNote(
                            recordId = rec.id,
                            dateLocal = dateLocal,
                            outcomeNote = outcomeDrafts[rec.id].orEmpty().trim(),
                            feynmanExplanation = feynmanDrafts[rec.id].orEmpty().trim(),
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                    onSaveFullDailyReview(
                        dateLocal,
                        summaryDraft.trim().ifBlank { snapshot.generatedSummary },
                        q1,
                        q2,
                        answer1Draft.trim(),
                        answer2Draft.trim(),
                        generalNoteDraft.trim(),
                        notesToSave
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WalletMint),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text(
                    text = "✓ Lưu Daily Review ngày $dateLocal",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onExportJsonClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFDCE8DF)),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text(
                    text = "⇩ Xuất JSON (kèm Daily Review & Ngân sách)",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalletMintDark
                )
            }
        }
    }
}

@Composable
private fun ReviewMetricBox(
    label: String,
    valueText: String,
    subLabel: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE5ECE7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(11.dp)) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = valueText,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = WalletInk
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subLabel,
                fontSize = 9.sp,
                color = WalletMuted
            )
        }
    }
}

