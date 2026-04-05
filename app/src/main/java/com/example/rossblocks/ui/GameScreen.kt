package com.example.rossblocks.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.rossblocks.game.GameShapes
import com.example.rossblocks.game.GameViewModel
import com.example.rossblocks.game.SavedGame
import com.example.rossblocks.game.UiPiece
import kotlin.math.floor

private data class DragOverlay(
    val slot: Int,
    val fingerWindow: Offset
)

/** 棋盘上预览与落点：手指上移一点，避免挡住格心 */
private fun fingerLiftPx(cellHpx: Float): Float = cellHpx * 0.55f + 40f

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onLeaveAfterSave: () -> Unit
) {
    val state = viewModel.uiState
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onStop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler { viewModel.requestExitConfirm() }

    var boardLc by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var rootLc by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var cellWpx by remember { mutableStateOf(0f) }
    var cellHpx by remember { mutableStateOf(0f) }
    var dragOverlay by remember { mutableStateOf<DragOverlay?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootLc = it }
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0EA5E9), Color(0xFF0B1B3A), Color(0xFF020617))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            HeaderRow(viewModel, state)
            Spacer(Modifier.height(6.dp))
            BoardArea(
                viewModel = viewModel,
                state = state,
                onBoardPositioned = { lc ->
                    boardLc = lc
                    val w = lc.size.width.toFloat()
                    val h = lc.size.height.toFloat()
                    cellWpx = w / SavedGame.GRID_SIZE
                    cellHpx = h / SavedGame.GRID_SIZE
                }
            )
            Spacer(Modifier.height(8.dp))
            SlotSelectorRow(viewModel, state)
            Spacer(Modifier.height(6.dp))
            TrayRow(
                viewModel = viewModel,
                state = state,
                boardLc = boardLc,
                cellHpx = cellHpx,
                dragOverlaySetter = { dragOverlay = it },
                dragOverlayGetter = { dragOverlay }
            )
        }

        DragPreviewLayer(
            drag = dragOverlay,
            state = state,
            boardLc = boardLc,
            rootLc = rootLc,
            cellWpx = cellWpx,
            cellHpx = cellHpx,
            viewModel = viewModel,
            density = density
        )

        if (state.paused && !state.showStuckDialog && !state.showExitConfirm) {
            PauseOverlay(
                viewModel = viewModel,
                modifier = Modifier.zIndex(400f)
            )
        }
        if (state.showStuckDialog) {
            StuckDialog(viewModel)
        }
        if (state.showExitConfirm) {
            ExitDialog(viewModel, onLeaveAfterSave)
        }
    }
}

@Composable
private fun HeaderRow(viewModel: GameViewModel, state: com.example.rossblocks.game.GameUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = { viewModel.togglePause() },
            enabled = !state.showStuckDialog && !state.showExitConfirm,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0xFF7C3AED))
        ) {
            Icon(Icons.Default.Pause, contentDescription = "暂停", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "分数 ${state.score}",
                color = Color(0xFFFBBF24),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "最高 ${state.highScore}",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 17.sp
            )
        }

        Column(
            modifier = Modifier.widthIn(max = 148.dp),
            horizontalAlignment = Alignment.End
        ) {
            IconButton(
                onClick = { viewModel.toggleHammer() },
                enabled = !state.showStuckDialog && !state.showExitConfirm,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.End)
                    .clip(CircleShape)
                    .background(
                        if (state.hammerMode) Color(0xFFFBBF24) else Color(0xFF334155)
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Gavel,
                    contentDescription = "锤子",
                    tint = if (state.hammerMode) Color(0xFF0F172A) else Color.White
                )
            }
            if (state.hammerMode) {
                Text(
                    "点格消除",
                    color = Color(0xFFFDE68A),
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                "四格均可拖入棋盘；点数字优先该槽。",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SlotSelectorRow(viewModel: GameViewModel, state: com.example.rossblocks.game.GameUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("优先槽位", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        repeat(4) { i ->
            val sel = state.selectedTrayIndex == i
            Text(
                text = "${i + 1}",
                color = if (sel) Color(0xFF0F172A) else Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (sel) Color(0xFF22D3EE) else Color(0xFF1E293B))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { viewModel.selectTraySlot(i) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun BoardArea(
    viewModel: GameViewModel,
    state: com.example.rossblocks.game.GameUiState,
    onBoardPositioned: (LayoutCoordinates) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .onGloballyPositioned { onBoardPositioned(it) }
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, Color(0xFF22D3EE).copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.55f))
    ) {
        val cell = maxWidth / SavedGame.GRID_SIZE
        Column(Modifier.fillMaxSize()) {
            for (r in 0 until SavedGame.GRID_SIZE) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until SavedGame.GRID_SIZE) {
                        val idx = r * SavedGame.GRID_SIZE + c
                        val colorIdx = state.grid[idx]
                        val pulse = state.pulseClearCell == (r to c)
                        Box(
                            modifier = Modifier
                                .size(cell)
                                .border(0.5.dp, Color(0xFF38BDF8).copy(alpha = 0.22f))
                                .pointerInput(state.hammerMode, state.paused, state.showStuckDialog) {
                                    detectTapGestures(
                                        onTap = {
                                            if (state.showStuckDialog || state.showExitConfirm) return@detectTapGestures
                                            if (state.paused && !state.hammerMode) return@detectTapGestures
                                            viewModel.tapCell(r, c)
                                        }
                                    )
                                }
                        ) {
                            when {
                                colorIdx >= 0 -> {
                                    JewelCell(
                                        base = GameShapes.palette[colorIdx % GameShapes.palette.size],
                                        modifier = Modifier.fillMaxSize(),
                                        pulse = pulse
                                    )
                                }

                                else -> {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF1E293B).copy(alpha = 0.35f))
                                    )
                                }
                            }
                            if (pulse) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.White.copy(alpha = 0.35f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrayRow(
    viewModel: GameViewModel,
    state: com.example.rossblocks.game.GameUiState,
    boardLc: LayoutCoordinates?,
    cellHpx: Float,
    dragOverlaySetter: (DragOverlay?) -> Unit,
    dragOverlayGetter: () -> DragOverlay?
) {
    val boardLcState = rememberUpdatedState(boardLc)
    val cellHState = rememberUpdatedState(cellHpx)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        state.pieces.forEachIndexed { index, piece ->
            var slotLc by remember { mutableStateOf<LayoutCoordinates?>(null) }
            val slotLcState = rememberUpdatedState(slotLc)
            val draggingHere = dragOverlayGetter()?.slot == index
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 3.dp)
                    .onGloballyPositioned { slotLc = it }
                    .then(
                        if (state.selectedTrayIndex == index) {
                            Modifier.border(2.dp, Color(0xFF22D3EE), RoundedCornerShape(10.dp))
                        } else Modifier
                    )
                    .pointerInput(
                        index,
                        state.pieces,
                        state.paused,
                        state.hammerMode,
                        state.showStuckDialog,
                        state.showExitConfirm
                    ) {
                        if (state.paused || state.hammerMode || state.showStuckDialog || state.showExitConfirm) {
                            return@pointerInput
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val pointerId = down.id
                            viewModel.selectTraySlot(index)
                            val lcStart = slotLcState.value
                            if (lcStart == null || !lcStart.isAttached) return@awaitEachGesture
                            dragOverlaySetter(
                                DragOverlay(
                                    slot = index,
                                    fingerWindow = lcStart.localToWindow(down.position)
                                )
                            )
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.find { it.id == pointerId } ?: continue
                                if (change.changedToUp()) {
                                    val cur = dragOverlayGetter()
                                    val blc = boardLcState.value
                                    if (cur != null && blc != null && blc.isAttached) {
                                        val local = blc.windowToLocal(cur.fingerWindow)
                                        val w = blc.size.width.toFloat()
                                        val h = blc.size.height.toFloat()
                                        val cw = w / SavedGame.GRID_SIZE
                                        val ch = h / SavedGame.GRID_SIZE
                                        val cellH = if (cellHState.value > 1f) cellHState.value else ch
                                        val lift = fingerLiftPx(cellH)
                                        if (local.x in 0f..w && local.y in 0f..h) {
                                            val col = floor((local.x / cw).toDouble()).toInt()
                                            val row = floor(((local.y - lift) / ch).coerceAtLeast(0f).toDouble()).toInt()
                                            if (row in 0 until SavedGame.GRID_SIZE && col in 0 until SavedGame.GRID_SIZE) {
                                                viewModel.tryPlaceFromDrag(cur.slot, row, col)
                                            }
                                        }
                                    }
                                    dragOverlaySetter(null)
                                    break
                                }
                                change.consume()
                                val lc = slotLcState.value
                                val cur = dragOverlayGetter()
                                if (lc != null && lc.isAttached && cur != null) {
                                    dragOverlaySetter(
                                        cur.copy(fingerWindow = lc.localToWindow(change.position))
                                    )
                                }
                            }
                        }
                    }
            ) {
                TrayPieceMini(
                    piece = piece,
                    cellDp = 24.dp,
                    modifier = Modifier.alpha(if (draggingHere) 0f else 1f)
                )
            }
        }
    }
}

@Composable
private fun TrayPieceMini(piece: UiPiece, cellDp: Dp, modifier: Modifier = Modifier) {
    val shape = GameShapes.shapes.getOrNull(piece.shapeIndex)
    if (shape == null) {
        Spacer(Modifier.size(80.dp))
        return
    }
    val maxR = shape.maxOf { it.first } + 1
    val maxC = shape.maxOf { it.second } + 1
    val color = GameShapes.palette[piece.colorIndex % GameShapes.palette.size]
    Column(
        modifier = modifier
            .padding(vertical = 4.dp)
    ) {
        for (r in 0 until maxR) {
            Row {
                for (c in 0 until maxC) {
                    val on = shape.contains(r to c)
                    Box(
                        modifier = Modifier
                            .size(cellDp)
                            .padding(1.dp)
                    ) {
                        if (on) {
                            JewelCell(
                                base = color,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DragPreviewLayer(
    drag: DragOverlay?,
    state: com.example.rossblocks.game.GameUiState,
    boardLc: LayoutCoordinates?,
    rootLc: LayoutCoordinates?,
    cellWpx: Float,
    cellHpx: Float,
    viewModel: GameViewModel,
    density: androidx.compose.ui.unit.Density
) {
    Box(Modifier.fillMaxSize().zIndex(320f)) {
        val d = drag ?: return@Box
        val rl = rootLc ?: return@Box
        val bl = boardLc ?: return@Box
        if (!bl.isAttached || !rl.isAttached) return@Box
        if (cellWpx <= 0f || cellHpx <= 0f) return@Box
        val piece = state.pieces.getOrNull(d.slot) ?: return@Box
        val shape = GameShapes.shapes.getOrNull(piece.shapeIndex) ?: return@Box
        val maxR = shape.maxOf { it.first } + 1
        val maxC = shape.maxOf { it.second } + 1

        val localB = bl.windowToLocal(d.fingerWindow)
        val w = bl.size.width.toFloat()
        val h = bl.size.height.toFloat()
        val onBoard = localB.x in 0f..w && localB.y in 0f..h
        val lift = fingerLiftPx(cellHpx)
        val localAdjY = (localB.y - lift).coerceAtLeast(0f)
        val anchorCol = floor((localB.x / cellWpx).toDouble()).toInt()
        val anchorRow = floor((localAdjY / cellHpx).toDouble()).toInt()
        val validOnBoard = onBoard &&
            anchorRow in 0 until SavedGame.GRID_SIZE &&
            anchorCol in 0 until SavedGame.GRID_SIZE &&
            viewModel.canPlacePreview(d.slot, anchorRow, anchorCol)

        val shapeW = maxC * cellWpx
        val shapeH = maxR * cellHpx
        val gapBelowFinger = maxOf(12f, cellHpx * 0.08f)

        val topLeftRoot = if (onBoard) {
            val topLeftBoardPx = Offset(anchorCol * cellWpx, anchorRow * cellHpx)
            val topLeftWin = bl.localToWindow(topLeftBoardPx)
            rl.windowToLocal(topLeftWin)
        } else {
            val fingerRoot = rl.windowToLocal(d.fingerWindow)
            Offset(
                fingerRoot.x - shapeW / 2f,
                fingerRoot.y - shapeH - gapBelowFinger
            )
        }

        val widthDp = with(density) { shapeW.toDp() }
        val heightDp = with(density) { shapeH.toDp() }
        val cellDpW = with(density) { cellWpx.toDp() }
        val cellDpH = with(density) { cellHpx.toDp() }

        val color = GameShapes.palette[piece.colorIndex % GameShapes.palette.size]
        val invalidTint = !validOnBoard || !onBoard

        Column(
            Modifier
                .offset {
                    IntOffset(topLeftRoot.x.toInt(), topLeftRoot.y.toInt())
                }
                .size(widthDp, heightDp)
        ) {
            for (r in 0 until maxR) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until maxC) {
                        val on = shape.contains(r to c)
                        Box(
                            Modifier
                                .size(cellDpW, cellDpH)
                                .padding(1.dp)
                        ) {
                            if (on) {
                                JewelCell(
                                    base = if (invalidTint) color.copy(alpha = 0.55f) else color,
                                    modifier = Modifier.fillMaxSize(),
                                    pulse = false
                                )
                                if (invalidTint) {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .border(1.5.dp, Color(0xFFFB7185), RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PauseOverlay(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF1E293B)) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("已暂停", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                Button(onClick = { viewModel.togglePause() }) {
                    Text("继续游戏", fontSize = 18.sp)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.restartFromPause() },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("重新开始", fontSize = 17.sp, color = Color(0xFFFBBF24))
                }
            }
        }
    }
}

@Composable
private fun StuckDialog(viewModel: GameViewModel) {
    AlertDialog(
        onDismissRequest = { },
        title = {
            Text("没有可放的图形", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "四个图形都无法放入棋盘时可用锤子消除一格（不限次数），或重新开始。清盘前若分数更高会更新最高分。",
                fontSize = 17.sp,
                lineHeight = 24.sp
            )
        },
        confirmButton = {
            TextButton(onClick = { viewModel.stuckChooseHammer() }) {
                Text("使用锤子", fontSize = 17.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.stuckRestart() }) {
                Text("重新开始", fontSize = 17.sp)
            }
        }
    )
}

@Composable
private fun ExitDialog(viewModel: GameViewModel, onLeave: () -> Unit) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissExitConfirm() },
        title = {
            Text("退出游戏？", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "将保存本局进度（含棋盘与四个图形），下次可选择「继续之前」。",
                fontSize = 17.sp,
                lineHeight = 24.sp
            )
        },
        confirmButton = {
            TextButton(onClick = { viewModel.confirmExit(onLeave) }) {
                Text("保存并退出", fontSize = 17.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissExitConfirm() }) {
                Text("取消", fontSize = 17.sp)
            }
        }
    )
}
