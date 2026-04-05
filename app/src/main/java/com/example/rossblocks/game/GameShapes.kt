package com.example.rossblocks.game

import androidx.compose.ui.graphics.Color

object GameShapes {

    /** 高饱和、格子上好辨认（参考休闲方块类配色） */
    val palette: List<Color> = listOf(
        Color(0xFFFF1744),
        Color(0xFFFFC400),
        Color(0xFF76FF03),
        Color(0xFF00B0FF),
        Color(0xFFD500F9),
        Color(0xFFFF6D00),
        Color(0xFF00E5FF),
        Color(0xFFFF4081)
    )

    /** Each shape: list of (row, col) relative to bounding-box top-left (min = 0). */
    val shapes: List<List<Pair<Int, Int>>> = listOf(
        listOf(0 to 0),
        listOf(0 to 0, 0 to 1),
        listOf(0 to 0, 1 to 0),
        listOf(0 to 0, 0 to 1, 0 to 2),
        listOf(0 to 0, 1 to 0, 2 to 0),
        listOf(0 to 0, 1 to 0, 1 to 1),
        listOf(0 to 1, 1 to 0, 1 to 1),
        listOf(0 to 0, 0 to 1, 1 to 0),
        listOf(0 to 0, 0 to 1, 1 to 1),
        listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0),
        listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3),
        listOf(
            0 to 0, 0 to 1, 0 to 2,
            1 to 0, 1 to 1, 1 to 2,
            2 to 0, 2 to 1, 2 to 2
        ),
        listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1),
        listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1),
        listOf(0 to 1, 1 to 0, 1 to 1, 2 to 0),
        listOf(0 to 0, 0 to 1, 1 to 1, 1 to 2),
        listOf(0 to 1, 0 to 2, 1 to 0, 1 to 1)
    )

    fun randomShapeIndex(): Int = shapes.indices.random()

    fun randomColorIndex(): Int = palette.indices.random()
}
