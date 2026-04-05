package com.example.rossblocks.game

import androidx.compose.ui.graphics.Color

object GameShapes {

    val palette: List<Color> = listOf(
        Color(0xFF38BDF8),
        Color(0xFFF472B6),
        Color(0xFFFBBF24),
        Color(0xFFA78BFA),
        Color(0xFF34D399),
        Color(0xFFFB7185),
        Color(0xFF2DD4BF),
        Color(0xFFFACC15)
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
