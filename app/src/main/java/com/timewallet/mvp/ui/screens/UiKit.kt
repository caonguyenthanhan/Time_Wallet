package com.timewallet.mvp.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timewallet.mvp.R
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.DonutSlice
import com.timewallet.mvp.ui.AppTab
import com.timewallet.mvp.ui.theme.WalletCardBg
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletIsDark
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintAccent
import com.timewallet.mvp.ui.theme.WalletMintSoft
import com.timewallet.mvp.ui.theme.WalletMintTint
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletRing
import com.timewallet.mvp.ui.theme.WalletSoftNeutral
import com.timewallet.mvp.ui.theme.WalletStroke
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

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

/** Viên thuốc bấm gọn mắt nhưng vẫn giữ vùng chạm tối thiểu >= 48.dp bao bên ngoài. */
@Composable
fun WalletPill(
    onClickLabel: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(100.dp),
    containerColor: Color = WalletMintTint,
    borderColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 11.dp, vertical = 6.dp),
    selected: Boolean? = null,
    role: Role = Role.Button,
    contentDescription: String? = null,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier
            .semantics {
                if (selected != null) this.selected = selected
                if (!contentDescription.isNullOrBlank()) this.contentDescription = contentDescription
            }
            .walletClickable(onClickLabel = onClickLabel, role = role, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(containerColor)
                .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

enum class WalletGlyph {
    HOME, CLOCK, WALLY_BOT, SPARK, LIST, BARS,
    PLUS, CHEVRON, SEND, STOP, MORE, DICE, BOOK,
    EDIT, DELETE, CONFIRM, CLOSE, ERASER, NOTIFY, SETTINGS, GOAL, BACK, PAUSE, WARNING, ERROR,
    ARROW_UP, DRAG
}

fun AppTab.glyph(): WalletGlyph = when (this) {
    AppTab.SCHEDULE -> WalletGlyph.LIST
    AppTab.TASKS -> WalletGlyph.CONFIRM
    AppTab.FOCUS -> WalletGlyph.CLOCK
    AppTab.MORE -> WalletGlyph.BARS
}

private fun vectorResForGlyph(glyph: WalletGlyph, filled: Boolean): Int? = when (glyph) {
    WalletGlyph.HOME -> if (filled) R.drawable.ic_nav_overview_selected else R.drawable.ic_nav_overview_default
    WalletGlyph.CLOCK -> if (filled) R.drawable.ic_nav_timer_selected else R.drawable.ic_nav_timer_default
    WalletGlyph.WALLY_BOT -> if (filled) R.drawable.ic_nav_wally_selected else R.drawable.ic_nav_wally_default
    WalletGlyph.LIST -> if (filled) R.drawable.ic_nav_journal_selected else R.drawable.ic_nav_journal_default
    WalletGlyph.BARS -> if (filled) R.drawable.ic_nav_analytics_selected else R.drawable.ic_nav_analytics_default
    WalletGlyph.PLUS -> R.drawable.ic_action_add_default
    WalletGlyph.EDIT -> R.drawable.ic_action_edit_default
    WalletGlyph.DELETE -> R.drawable.ic_action_delete_error
    WalletGlyph.CONFIRM -> R.drawable.ic_action_confirm_default
    WalletGlyph.CLOSE -> R.drawable.ic_action_cancel_default
    WalletGlyph.ERASER -> R.drawable.ic_action_eraser_default
    WalletGlyph.NOTIFY -> R.drawable.ic_action_notify_default
    WalletGlyph.SETTINGS -> R.drawable.ic_action_settings_default
    WalletGlyph.GOAL -> R.drawable.ic_action_goal_default
    WalletGlyph.BACK -> R.drawable.ic_action_back_default
    WalletGlyph.PAUSE -> R.drawable.ic_action_pause_default
    WalletGlyph.WARNING -> R.drawable.ic_warning_default
    WalletGlyph.ERROR -> R.drawable.ic_error_default
    else -> null
}

private fun vectorResForCategory(category: TimeCategory): Int = when (category) {
    TimeCategory.WORK -> R.drawable.ic_cat_work_default
    TimeCategory.LEARN -> R.drawable.ic_cat_learn_default
    TimeCategory.HEALTH -> R.drawable.ic_cat_move_default
    TimeCategory.LIFE -> R.drawable.ic_cat_home_default
    TimeCategory.REST -> R.drawable.ic_cat_rest_default
    TimeCategory.SOCIAL -> R.drawable.ic_cat_connect_default
    TimeCategory.FUN -> R.drawable.ic_cat_fun_default
    TimeCategory.SLEEP -> R.drawable.ic_cat_sleep_default
    TimeCategory.OTHER -> R.drawable.ic_cat_other_default
}

/** Icon vector 24dp chuẩn Style Guide v2 cho 9 danh mục hoạt động. */
@Composable
fun CategoryIcon(
    category: TimeCategory,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = WalletMint
) {
    Icon(
        painter = painterResource(id = vectorResForCategory(category)),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size)
    )
}

/** Biểu tượng đồng bộ từ VectorDrawable Style Guide v2 hoặc Canvas cùng tỉ lệ nét 2dp. */
@Composable
fun GlyphIcon(
    glyph: WalletGlyph,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    filled: Boolean = false
) {
    if (glyph == WalletGlyph.CLOCK && filled && color == WalletMint) {
        Icon(
            painter = painterResource(id = R.drawable.ic_nav_timer_pressed),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = modifier.size(size)
        )
        return
    }
    val vectorRes = vectorResForGlyph(glyph, filled)
    if (vectorRes != null) {
        Icon(
            painter = painterResource(id = vectorRes),
            contentDescription = null,
            tint = color,
            modifier = modifier.size(size)
        )
        return
    }
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val sw = s * 0.09f
        val line = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val style = if (filled) Fill else line
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        when (glyph) {
            WalletGlyph.SPARK -> {
                val path = Path().apply {
                    moveTo(0.5f * s, 0.10f * s); lineTo(0.59f * s, 0.41f * s); lineTo(0.90f * s, 0.5f * s)
                    lineTo(0.59f * s, 0.59f * s); lineTo(0.5f * s, 0.90f * s); lineTo(0.41f * s, 0.59f * s)
                    lineTo(0.10f * s, 0.5f * s); lineTo(0.41f * s, 0.41f * s); close()
                }
                drawPath(path, color, style = style)
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
            WalletGlyph.BOOK -> {
                drawRoundRect(color, topLeft = p(0.2f, 0.14f), size = Size(s * 0.6f, s * 0.72f), cornerRadius = CornerRadius(s * 0.08f), style = line)
                drawLine(color, p(0.34f, 0.36f), p(0.66f, 0.36f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, p(0.34f, 0.54f), p(0.58f, 0.54f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            WalletGlyph.ARROW_UP -> {
                drawLine(color, p(0.5f, 0.80f), p(0.5f, 0.20f), strokeWidth = sw * 1.15f, cap = StrokeCap.Round)
                val head = Path().apply { moveTo(0.25f * s, 0.45f * s); lineTo(0.5f * s, 0.20f * s); lineTo(0.75f * s, 0.45f * s) }
                drawPath(head, color, style = Stroke(width = sw * 1.15f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            WalletGlyph.DRAG -> {
                listOf(0.37f, 0.63f).forEach { x ->
                    listOf(0.25f, 0.50f, 0.75f).forEach { y ->
                        drawCircle(color, radius = s * 0.075f, center = p(x, y))
                    }
                }
            }
            else -> Unit
        }
    }
}

/** Thẻ nội dung chính: bề mặt nổi phân tầng theo sáng/tối, không viền trang trí dư thừa. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    tint: Color = WalletCardBg,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = tint),
        elevation = CardDefaults.cardElevation(defaultElevation = if (WalletIsDark) 0.dp else 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(padding)) { content() }
    }
}

/** Tiêu đề màn hình rõ, có điểm nhấn thị giác lớn hơn. */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            letterSpacing = (-0.4).sp,
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
                color = WalletMintAccent,
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

/** Biểu tượng danh mục chuẩn Style Guide v2 (VectorDrawable 24dp trong ô màu danh mục). */
@Composable
fun CategoryBadge(category: TimeCategory, size: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(Color(category.colorHex).copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        CategoryIcon(
            category = category,
            size = (size.value * 0.56f).dp,
            tint = WalletInk
        )
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
                .size(72.dp)
                .clip(CircleShape)
                .background(WalletSoftNeutral),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(WalletMintSoft),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(glyph = glyph, color = WalletMintAccent, size = 26.dp)
            }
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
                colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                modifier = Modifier.height(48.dp)
            ) {
                Text(text = actionLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Giới hạn tối đa [maxSlices] lát cắt (mặc định 6) để donut và chú giải luôn gọn, đồng màu và không tràn bảng.
 * Phần vượt ngưỡng được gộp thành một lát `Khác` giữ nguyên tổng 100%.
 */
internal fun capDonutSlices(slices: List<DonutSlice>, maxSlices: Int = 6): List<DonutSlice> {
    if (maxSlices <= 0) return emptyList()
    val positive = slices.filter { it.seconds > 0L }
    if (positive.isEmpty()) return emptyList()
    val totalSeconds = positive.sumOf { it.seconds }.coerceAtLeast(1L)
    if (positive.size <= maxSlices) {
        return positive.map { slice ->
            if (slice.fraction > 0f) slice else slice.copy(fraction = slice.seconds.toFloat() / totalSeconds.toFloat())
        }
    }
    if (maxSlices == 1) {
        return listOf(DonutSlice(TimeCategory.OTHER, totalSeconds, 1f))
    }
    val head = positive.filter { it.category != TimeCategory.OTHER }.take(maxSlices - 1)
    val headCategories = head.map { it.category }.toSet()
    val otherSeconds = positive.filterNot { it.category in headCategories }.sumOf { it.seconds }
    val combined = if (otherSeconds > 0L) {
        head + DonutSlice(
            category = TimeCategory.OTHER,
            seconds = otherSeconds,
            fraction = otherSeconds.toFloat() / totalSeconds.toFloat()
        )
    } else {
        head
    }
    return combined.map { it.copy(fraction = it.seconds.toFloat() / totalSeconds.toFloat()) }
}

/** Hit-test thuần theo góc/bán kính vòng donut (-90° = 12 giờ, chiều kim đồng hồ). Trả null nếu chạm vào lỗ giữa hoặc ngoài vòng. */
internal fun hitTestDonutSliceIndex(
    slices: List<DonutSlice>,
    touchX: Float,
    touchY: Float,
    width: Float,
    height: Float,
    ringWidthPx: Float
): Int? {
    if (slices.isEmpty() || width <= 0f || height <= 0f) return null
    val cx = width / 2f
    val cy = height / 2f
    val dx = touchX - cx
    val dy = touchY - cy
    val dist = hypot(dx, dy)
    val maxR = minOf(width, height) / 2f
    val outerHitR = maxR + ringWidthPx * 0.45f
    val innerHitR = (maxR - ringWidthPx * 1.6f).coerceAtLeast(0f)
    if (dist !in innerHitR..outerHitR) return null
    val rawDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    val angleFromTop = ((rawDeg + 90f) % 360f + 360f) % 360f
    var accum = 0f
    slices.forEachIndexed { idx, slice ->
        val sweep = (360f * slice.fraction).coerceAtLeast(0f)
        if (angleFromTop >= accum && (angleFromTop < accum + sweep || idx == slices.lastIndex)) {
            return idx
        }
        accum += sweep
    }
    return null
}

/** Donut 2D phẳng vẽ bằng Canvas: tối đa 6 lát, khe hở đều giữa các lát, chạm lát/chú giải đẩy lát ra nhẹ & hiện chi tiết ở giữa. */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centerTop: String,
    modifier: Modifier = Modifier,
    centerBottom: String? = null,
    ringWidth: Dp = 16.dp,
    selectedIndex: Int? = null,
    onSelectSlice: ((Int?) -> Unit)? = null
) {
    val displaySlices = remember(slices) { capDonutSlices(slices, maxSlices = 6) }
    val density = LocalDensity.current
    val popOutDp = 6.dp
    val ringPx = with(density) { ringWidth.toPx() }
    val activeSelection = selectedIndex?.takeIf { it in displaySlices.indices }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val interactiveModifier = if (onSelectSlice != null && displaySlices.isNotEmpty()) {
            Modifier
                .fillMaxSize()
                .pointerInput(displaySlices, activeSelection) {
                    detectTapGestures { offset ->
                        val hit = hitTestDonutSliceIndex(
                            slices = displaySlices,
                            touchX = offset.x,
                            touchY = offset.y,
                            width = size.width.toFloat(),
                            height = size.height.toFloat(),
                            ringWidthPx = ringPx
                        )
                        val next = when {
                            hit == null -> null
                            hit == activeSelection -> null
                            else -> hit
                        }
                        onSelectSlice(next)
                    }
                }
        } else {
            Modifier.fillMaxSize()
        }

        val reduceMotion = com.timewallet.mvp.ui.theme.WalletReduceMotion
        val animatedPopFractions = displaySlices.indices.map { idx ->
            val target = if (activeSelection == idx) 1f else 0f
            if (reduceMotion) {
                target
            } else {
                val anim by animateFloatAsState(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 180),
                    label = "donutPop_$idx"
                )
                anim
            }
        }

        val ringColor = WalletRing
        Canvas(modifier = interactiveModifier) {
            val strokePx = ringWidth.toPx()
            val maxPopPx = popOutDp.toPx()
            val inset = strokePx / 2f + maxPopPx
            val arcDiameter = (minOf(size.width, size.height) - inset * 2f).coerceAtLeast(strokePx)
            val topLeftBase = Offset(
                (size.width - arcDiameter) / 2f,
                (size.height - arcDiameter) / 2f
            )
            val arcSize = Size(arcDiameter, arcDiameter)

            drawArc(
                color = ringColor, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeftBase, size = arcSize, style = Stroke(width = strokePx)
            )
            val gapDeg = if (displaySlices.size >= 2) 2.6f else 0f
            var start = -90f
            displaySlices.forEachIndexed { idx, slice ->
                val rawSweep = 360f * slice.fraction
                val drawStart = start + gapDeg / 2f
                val drawSweep = (rawSweep - gapDeg).coerceAtLeast(if (displaySlices.size == 1) 360f else 1.2f)
                val midRad = Math.toRadians((start + rawSweep / 2f).toDouble())
                val shiftPx = animatedPopFractions.getOrElse(idx) { 0f } * maxPopPx
                val dx = (cos(midRad) * shiftPx).toFloat()
                val dy = (sin(midRad) * shiftPx).toFloat()
                val isSelected = activeSelection == idx
                val baseColor = Color(slice.category.colorHex)
                val sliceColor = when {
                    activeSelection == null -> baseColor
                    isSelected -> baseColor
                    else -> baseColor.copy(alpha = 0.45f)
                }
                drawArc(
                    color = sliceColor,
                    startAngle = drawStart,
                    sweepAngle = drawSweep,
                    useCenter = false,
                    topLeft = Offset(topLeftBase.x + dx, topLeftBase.y + dy),
                    size = arcSize,
                    style = Stroke(
                        width = if (isSelected) strokePx + 2.dp.toPx() else strokePx,
                        cap = StrokeCap.Butt
                    )
                )
                start += rawSweep
            }
        }
        Column(
            modifier = Modifier.widthIn(max = 78.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = centerTop,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = WalletInk,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            if (centerBottom != null) {
                Text(
                    text = centerBottom,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    color = WalletMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Donut + chú giải. Khi màn hẹp hoặc chữ hệ thống lớn, chú giải xuống dưới donut để tên danh mục không bị cắt.
 * Hỗ trợ chọn lát cắt đồng bộ giữa biểu đồ và các dòng chú giải.
 */
@Composable
fun DonutWithLegend(
    slices: List<DonutSlice>,
    centerTop: String,
    description: String,
    modifier: Modifier = Modifier,
    centerBottom: String? = null,
    selectedIndex: Int? = null,
    onSelectSlice: ((Int?) -> Unit)? = null,
    legend: @Composable () -> Unit
) {
    val stacked = LocalDensity.current.fontScale > 1.15f || LocalConfiguration.current.screenWidthDp < 340
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
                selectedIndex = selectedIndex,
                onSelectSlice = onSelectSlice,
                modifier = Modifier.size(152.dp).describe(description)
            )
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) { legend() }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DonutChart(
                slices = slices,
                centerTop = centerTop,
                centerBottom = centerBottom,
                ringWidth = 15.dp,
                selectedIndex = selectedIndex,
                onSelectSlice = onSelectSlice,
                modifier = Modifier.size(126.dp).describe(description)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { legend() }
        }
    }
}

/** Một dòng chú giải: chấm màu + icon vector danh mục + tên + giá trị; phần trăm và chọn tương tác tuỳ chọn. */
@Composable
fun LegendRow(
    label: String,
    value: String,
    dot: Color,
    modifier: Modifier = Modifier,
    category: TimeCategory? = null,
    percentText: String? = null,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .then(
            if (isSelected) {
                Modifier
                    .background(WalletMintTint)
                    .border(1.dp, WalletMint.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            } else {
                Modifier
            }
        )
        .then(
            if (onClick != null) {
                Modifier
                    .semantics { selected = isSelected }
                    .walletClickable(onClickLabel = "Xem chi tiết $label", onClick = onClick)
            } else {
                Modifier
            }
        )
        .padding(horizontal = if (onClick != null) 4.dp else 0.dp, vertical = 4.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(modifier = Modifier.size(if (isSelected) 11.dp else 9.dp).clip(CircleShape).background(dot))
        if (category != null) {
            CategoryIcon(
                category = category,
                size = 15.dp,
                tint = if (isSelected) WalletMint else WalletInk
            )
        }
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = WalletInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(text = value, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = WalletInk, maxLines = 1, softWrap = false)
        if (percentText != null) {
            Text(
                text = percentText,
                fontSize = 11.5.sp,
                color = if (isSelected) WalletMint else WalletMuted,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End
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
 * Hỗ trợ trạng thái hoàn thành (chấm đặc), đang diễn ra (vòng xoay động) và đường nối dừng gọn ở chấm cuối.
 */
@Composable
fun Modifier.timelineRail(
    color: Color,
    isFirst: Boolean,
    isLast: Boolean,
    isInProgress: Boolean = false,
    isPlanned: Boolean = false,
    railCenterX: Dp = 11.dp
): Modifier {
    val reduceMotion = com.timewallet.mvp.ui.theme.WalletReduceMotion
    val spinDeg = if (isInProgress && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "timelineSpinner")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "timelineSpinnerDeg"
        )
        angle
    } else {
        0f
    }

    return this.drawBehind {
        val cx = railCenterX.toPx()
        val dotY = 26.dp.toPx()
        val lineW = 2.dp.toPx()
        val haloR = 8.dp.toPx()
        val dotR = 5.5.dp.toPx()
        val connectorColor = Color(0xFFCADDD0)

        if (!isFirst) {
            drawLine(connectorColor, Offset(cx, 0f), Offset(cx, (dotY - haloR).coerceAtLeast(0f)), strokeWidth = lineW)
        }
        if (!isLast) {
            drawLine(connectorColor, Offset(cx, (dotY + haloR).coerceAtMost(size.height)), Offset(cx, size.height), strokeWidth = lineW)
        }

        drawCircle(Color.White, radius = haloR, center = Offset(cx, dotY))
        when {
            isInProgress -> {
                val ringR = 7.dp.toPx()
                val ringStroke = 2.5.dp.toPx()
                drawCircle(color = color.copy(alpha = 0.22f), radius = ringR, center = Offset(cx, dotY), style = Stroke(width = ringStroke))
                drawArc(
                    color = color,
                    startAngle = spinDeg,
                    sweepAngle = 220f,
                    useCenter = false,
                    topLeft = Offset(cx - ringR, dotY - ringR),
                    size = Size(ringR * 2f, ringR * 2f),
                    style = Stroke(width = ringStroke, cap = StrokeCap.Round)
                )
                drawCircle(color, radius = 3.2.dp.toPx(), center = Offset(cx, dotY))
            }
            isPlanned -> {
                drawCircle(color = color.copy(alpha = 0.16f), radius = dotR, center = Offset(cx, dotY))
                drawCircle(color = color, radius = dotR, center = Offset(cx, dotY), style = Stroke(width = 2.dp.toPx()))
            }
            else -> {
                drawCircle(color, radius = dotR, center = Offset(cx, dotY))
            }
        }
    }
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

/**
 * Avatar linh vật Wally theo Style Guide v3:
 * - >= 80dp (empty/onboarding >= 96dp): dùng mascot toàn thân WebP chi tiết cao xuất từ Wally_Vector_Detailed ([R.drawable.ic_wally]).
 * - 32–79dp (Tổng quan, AI Coach, Chat bubble): dùng avatar mặt robot 2D ([R.drawable.ic_wally_avatar_2d]).
 */
@Composable
fun walletSwitchColors(checkedTrackColor: Color = WalletMint): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = checkedTrackColor,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = if (WalletIsDark) Color(0xFFB8C0C8) else Color(0xFF5D6B65),
    uncheckedTrackColor = if (WalletIsDark) Color(0xFF282D32) else Color(0xFFE0E5DF),
    uncheckedBorderColor = WalletStroke
)

@Composable
fun WallyMascotAvatar(
    size: Dp,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val isFullBody = size >= 80.dp
    Image(
        painter = painterResource(id = if (isFullBody) R.drawable.ic_wally else R.drawable.ic_wally_avatar_2d),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = if (isFullBody) {
            modifier.size(size)
        } else {
            modifier.size(size).clip(CircleShape)
        }
    )
}
