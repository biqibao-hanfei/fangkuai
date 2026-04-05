package com.example.rossblocks.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    /** 当前优先尝试放置的槽位（点击棋盘时从此槽开始轮询） */
    val selectedTrayIndex: Int = 0,
    /** 消除动画：正在高亮的格子 */
    val pulseClearCell: Pair<Int, Int>? = null
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = GameRepository(application)
    private val sound = GameSoundPlayer()

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
            showStuckDialog = false,
            selectedTrayIndex = 0,
            pulseClearCell = null
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
            paused = paused,
            selectedTrayIndex = 0,
            pulseClearCell = null
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

    fun selectTraySlot(index: Int) {
        if (index in 0 until 4) {
            uiState = uiState.copy(selectedTrayIndex = index)
        }
    }

    fun stuckChooseHammer() {
        uiState = uiState.copy(showStuckDialog = false, hammerMode = true, paused = false)
        persistSoon()
    }

    fun stuckRestart() {
        viewModelScope.launch {
            restartGameInternal(closeStuckDialog = true)
        }
    }

    /** 暂停面板内「重新开始」，无二次确认 */
    fun restartFromPause() {
        viewModelScope.launch {
            restartGameInternal(closeStuckDialog = false)
            uiState = uiState.copy(paused = false)
        }
    }

    private suspend fun restartGameInternal(closeStuckDialog: Boolean) {
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
            showStuckDialog = if (closeStuckDialog) false else uiState.showStuckDialog,
            paused = if (closeStuckDialog) false else uiState.paused,
            selectedTrayIndex = 0,
            pulseClearCell = null
        )
        persistNow()
        evaluateStuck()
    }

    fun tapCell(row: Int, col: Int) {
        val s = uiState
        if (s.paused && !s.showExitConfirm) return
        if (s.showExitConfirm) return
        if (s.hammerMode) {
            hammerAt(row, col)
            return
        }
        val order = buildSlotTryOrder(s.selectedTrayIndex, s.pieces.size)
        for (slot in order) {
            if (tryPlaceAt(slot, row, col)) return
        }
    }

    /** 拖拽松手时只尝试放置指定槽位 */
    fun canPlacePreview(slot: Int, row: Int, col: Int): Boolean {
        val piece = uiState.pieces.getOrNull(slot) ?: return false
        val shape = GameShapes.shapes.getOrNull(piece.shapeIndex) ?: return false
        return canPlace(shape, row, col, uiState.grid)
    }

    fun tryPlaceFromDrag(slot: Int, row: Int, col: Int): Boolean {
        val s = uiState
        if (s.paused && !s.showExitConfirm) return false
        if (s.showExitConfirm || s.showStuckDialog) return false
        if (s.hammerMode) return false
        if (slot !in 0 until 4) return false
        uiState = uiState.copy(selectedTrayIndex = slot)
        return tryPlaceAt(slot, row, col)
    }

    private fun buildSlotTryOrder(preferred: Int, size: Int): List<Int> {
        if (size <= 0) return emptyList()
        val p = preferred.coerceIn(0, size - 1)
        val rest = (0 until size).filter { it != p }
        return listOf(p) + rest
    }

    private fun hammerAt(row: Int, col: Int) {
        val idx = row * SavedGame.GRID_SIZE + col
        val grid = uiState.grid.toMutableList()
        if (idx !in grid.indices) return
        if (grid[idx] < 0) return
        grid[idx] = -1
        sound.playHammer()
        uiState = uiState.copy(grid = grid.toList(), hammerMode = false)
        persistSoon()
        evaluateStuck()
    }

    private fun tryPlaceAt(slot: Int, row: Int, col: Int): Boolean {
        val pieces = uiState.pieces
        if (slot !in pieces.indices) return false
        val piece = pieces[slot]
        val shape = GameShapes.shapes.getOrNull(piece.shapeIndex) ?: return false
        if (!canPlace(shape, row, col, uiState.grid)) return false

        val grid = uiState.grid.toMutableList()
        for ((dr, dc) in shape) {
            val r = row + dr
            val c = col + dc
            val i = r * SavedGame.GRID_SIZE + c
            grid[i] = piece.colorIndex
        }

        val placedCells = shape.size
        val baseScore = uiState.score + placedCells * 10
        val newPieces = pieces.toMutableList()
        newPieces[slot] = UiPiece(GameShapes.randomShapeIndex(), GameShapes.randomColorIndex())

        uiState = uiState.copy(
            grid = grid.toList(),
            pieces = newPieces,
            score = baseScore,
            pulseClearCell = null
        )
        sound.playPlace()

        viewModelScope.launch {
            val snapshotRows = findFullRows(grid)
            val snapshotCols = findFullCols(grid)
            if (snapshotRows.isEmpty() && snapshotCols.isEmpty()) {
                repo.maybeUpdateHighScore(baseScore)
                val loadedHigh = repo.getHighScore()
                uiState = uiState.copy(
                    highScore = maxOf(uiState.highScore, baseScore, loadedHigh)
                )
                persistSoon()
                evaluateStuck()
                return@launch
            }

            val order = buildClearOrder(snapshotRows, snapshotCols)
            var step = 0
            for (chunk in order.chunked(10)) {
                delay(2)
                for ((r, c) in chunk) {
                    sound.playClearStep(step++)
                    uiState = uiState.copy(pulseClearCell = r to c)
                    val i = r * SavedGame.GRID_SIZE + c
                    if (i in grid.indices) grid[i] = -1
                    uiState = uiState.copy(grid = grid.toList())
                }
            }

            sound.playClearFinish()
            val clearedLines = snapshotRows.size + snapshotCols.size
            val finalScore = baseScore + clearedLines * 100
            repo.maybeUpdateHighScore(finalScore)
            val loadedHigh = repo.getHighScore()
            uiState = uiState.copy(
                grid = grid.toList(),
                score = finalScore,
                highScore = maxOf(uiState.highScore, finalScore, loadedHigh),
                pulseClearCell = null
            )
            persistSoon()
            evaluateStuck()
        }
        return true
    }

    private fun findFullRows(grid: List<Int>): Set<Int> {
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
        return fullRows
    }

    private fun findFullCols(grid: List<Int>): Set<Int> {
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
        return fullCols
    }

    private fun buildClearOrder(rows: Set<Int>, cols: Set<Int>): List<Pair<Int, Int>> {
        val set = mutableSetOf<Pair<Int, Int>>()
        for (r in rows) {
            for (c in 0 until SavedGame.GRID_SIZE) set.add(r to c)
        }
        for (c in cols) {
            for (r in 0 until SavedGame.GRID_SIZE) set.add(r to c)
        }
        val ordered = mutableListOf<Pair<Int, Int>>()
        for (r in rows.sorted()) {
            for (c in 0 until SavedGame.GRID_SIZE) {
                val p = r to c
                if (p in set) ordered.add(p)
            }
        }
        for (c in cols.sorted()) {
            for (r in 0 until SavedGame.GRID_SIZE) {
                val p = r to c
                if (p in set && p !in ordered) ordered.add(p)
            }
        }
        return ordered
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
        if (canPlaceAny(uiState.grid, uiState.pieces)) {
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

    private fun canPlaceAny(grid: List<Int>, pieces: List<UiPiece>): Boolean {
        for (slot in pieces.indices) {
            val shape = GameShapes.shapes.getOrNull(pieces[slot].shapeIndex) ?: continue
            for (r in 0 until SavedGame.GRID_SIZE) {
                for (c in 0 until SavedGame.GRID_SIZE) {
                    if (canPlace(shape, r, c, grid)) return true
                }
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
        super.onCleared()
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
