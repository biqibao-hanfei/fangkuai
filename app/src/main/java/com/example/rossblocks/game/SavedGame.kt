package com.example.rossblocks.game

import kotlinx.serialization.Serializable

@Serializable
data class SavedPiece(val shapeIndex: Int, val colorIndex: Int)

@Serializable
data class SavedGame(
    val grid: List<Int>,
    val score: Int,
    val pieces: List<SavedPiece>,
    val hammerMode: Boolean,
    val paused: Boolean = false
) {
    companion object {
        const val GRID_SIZE = 10
        const val CELL_COUNT = GRID_SIZE * GRID_SIZE

        fun emptyGrid(): List<Int> = List(CELL_COUNT) { -1 }
    }
}
