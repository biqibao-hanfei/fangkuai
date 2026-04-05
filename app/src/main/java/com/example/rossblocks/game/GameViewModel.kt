package com.example.rossblocks.game

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiPiece(val shapeIndex: Int, val colorIndex: Int)

data class GameUiState(
    val grid: List<Int> = SavedGame.emptyGrid(),
    val pieces: List<UiPiece> = emptyList(),
    val score: Int = 0,
    val highScore: Int = 0,
    val hammerMode: Boolean = false,
    val paused: Boolean = false,
    val showStuckDialog: Boolean = false,
    val showExitConfirm: Boolean = false,
    val flashRows: Set<Int> = emptySet(),
    val flashCols: Set<Int> = emptySet()
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = GameRepository(application)
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)

    var uiState by mutableStateOf(GameUiState())
        private set

    private var pendingPersist = false

    fun requestExitConfirm() {
        uiState = uiState.copy(showExitConfirm = true, paused = true)
    }

    fun dismissExitConfirm() {
        uiState = uiState.copy(showExitConfirm = false, paused = false)
    }

    fun confirmExit(onDone: () -> Unit) {
        viewModelScope.launch {
            persistNow()
            withContext(Dispatchers.Main) {
                uiState = uiState.copy(showExitConfirm = false, paused = false)
                onDone()
            }
        }
    }

    fun startSession(newGame: Boolean) {
        viewModelScope.launch {
            val high = repo.getHighScore()
            if (newGame) {
                repo.clearSavedGame()
                uiState = freshState(high)
                persistSoon()
            } else {
                val saved = repo.loadSavedGame()
                if (saved != null && saved.pieces.size == 4) {
                    uiState = saved.toUi(high)
                } else {
                    uiState = freshState(high)
                }
                persistSoon()
            }
            evaluateStuck()
        }
    }

    private fun freshState(high: Int): GameUiState {
        return GameUiState(
            grid = SavedGame.emptyGrid(),
            pieces = List(4) {
                UiPiece(GameShapes.randomShapeIndex(), GameShapes.randomColorIndex())
            },
            score = 0,
            highScore = high,
            hammerMode = false,
            paused = false,
            showStuckDialog = false
        )
    }

    private fun SavedGame.toUi(high: Int): GameUiState {
        val g = if (grid.size == SavedGame.CELL_COUNT) grid else SavedGame.emptyGrid()
        val p = pieces.take(4).map { UiPiece(it.shapeIndex, it.colorIndex) }
        val filled = if (p.size == 4) p else List(4) {
            UiPiece(GameShapes.randomShapeIndex(), GameShapes.randomColorIndex())
        }
        return GameUiState(
            grid = g,
            pieces = filled,
            score = score,
            highScore = maxOf(high, score),
            hammerMode = hammerMode,
            paused = paused
        )
    }

    fun togglePause() {
        if (uiState.showExitConfirm) return
        uiState = uiState.copy(paused = !uiState.paused)
        persistSoon()
    }

    fun toggleHammer() {
        if (uiState.paused && !uiState.showExitConfirm) return
        uiState = uiState.copy(hammerMode = !uiState.hammerMode)
        persistSoon()
    }

    fun dismissStuckDialog() {
        uiState = uiState.copy(showStuckDialog = false)
    }

    fun stuckChooseHammer() {
        uiState = uiState.copy(showStuckDialog = false, hammerMode = true, paused = false)
        persistSoon()
    }

    fun stuckRestart() {
        viewModelScope.launch {
            repo.maybeUpdateHighScore(uiState.score)
            val high = repo.getHighScore()
            uiState = uiState.copy(
                grid = SavedGame.emptyGrid(),
                pieces = List(4) {
                    UiPiece(GameShapes.randomShapeIndex(), GameShapes.randomColorIndex())
                },
                score = 0,
                highScore = high,
                hammerMode = false,
                showStuckDialog = false,
                paused = false
            )
            persistNow()
            evaluateStuck()
        }
    }

    fun tapCell(row: Int, col: Int) {
        val s = uiState
        if (s.paused && !s.showExitConfirm) return
        if (s.showExitConfirm) return
        if (s.hammerMode) {
            hammerAt(row, col)
            return
        }
        tryPlaceAt(row, col)
    }

    private fun hammerAt(row: Int, col: Int) {
        val idx = row * SavedGame.GRID_SIZE + col
        val grid = uiState.grid.toMutableList()
        if (idx !in grid.indices) return
        if (grid[idx] < 0) return
        grid[idx] = -1
        playTone(ToneGenerator.TONE_PROP_ACK)
        uiState = uiState.copy(grid = grid, hammerMode = false)
        persistSoon()
        evaluateStuck()
    }

    private fun tryPlaceAt(row: Int, col: Int) {
        val pieces = uiState.pieces
        if (pieces.isEmpty()) return
        val head = pieces[0]
        val shape = GameShapes.shapes.getOrNull(head.shapeIndex) ?: return
        if (!canPlace(shape, row, col, uiState.grid)) return

        val grid = uiState.grid.toMutableList()
        for ((dr, dc) in shape) {
            val r = row + dr
            val c = col + dc
            val i = r * SavedGame.GRID_SIZE + c
            grid[i] = head.colorIndex
        }

        val placedCells = shape.size
        val baseScore = uiState.score + placedCells * 10
        val shifted = pieces.drop(1) + UiPiece(GameShapes.randomShapeIndex(), GameShapes.randomColorIndex())
        uiState = uiState.copy(grid = grid, pieces = shifted, score = baseScore)
        playTone(ToneGenerator.TONE_PROP_BEEP)

        viewModelScope.launch {
            val (toFlashRows, toFlashCols) = findFullLines(grid)
            if (toFlashRows.isNotEmpty() || toFlashCols.isNotEmpty()) {
                uiState = uiState.copy(flashRows = toFlashRows, flashCols = toFlashCols)
                delay(260)
                clearLines(grid, toFlashRows, toFlashCols)
                val cleared = toFlashRows.size + toFlashCols.size
                val finalScore = baseScore + cleared * 100
                if (cleared > 0) playTone(ToneGenerator.TONE_CDMA_CONFIRM)
                repo.maybeUpdateHighScore(finalScore)
                val loadedHigh = repo.getHighScore()
                uiState = uiState.copy(
                    grid = grid,
                    score = finalScore,
                    highScore = maxOf(uiState.highScore, finalScore, loadedHigh),
                    flashRows = emptySet(),
                    flashCols = emptySet()
                )
            } else {
                repo.maybeUpdateHighScore(baseScore)
                val loadedHigh = repo.getHighScore()
                uiState = uiState.copy(
                    highScore = maxOf(uiState.highScore, baseScore, loadedHigh)
                )
            }
            persistSoon()
            evaluateStuck()
        }
    }

    private fun findFullLines(grid: List<Int>): Pair<Set<Int>, Set<Int>> {
        val fullRows = mutableSetOf<Int>()
        for (r in 0 until SavedGame.GRID_SIZE) {
            var full = true
            for (c in 0 until SavedGame.GRID_SIZE) {
                if (grid[r * SavedGame.GRID_SIZE + c] < 0) {
                    full = false
                    break
                }
            }
            if (full) fullRows.add(r)
        }
        val fullCols = mutableSetOf<Int>()
        for (c in 0 until SavedGame.GRID_SIZE) {
            var full = true
            for (r in 0 until SavedGame.GRID_SIZE) {
                if (grid[r * SavedGame.GRID_SIZE + c] < 0) {
                    full = false
                    break
                }
            }
            if (full) fullCols.add(c)
        }
        return fullRows to fullCols
    }

    private fun clearLines(grid: MutableList<Int>, rows: Set<Int>, cols: Set<Int>) {
        for (r in rows) {
            for (c in 0 until SavedGame.GRID_SIZE) {
                grid[r * SavedGame.GRID_SIZE + c] = -1
            }
        }
        for (c in cols) {
            for (r in 0 until SavedGame.GRID_SIZE) {
                grid[r * SavedGame.GRID_SIZE + c] = -1
            }
        }
    }

    private fun canPlace(
        shape: List<Pair<Int, Int>>,
        anchorRow: Int,
        anchorCol: Int,
        grid: List<Int>
    ): Boolean {
        for ((dr, dc) in shape) {
            val r = anchorRow + dr
            val c = anchorCol + dc
            if (r !in 0 until SavedGame.GRID_SIZE || c !in 0 until SavedGame.GRID_SIZE) return false
            val idx = r * SavedGame.GRID_SIZE + c
            if (grid[idx] >= 0) return false
        }
        return true
    }

    private fun evaluateStuck() {
        if (canPlaceHead(uiState.grid, uiState.pieces)) {
            val wasStuckUi = uiState.showStuckDialog
            uiState = uiState.copy(
                showStuckDialog = false,
                paused = if (wasStuckUi) false else uiState.paused
            )
            return
        }
        uiState = uiState.copy(showStuckDialog = true, paused = true)
        persistSoon()
    }

    /** 传送带：只有队首图形可以放置，卡死仅检测队首是否有合法位置。 */
    private fun canPlaceHead(grid: List<Int>, pieces: List<UiPiece>): Boolean {
        if (pieces.isEmpty()) return true
        val head = pieces[0]
        val shape = GameShapes.shapes.getOrNull(head.shapeIndex) ?: return false
        for (r in 0 until SavedGame.GRID_SIZE) {
            for (c in 0 until SavedGame.GRID_SIZE) {
                if (canPlace(shape, r, c, grid)) return true
            }
        }
        return false
    }

    fun persistSoon() {
        pendingPersist = true
        viewModelScope.launch {
            delay(120)
            if (pendingPersist) {
                pendingPersist = false
                persistNow()
            }
        }
    }

    suspend fun persistNow() {
        val s = uiState
        val saved = SavedGame(
            grid = s.grid,
            score = s.score,
            pieces = s.pieces.map { SavedPiece(it.shapeIndex, it.colorIndex) },
            hammerMode = s.hammerMode,
            paused = s.paused
        )
        repo.saveGame(saved)
    }

    fun onStop() {
        viewModelScope.launch {
            persistNow()
        }
    }

    override fun onCleared() {
        tone.release()
        super.onCleared()
    }

    private fun playTone(toneId: Int) {
        try {
            tone.startTone(toneId, 120)
        } catch (_: Exception) {
        }
    }

    companion object {
        fun createFactory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return GameViewModel(app) as T
                }
            }
    }
}
