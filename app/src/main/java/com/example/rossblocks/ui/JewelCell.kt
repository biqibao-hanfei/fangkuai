package com.example.rossblocks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.unit.dp

@Composable
fun JewelCell(
    base: Color,
    modifier: Modifier = Modifier,
    pulse: Boolean = false
) {
    val density = LocalDensity.current
    val top = colorLerp(base, Color.White, 0.58f)
    val mid = base
    val bottom = colorLerp(base, Color.Black, 0.38f)
    BoxWithConstraints(modifier) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(5.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(top, mid, bottom),
                        start = Offset(0f, 0f),
                        end = Offset(wPx * 1.1f, hPx * 1.1f)
                    )
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = if (pulse) 0.9f else 0.52f),
                    RoundedCornerShape(5.dp)
                )
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (pulse) 0.55f else 0.22f),
                                Color.Transparent
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(wPx * 0.9f, hPx * 0.9f)
                        )
                    )
                    .align(Alignment.TopStart)
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 3.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.Black.copy(alpha = 0.12f))
                    .align(Alignment.BottomEnd)
            )
        }
    }
}
