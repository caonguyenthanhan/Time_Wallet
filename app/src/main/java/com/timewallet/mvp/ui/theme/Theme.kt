package com.timewallet.mvp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density

@Immutable
internal data class WalletPalette(
    val isDark: Boolean,
    val ink: Color,
    val muted: Color,
    val bg: Color,
    val stroke: Color,
    val cardBg: Color,
    val untrackedBar: Color,
    val ring: Color,
    val mintAccent: Color,
    val mintSoft: Color,
    val mintTint: Color,
    val softNeutral: Color,
    val divider: Color,
    val microBg: Color,
    val microText: Color,
    val dangerBg: Color,
    val dangerBorder: Color,
    val dangerText: Color,
    val warningBg: Color,
    val warningBorder: Color,
    val warningText: Color,
    val infoBg: Color,
    val infoText: Color,
    val clarificationBg: Color
)

private val LightWalletPalette = WalletPalette(
    isDark = false,
    ink = Color(0xFF141B18),
    muted = Color(0xFF5D6B65),
    bg = Color(0xFFF6F7F5),
    stroke = Color(0xFFE3E7E2),
    cardBg = Color(0xFFFFFFFF),
    untrackedBar = Color(0xFFE2E6E1),
    ring = Color(0xFFE8ECE7),
    mintAccent = Color(0xFF156E58),
    mintSoft = Color(0xFFE0F2EA),
    mintTint = Color(0xFFF0F6F3),
    softNeutral = Color(0xFFEDF0EC),
    divider = Color(0xFFECEFEA),
    microBg = Color(0xFFF0F6F3),
    microText = Color(0xFF156E58),
    dangerBg = Color(0xFFFFF0EF),
    dangerBorder = Color(0xFFF5D4CA),
    dangerText = Color(0xFFB63F37),
    warningBg = Color(0xFFFFF4E2),
    warningBorder = Color(0xFFF2D6A2),
    warningText = Color(0xFFA24C18),
    infoBg = Color(0xFFEEF4FF),
    infoText = Color(0xFF185FA5),
    clarificationBg = Color(0xFFFFF8E8)
)

private val DarkWalletPalette = WalletPalette(
    isDark = true,
    ink = Color(0xFFF3F5F4),
    muted = Color(0xFFA0A8B0),
    bg = Color(0xFF111315),
    stroke = Color(0xFF2D3237),
    cardBg = Color(0xFF1A1D20),
    untrackedBar = Color(0xFF2A2F34),
    ring = Color(0xFF262B30),
    mintAccent = Color(0xFF5CE0B3),
    mintSoft = Color(0xFF18362B),
    mintTint = Color(0xFF162721),
    softNeutral = Color(0xFF23272B),
    divider = Color(0xFF262A2E),
    microBg = Color(0xFF1B2B25),
    microText = Color(0xFF7CEBC5),
    dangerBg = Color(0xFF331D1E),
    dangerBorder = Color(0xFF6B393A),
    dangerText = Color(0xFFFFA8A0),
    warningBg = Color(0xFF2E2416),
    warningBorder = Color(0xFF73552B),
    warningText = Color(0xFFFFD694),
    infoBg = Color(0xFF17273D),
    infoText = Color(0xFF9BCBFF),
    clarificationBg = Color(0xFF2B2317)
)

private val LocalWalletPalette = staticCompositionLocalOf { LightWalletPalette }
private val LocalReducedMotion = staticCompositionLocalOf { false }

// Theme-aware surface & typography tokens (tự thích ứng Light / Dark qua TimeWalletTheme)
val WalletReduceMotion: Boolean @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
val WalletIsDark: Boolean @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.isDark
val WalletInk: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.ink
val WalletMuted: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.muted
val WalletBg: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.bg
val WalletStroke: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.stroke
val WalletCardBg: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.cardBg
val WalletUntrackedBar: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.untrackedBar
val WalletRing: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.ring
val WalletMintAccent: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.mintAccent
val WalletSoftNeutral: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.softNeutral
val WalletDivider: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.divider
val WalletInfoBg: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.infoBg
val WalletInfoText: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.infoText
val WalletWarningBorder: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.warningBorder
val WalletClarificationBg: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.clarificationBg

// Solid brand colors (chỉ dùng cho nền nút/banner có chữ trắng) & theme-aware semantic tokens
val WalletMint = Color(0xFF176B57)
val WalletMintDark = Color(0xFF103F35)
val WalletMintMid = Color(0xFF207E68)
val WalletMintBright = Color(0xFF258F76)
val WalletDangerSolid = Color(0xFFB3261E)
val WalletMintSoft: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.mintSoft
val WalletMicroBg: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.microBg
val WalletMicroText: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.microText
val WalletDangerBg: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.dangerBg
val WalletDangerBorder: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.dangerBorder
val WalletDangerText: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.dangerText
val WalletWarningBg: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.warningBg
val WalletWarningText: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.warningText
val WalletDisabled: Color @Composable @ReadOnlyComposable get() = if (WalletIsDark) WalletDarkTokens.Disabled else Color(0xFFA0AEA5)
// v1.0.11 UI polish & v2 pressed/surface tint
val WalletMintTint: Color @Composable @ReadOnlyComposable get() = LocalWalletPalette.current.mintTint

/** Bộ token Dark Mode (Charcoal trung tính phân tầng rõ; giữ xanh thương hiệu cho nút/trạng thái chính). */
object WalletDarkTokens {
    val Bg = Color(0xFF111315)
    val Surface = Color(0xFF1A1D20)
    val Ink = Color(0xFFF3F5F4)
    val Primary = Color(0xFF5CE0B3)
    val Muted = Color(0xFFA0A8B0)
    val Error = Color(0xFFFFA8A0)
    val Warning = Color(0xFFFFD694)
    val Disabled = Color(0xFF707880)
}

private val LightColorScheme = lightColorScheme(
    primary = WalletMint,
    onPrimary = Color.White,
    primaryContainer = LightWalletPalette.mintSoft,
    onPrimaryContainer = WalletMintDark,
    secondary = WalletMintMid,
    onSecondary = Color.White,
    background = LightWalletPalette.bg,
    onBackground = LightWalletPalette.ink,
    surface = LightWalletPalette.cardBg,
    onSurface = LightWalletPalette.ink,
    surfaceVariant = LightWalletPalette.softNeutral,
    onSurfaceVariant = LightWalletPalette.muted,
    surfaceContainer = LightWalletPalette.cardBg,
    surfaceContainerLow = LightWalletPalette.bg,
    surfaceContainerHigh = LightWalletPalette.softNeutral,
    outline = LightWalletPalette.stroke
)

val WalletDarkColorScheme = darkColorScheme(
    primary = WalletDarkTokens.Primary,
    onPrimary = WalletDarkTokens.Bg,
    primaryContainer = Color(0xFF18362B),
    onPrimaryContainer = WalletDarkTokens.Ink,
    secondary = WalletMintBright,
    onSecondary = WalletDarkTokens.Bg,
    background = WalletDarkTokens.Bg,
    onBackground = WalletDarkTokens.Ink,
    surface = WalletDarkTokens.Surface,
    onSurface = WalletDarkTokens.Ink,
    surfaceVariant = Color(0xFF23272B),
    onSurfaceVariant = WalletDarkTokens.Muted,
    surfaceContainer = DarkWalletPalette.cardBg,
    surfaceContainerLow = DarkWalletPalette.bg,
    surfaceContainerHigh = DarkWalletPalette.softNeutral,
    outline = DarkWalletPalette.stroke,
    error = WalletDarkTokens.Error
)

@Composable
fun TimeWalletTheme(
    darkTheme: Boolean = false,
    fontScaleMultiplier: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val baseDensity = LocalDensity.current
    val scaledDensity = Density(
        density = baseDensity.density,
        fontScale = baseDensity.fontScale * fontScaleMultiplier.coerceIn(0.75f, 1.15f)
    )
    val inspection = LocalInspectionMode.current
    val context = LocalContext.current
    val reduceMotion = if (inspection) {
        false
    } else {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
    CompositionLocalProvider(
        LocalWalletPalette provides if (darkTheme) DarkWalletPalette else LightWalletPalette,
        LocalReducedMotion provides reduceMotion,
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) WalletDarkColorScheme else LightColorScheme,
            content = content
        )
    }
}

