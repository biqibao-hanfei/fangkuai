package com.example.rossblocks.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF0B1B3A)
private val CyanGlow = Color(0xFF22D3EE)

private val DarkColors = darkColorScheme(
    primary = CyanGlow,
    onPrimary = Navy,
    secondary = Color(0xFFF472B6),
    tertiary = Color(0xFFFBBF24),
    background = Navy,
    surface = Color(0xFF132046),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC)
)

@Composable
fun RossBlocksTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
