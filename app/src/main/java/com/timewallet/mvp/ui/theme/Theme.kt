package com.timewallet.mvp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light tokens (Style Guide v2 — độ tương phản UI >= 6.4:1 trên nền sáng)
val WalletInk = Color(0xFF172B27)
val WalletMuted = Color(0xFF5A7268)
val WalletMint = Color(0xFF176B57)
val WalletMintDark = Color(0xFF103F35)
val WalletMintMid = Color(0xFF207E68)
val WalletMintBright = Color(0xFF258F76)
val WalletMintSoft = Color(0xFFDCF3E8)
val WalletBg = Color(0xFFF5F7F3)
val WalletStroke = Color(0xFFDDE8E0)
val WalletCardBg = Color(0xFFFFFFFF)
val WalletUntrackedBar = Color(0xFFE0E6E0)
val WalletMicroBg = Color(0xFFEDF6F0)
val WalletMicroText = Color(0xFF176B57)
val WalletDangerBg = Color(0xFFFFF0EF)
val WalletDangerBorder = Color(0xFFF5D4CA)
val WalletDangerText = Color(0xFFB63F37)
val WalletWarningBg = Color(0xFFFFF3DF)
val WalletWarningText = Color(0xFFA24C18)
val WalletDisabled = Color(0xFFA0AEA5)
// v1.0.11 UI polish & v2 pressed/surface tint
val WalletMintTint = Color(0xFFEDF6F0)
val WalletRing = Color(0xFFE9EFEA)

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
    primaryContainer = WalletMintSoft,
    onPrimaryContainer = WalletMintDark,
    secondary = WalletMintMid,
    onSecondary = Color.White,
    background = WalletBg,
    onBackground = WalletInk,
    surface = WalletCardBg,
    onSurface = WalletInk,
    surfaceVariant = Color(0xFFEDF6F0),
    onSurfaceVariant = WalletMuted,
    outline = WalletStroke
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
    outline = Color(0xFF2B4B40),
    error = WalletDarkTokens.Error
)

@Composable
fun TimeWalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}

