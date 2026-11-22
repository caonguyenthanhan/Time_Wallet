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
import androidx.compose.ui.platform.LocalDensity
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
    ink = Color(0xFF172B27),
    muted = Color(0xFF5A7268),
    bg = Color(0xFFF5F7F3),
    stroke = Color(0xFFDDE8E0),
    cardBg = Color(0xFFFFFFFF),
    untrackedBar = Color(0xFFE0E6E0),
    ring = Color(0xFFE9EFEA),
    mintAccent = Color(0xFF176B57),
    mintSoft = Color(0xFFDCF3E8),
    mintTint = Color(0xFFEDF6F0),
    softNeutral = Color(0xFFF3F7F4),
    divider = Color(0xFFEAF0EC),
    microBg = Color(0xFFEDF6F0),
    microText = Color(0xFF176B57),
    dangerBg = Color(0xFFFFF0EF),
    dangerBorder = Color(0xFFF5D4CA),
    dangerText = Color(0xFFB63F37),
    warningBg = Color(0xFFFFF3DF),
    warningBorder = Color(0xFFF2D6A2),
    warningText = Color(0xFFA24C18),
    infoBg = Color(0xFFEEF4FF),
    infoText = Color(0xFF185FA5),
    clarificationBg = Color(0xFFFFF8E8)
)

private val DarkWalletPalette = WalletPalette(
    isDark = true,
    ink = Color(0xFFF4FAF6),
    muted = Color(0xFFB6D4C4),
    bg = Color(0xFF0F201C),
    stroke = Color(0xFF2B4B40),
    cardBg = Color(0xFF1A3029),
    untrackedBar = Color(0xFF284339),
    ring = Color(0xFF243F35),
    mintAccent = Color(0xFF7CE2BE),
    mintSoft = Color(0xFF1F473A),
    mintTint = Color(0xFF163229),
    softNeutral = Color(0xFF213B32),
    divider = Color(0xFF29473C),
    microBg = Color(0xFF1D3B30),
    microText = Color(0xFF96ECD0),
    dangerBg = Color(0xFF422120),
    dangerBorder = Color(0xFF7D3B38),
    dangerText = Color(0xFFFF9B91),
    warningBg = Color(0xFF3E2D14),
    warningBorder = Color(0xFF7C5724),
    warningText = Color(0xFFFFCF86),
    infoBg = Color(0xFF193152),
    infoText = Color(0xFF93C5FD),
    clarificationBg = Color(0xFF362A17)
)

private val LocalWalletPalette = staticCompositionLocalOf { LightWalletPalette }

// Theme-aware surface & typography tokens (tự thích ứng Light / Dark qua TimeWalletTheme)
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

/** Bộ token Dark Mode (Style Guide v2 — độ tương phản 8.9:1 trên dark surface #1A3029). */
object WalletDarkTokens {
    val Bg = Color(0xFF0F201C)
    val Surface = Color(0xFF1A3029)
    val Ink = Color(0xFFF4FAF6)
    val Primary = Color(0xFF84DFC0)
    val Muted = Color(0xFFB6D4C4)
    val Error = Color(0xFFFF958B)
    val Warning = Color(0xFFFFCD83)
    val Disabled = Color(0xFF769084)
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
    surfaceVariant = Color(0xFFEDF6F0),
    onSurfaceVariant = LightWalletPalette.muted,
    surfaceContainer = LightWalletPalette.cardBg,
    surfaceContainerLow = LightWalletPalette.bg,
    surfaceContainerHigh = Color(0xFFF3F7F4),
    outline = LightWalletPalette.stroke
)

val WalletDarkColorScheme = darkColorScheme(
    primary = WalletDarkTokens.Primary,
    onPrimary = WalletDarkTokens.Bg,
    primaryContainer = Color(0xFF174F43),
    onPrimaryContainer = WalletDarkTokens.Ink,
    secondary = WalletMintBright,
    onSecondary = WalletDarkTokens.Bg,
    background = WalletDarkTokens.Bg,
    onBackground = WalletDarkTokens.Ink,
    surface = WalletDarkTokens.Surface,
    onSurface = WalletDarkTokens.Ink,
    surfaceVariant = Color(0xFF213C33),
    onSurfaceVariant = WalletDarkTokens.Muted,
    surfaceContainer = DarkWalletPalette.cardBg,
    surfaceContainerLow = DarkWalletPalette.bg,
    surfaceContainerHigh = DarkWalletPalette.softNeutral,
    outline = Color(0xFF2B4B40),
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
    CompositionLocalProvider(
        LocalWalletPalette provides if (darkTheme) DarkWalletPalette else LightWalletPalette,
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) WalletDarkColorScheme else LightColorScheme,
            content = content
        )
    }
}

