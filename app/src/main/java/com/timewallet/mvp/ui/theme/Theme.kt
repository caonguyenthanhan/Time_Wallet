package com.timewallet.mvp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val WalletInk = Color(0xFF172926)
val WalletMuted = Color(0xFF73827D)
val WalletMint = Color(0xFF1F8066)
val WalletMintDark = Color(0xFF173F35)
val WalletMintMid = Color(0xFF21765C)
val WalletMintBright = Color(0xFF2B9474)
val WalletMintSoft = Color(0xFFDDF3E8)
val WalletBg = Color(0xFFF5F7F3)
val WalletStroke = Color(0xFFE7EBE6)
val WalletCardBg = Color(0xFFFFFFFF)
val WalletUntrackedBar = Color(0xFFE0E6E0)
val WalletMicroBg = Color(0xFFEBF4EC)
val WalletMicroText = Color(0xFF4D7660)
val WalletDangerBg = Color(0xFFFFF3F0)
val WalletDangerBorder = Color(0xFFF5D4CA)
val WalletDangerText = Color(0xFFAD4433)
// v1.0.11 UI polish
val WalletMintTint = Color(0xFFE8F5EE)
val WalletRing = Color(0xFFE9EFEA)

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
    surfaceVariant = Color(0xFFEDF2EE),
    onSurfaceVariant = WalletMuted,
    outline = WalletStroke
)

@Composable
fun TimeWalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}

