package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.DonutSlice
import com.timewallet.mvp.ui.AppTab
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintTint
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletRing

/*
 * v1.0.11 UI POLISH — bộ thành phần dùng chung. Chỉ là lớp trình bày: không chứa nghiệp vụ, dữ liệu hay phép tính.
 */

/** Vùng chạm >= 48dp + vai trò trợ năng; mọi nút tự vẽ nên dùng hàm này. */
fun Modifier.walletClickable(
    onClickLabel: String? = null,
    role: Role = Role.Button,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = this
    .minimumInteractiveComponentSize()
    .semantics { this.role = role }
    .clickable(enabled = enabled, onClickLabel = onClickLabel, onClick = onClick)

enum class WalletGlyph { HOME, CLOCK, SPARK, LIST, BARS, PLUS, CHEVRON, SEND, STOP, MORE, DICE, BOOK, EDIT, CLOSE }

fun AppTab.glyph(): WalletGlyph = when (this) {
    AppTab.OVERVIEW -> WalletGlyph.HOME
    AppTab.TIMER -> WalletGlyph.CLOCK
    AppTab.CHAT -> WalletGlyph.SPARK
    AppTab.HISTORY -> WalletGlyph.LIST
    AppTab.INSIGHTS -> WalletGlyph.BARS
}

/** Biểu tượng vẽ bằng Canvas để mọi tab/nút cùng một kiểu nét và kích thước (không phụ thuộc phông ký hiệu). */
@Composable
fun GlyphIcon(
    glyph: WalletGlyph,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    filled: Boolean = false
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val sw = s * 0.09f
        val line = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val style = if (filled) Fill else line
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        when (glyph) {
            WalletGlyph.HOME -> {
                val cell = s * 0.32f
                val r = CornerRadius(s * 0.09f)
                listOf(0.14f to 0.14f, 0.54f to 0.14f, 0.14f to 0.54f, 0.54f to 0.54f).forEach { (x, y) ->
                    drawRoundRect(color, topLeft = p(x, y), size = Size(cell, cell), cornerRadius = r, style = style)
                }
            }
            WalletGlyph.CLOCK -> {
                drawCircle(color, radius = s * 0.37f, center = p(0.5f, 0.5f), style = style)
                val hand = if (filled) Color.White else color
                drawLine(hand, p(0.5f, 0.5f), p(0.5f, 0.28f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(hand, p(0.5f, 0.5f), p(0.66f, 0.58f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            WalletGlyph.SPARK -> {
                val path = Path().apply {
                    moveTo(0.5f * s, 0.10f * s); lineTo(0.59f * s, 0.41f * s); lineTo(0.90f * s, 0.5f * s)
                    lineTo(0.59f * s, 0.59f * s); lineTo(0.5f * s, 0.90f * s); lineTo(0.41f * s, 0.59f * s)
                    lineTo(0.10f * s, 0.5f * s); lineTo(0.41f * s, 0.41f * s); close()
                }
                drawPath(path, color, style = style)
            }
            WalletGlyph.LIST -> {
                listOf(0.25f, 0.5f, 0.75f).forEach { y ->
                    drawCircle(color, radius = s * 0.065f, center = p(0.2f, y))
                    drawLine(color, p(0.36f, y), p(0.86f, y), strokeWidth = sw, cap = StrokeCap.Round)
                }
            }
            WalletGlyph.BARS -> {
                val r = CornerRadius(s * 0.05f)
                val bw = s * 0.17f
                listOf(0.16f to 0.40f, 0.415f to 0.70f, 0.67f to 0.54f).forEach { (x, hFrac) ->
                    drawRoundRect(color, topLeft = p(x, 0.86f - hFrac), size = Size(bw, hFrac * s), cornerRadius = r)
                }
            }
            WalletGlyph.PLUS -> {
                drawLine(color, p(0.5f, 0.2f), p(0.5f, 0.8f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, p(0.2f, 0.5f), p(0.8f, 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            WalletGlyph.CHEVRON -> {
                val path = Path().apply { moveTo(0.38f * s, 0.22f * s); lineTo(0.64f * s, 0.5f * s); lineTo(0.38f * s, 0.78f * s) }
                drawPath(path, color, style = line)
            }
            WalletGlyph.SEND -> {
                drawLine(color, p(0.5f, 0.82f), p(0.5f, 0.22f), strokeWidth = sw * 1.1f, cap = StrokeCap.Round)
                val head = Path().apply { moveTo(0.26f * s, 0.46f * s); lineTo(0.5f * s, 0.22f * s); lineTo(0.74f * s, 0.46f * s) }
                drawPath(head, color, style = Stroke(width = sw * 1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            WalletGlyph.STOP -> {
                drawRoundRect(color, topLeft = p(0.28f, 0.28f), size = Size(s * 0.44f, s * 0.44f), cornerRadius = CornerRadius(s * 0.08f))
            }
            WalletGlyph.MORE -> {
                listOf(0.2f, 0.5f, 0.8f).forEach { y -> drawCircle(color, radius = s * 0.07f, center = p(0.5f, y)) }
            }
            WalletGlyph.DICE -> {
                drawRoundRect(color, topLeft = p(0.14f, 0.14f), size = Size(s * 0.72f, s * 0.72f), cornerRadius = CornerRadius(s * 0.16f), style = line)
                listOf(0.33f to 0.33f, 0.5f to 0.5f, 0.67f to 0.67f).forEach { (x, y) -> drawCircle(color, radius = s * 0.06f, center = p(x, y)) }
            }
            WalletGlyph.EDIT -> {
                drawLine(color, p(0.26f, 0.74f), p(0.70f, 0.30f), strokeWidth = sw * 1.7f, cap = StrokeCap.Round)
                drawLine(color, p(0.2f, 0.86f), p(0.52f, 0.86f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            WalletGlyph.CLOSE -> {
                drawLine(color, p(0.26f, 0.26f), p(0.74f, 0.74f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, p(0.74f, 0.26f), p(0.26f, 0.74f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            WalletGlyph.BOOK -> {
                drawRoundRect(color, topLeft = p(0.2f, 0.14f), size = Size(s * 0.6f, s * 0.72f), cornerRadius = CornerRadius(s * 0.08f), style = line)
                drawLine(color, p(0.34f, 0.36f), p(0.66f, 0.36f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, p(0.34f, 0.54f), p(0.58f, 0.54f), strokeWidth = sw, cap = StrokeCap.Round)
            }
        }
    }
}

/** Thẻ nội dung chính: nền trắng, không viền, bóng nhẹ (thay cho thẻ viền xám lặp lại ở mọi khối). */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = tint),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(padding)) { content() }
    }
}

/** Tiêu đề màn hình gọn (thay cho khối eyebrow + tiêu đề + đoạn mô tả). */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = WalletInk,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

/** Tiêu đề khối: chữ nhỏ + hành động bên phải tuỳ chọn (không có đoạn mô tả). */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = WalletInk,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        if (actionText != null && onAction != null) {
            Text(
                text = actionText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = WalletMint,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .walletClickable(onClickLabel = actionText, onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

/** Biểu tượng danh mục: emoji trong ô bo góc có màu của danh mục. */
@Composable
fun CategoryBadge(category: TimeCategory, size: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(Color(category.colorHex).copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = category.emoji, fontSize = (size.value * 0.46f).sp)
    }
}

/** Trạng thái rỗng: biểu tượng + một câu ngắn + (tuỳ chọn) một nút hành động. */
@Composable
fun EmptyState(
    glyph: WalletGlyph,
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(WalletMintTint),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(glyph = glyph, color = WalletMint, size = 30.dp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = WalletInk, textAlign = TextAlign.Center)
        if (hint != null) {
            Text(text = hint, fontSize = 12.5.sp, color = WalletMuted, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WalletMint),
                modifier = Modifier.height(48.dp)
            ) {
                Text(text = actionLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Donut vẽ bằng Canvas; không có lát nào thì chỉ vẽ vòng nền (không biểu đồ giả). */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centerTop: String,
    modifier: Modifier = Modifier,
    centerBottom: String? = null,
    ringWidth: Dp = 16.dp
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = ringWidth.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = WalletRing, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = strokePx)
            )
            var start = -90f
            slices.forEach { slice ->
                val sweep = 360f * slice.fraction
                drawArc(
                    color = Color(slice.category.colorHex), startAngle = start,
                    sweepAngle = (sweep - 2f).coerceAtLeast(0.5f), useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerTop,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = WalletInk,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false
            )
            if (centerBottom != null) {
                Text(text = centerBottom, fontSize = 11.sp, color = WalletMuted, maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * Donut + chú giải. Khi màn hẹp hoặc chữ hệ thống lớn, chú giải xuống dưới donut để tên danh mục không bị cắt.
 * Nội dung chú giải do nơi gọi cung cấp (giữ emoji + tên + giờ + % trong cùng một dòng, không chỉ dựa vào màu).
 */
@Composable
fun DonutWithLegend(
    slices: List<DonutSlice>,
    centerTop: String,
    description: String,
    modifier: Modifier = Modifier,
    centerBottom: String? = null,
    legend: @Composable () -> Unit
) {
    val stacked = LocalDensity.current.fontScale > 1.15f || LocalConfiguration.current.screenWidthDp < 360
    if (stacked) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DonutChart(
                slices = slices,
                centerTop = centerTop,
                centerBottom = centerBottom,
                modifier = Modifier.size(148.dp).describe(description)
            )
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) { legend() }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DonutChart(
                slices = slices,
                centerTop = centerTop,
                centerBottom = centerBottom,
                modifier = Modifier.size(132.dp).describe(description)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) { legend() }
        }
    }
}

/** Một dòng chú giải: chấm màu + (emoji) tên + giá trị; phần trăm tuỳ chọn. Không chỉ dựa vào màu. */
@Composable
fun LegendRow(label: String, value: String, dot: Color, modifier: Modifier = Modifier, percentText: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dot))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = WalletInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = WalletInk, maxLines = 1, softWrap = false)
        if (percentText != null) {
            Text(
                text = percentText,
                fontSize = 12.sp,
                color = WalletMuted,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
                modifier = Modifier.width(40.dp)
            )
        }
    }
}

/** Giải thích dài ẩn mặc định: bấm "ⓘ" để mở (thay cho đoạn văn thường trực). */
@Composable
fun InfoToggle(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .walletClickable(onClickLabel = if (open) "Thu gọn: $label" else "Xem: $label") { open = !open }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "ⓘ", fontSize = 14.sp, color = WalletMuted)
            Text(text = label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = WalletMuted)
            Text(text = if (open) "▴" else "▾", fontSize = 12.sp, color = WalletMuted)
        }
        if (open) {
            Spacer(modifier = Modifier.height(4.dp))
            Column(modifier = Modifier.padding(horizontal = 6.dp)) { content() }
        }
    }
}

/** Hàng thanh tiến độ mảnh dùng chung (nền xám nhạt + phần đã đạt). */
@Composable
fun SlimProgress(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height))
            .background(WalletRing)
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.03f, 1f))
                    .fillMaxSize()
                    .clip(RoundedCornerShape(height))
                    .background(color)
            )
        }
    }
}

/**
 * Trục dọc + chấm của timeline, vẽ phía sau hàng (không dùng IntrinsicSize nên không bao giờ làm hụt chiều cao khi chữ xuống dòng).
 * Chừa sẵn 28dp bên trái cho trục.
 */
fun Modifier.timelineRail(color: Color, isFirst: Boolean, isLast: Boolean): Modifier = this.drawBehind {
    val cx = 11.dp.toPx()
    val dotY = 26.dp.toPx()
    val w = 2.dp.toPx()
    if (!isFirst) drawLine(Color(0xFFCADDD0), Offset(cx, 0f), Offset(cx, dotY), strokeWidth = w)
    if (!isLast) drawLine(Color(0xFFCADDD0), Offset(cx, dotY), Offset(cx, size.height), strokeWidth = w)
    drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(cx, dotY))
    drawCircle(color, radius = 5.dp.toPx(), center = Offset(cx, dotY))
}

/**
 * Cỡ chữ theo sp nhưng giới hạn mức phóng theo cỡ chữ hệ thống (mặc định tối đa 1.15x): nhãn nhỏ vẫn to lên cho người dùng
 * chữ lớn nhưng không vỡ bố cục (nhãn tab, nhãn biểu đồ, mốc giờ). `cappedSpValue(base, fs, cap) * fs` = cỡ hiển thị thật.
 */
fun cappedSpValue(base: Float, fontScale: Float, cap: Float = 1.15f): Float =
    if (fontScale <= 0f) base else base * minOf(fontScale, cap) / fontScale

@Composable
fun cappedSp(base: Float, cap: Float = 1.15f): TextUnit = cappedSpValue(base, LocalDensity.current.fontScale, cap).sp

/** Nội dung mô tả trợ năng cho vùng đồ họa (donut, thanh...). */
fun Modifier.describe(text: String): Modifier = this.semantics { contentDescription = text }
