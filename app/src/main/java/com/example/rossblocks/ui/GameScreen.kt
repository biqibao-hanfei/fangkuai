package com.example.rossblocks.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.rossblocks.game.GameShapes
import com.example.rossblocks.game.GameViewModel
import com.example.rossblocks.game.SavedGame
import com.example.rossblocks.game.UiPiece
import kotlin.math.floor

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onLeaveAfterSave: () -> Unit
) {
    val state = viewModel.uiState
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onStop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler { viewModel.requestExitConfirm() }

    var boardLc by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0EA5E9), Color(0xFF0B1B3A), Color(0xFF020617))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            TopBar(viewModel, state)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "分数 ${state.score}",
                color = Color(0xFFFBBF24),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                text = "最高分数：${state.highScore}",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(8.dp))
            HammerRow(viewModel, state.hammerMode)
            Spacer(Modifier.height(10.dp))
            BoardArea(viewModel, state) { boardLc = it }
            Spacer(Modifier.height(12.dp))
            TrayArea(viewModel, state, boardLc)
        }

        if (state.paused && !state.showStuckDialog && !state.showExitConfirm) {
            PauseOverlay(viewModel)
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
private fun TopBar(viewModel: GameViewModel, state: com.example.rossblocks.game.GameUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = { viewModel.togglePause() },
            enabled = !state.showStuckDialog && !state.showExitConfirm,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF7C3AED))
        ) {
            Icon(Icons.Default.Pause, contentDescription = "暂停", tint = Color.White)
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun HammerRow(viewModel: GameViewModel, hammerMode: Boolean) {
    val active = hammerMode
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = { viewModel.toggleHammer() },
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    if (active) Color(0xFFFBBF24) else Color(0xFF334155)
                )
                .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
        ) {
            Icon(
                Icons.Default.Gavel,
                contentDescription = "锤子",
                tint = if (active) Color(0xFF0F172A) else Color.White
            )
        }
    }
    if (active) {
        Text(
            "点击棋盘上的格子消除一格（次数不限）",
            color = Color(0xFFFDE68A),
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
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
                        val flashRow = r in state.flashRows
                        val flashCol = c in state.flashCols
                        val fill = when {
                            flashRow || flashCol -> Color.White.copy(alpha = 0.92f)
                            colorIdx >= 0 -> GameShapes.palette[colorIdx % GameShapes.palette.size]
                            else -> Color(0xFF1E293B).copy(alpha = 0.35f)
                        }
                        Box(
                            modifier = Modifier
                                .size(cell)
                                .border(0.5.dp, Color(0xFF38BDF8).copy(alpha = 0.25f))
                                .background(fill)
                                .pointerInput(state.hammerMode, state.paused, state.showStuckDialog) {
                                    detectTapGestures(
                                        onTap = {
                                            if (state.showStuckDialog || state.showExitConfirm) return@detectTapGestures
                                            if (state.paused && !state.hammerMode) return@detectTapGestures
                                            viewModel.tapCell(r, c)
                                        }
                                    )
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrayArea(
    viewModel: GameViewModel,
    state: com.example.rossblocks.game.GameUiState,
    boardLc: LayoutCoordinates?
) {
    var headLc by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var lastInPiece by remember { mutableStateOf(Offset.Zero) }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "拖动队首图形到棋盘，或点击格子以左上角对齐放置",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            state.pieces.forEachIndexed { index, piece ->
                val isHead = index == 0
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp)
                        .then(
                            if (isHead) {
                                Modifier
                                    .onGloballyPositioned { headLc = it }
                                    .pointerInput(
                                        state.pieces,
                                        state.paused,
                                        state.hammerMode,
                                        state.showStuckDialog
                                    ) {
                                        if (state.paused || state.hammerMode || state.showStuckDialog) {
                                            return@pointerInput
                                        }
                                        detectDragGestures(
                                            onDragStart = {
                                                dragging = true
                                                drag = Offset.Zero
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                drag += amount
                                                lastInPiece = change.position
                                            },
                                            onDragCancel = {
                                                dragging = false
                                                drag = Offset.Zero
                                            },
                                            onDragEnd = {
                                                dragging = false
                                                val blc = boardLc
                                                val hlc = headLc
                                                if (blc != null && hlc != null && blc.isAttached && hlc.isAttached) {
                                                    val windowPos = hlc.localToWindow(lastInPiece)
                                                    val localBoard = blc.windowToLocal(windowPos)
                                                    val w = blc.size.width.toFloat()
                                                    val h = blc.size.height.toFloat()
                                                    val cw = w / SavedGame.GRID_SIZE
                                                    val ch = h / SavedGame.GRID_SIZE
                                                    val col = floor((localBoard.x / cw).toDouble()).toInt()
                                                    val row = floor((localBoard.y / ch).toDouble()).toInt()
                                                    if (row in 0 until SavedGame.GRID_SIZE && col in 0 until SavedGame.GRID_SIZE) {
                                                        viewModel.tapCell(row, col)
                                                    }
                                                }
                                                drag = Offset.Zero
                                            }
                                        )
                                    }
                            } else Modifier
                        )
                ) {
                    MiniPiece(
                        piece = piece,
                        modifier = Modifier
                            .then(
                                if (isHead && dragging) {
                                    Modifier.offset {
                                        IntOffset(drag.x.toInt(), drag.y.toInt())
                                    }
                                } else Modifier
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniPiece(piece: UiPiece, modifier: Modifier = Modifier) {
    val shape = GameShapes.shapes.getOrNull(piece.shapeIndex)
    if (shape == null) {
        Spacer(modifier.size(72.dp))
        return
    }
    val maxR = shape.maxOf { it.first } + 1
    val maxC = shape.maxOf { it.second } + 1
    val color = GameShapes.palette[piece.colorIndex % GameShapes.palette.size]
    Column(
        modifier = modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.4f))
            .padding(6.dp)
    ) {
        for (r in 0 until maxR) {
            Row {
                for (c in 0 until maxC) {
                    val on = shape.contains(r to c)
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(1.dp)
                            .background(
                                if (on) color else Color.Transparent,
                                RoundedCornerShape(3.dp)
                            )
                            .border(
                                0.5.dp,
                                if (on) Color.White.copy(alpha = 0.35f) else Color.Transparent,
                                RoundedCornerShape(3.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun PauseOverlay(viewModel: GameViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF1E293B)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("已暂停", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.togglePause() }) {
                    Text("继续游戏")
                }
            }
        }
    }
}

@Composable
private fun StuckDialog(viewModel: GameViewModel) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("无法放置队首图形") },
        text = {
            Text("可以用锤子消除任意一格（不限次数），或重新开始本局。清空棋盘前若当前分数更高，会更新最高分数。")
        },
        confirmButton = {
            TextButton(onClick = { viewModel.stuckChooseHammer() }) {
                Text("使用锤子")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.stuckRestart() }) {
                Text("重新开始")
            }
        }
    )
}

@Composable
private fun ExitDialog(viewModel: GameViewModel, onLeave: () -> Unit) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissExitConfirm() },
        title = { Text("退出游戏？") },
        text = { Text("将保存本局进度（含棋盘与四个图形），下次可选择「继续之前」。") },
        confirmButton = {
            TextButton(onClick = { viewModel.confirmExit(onLeave) }) {
                Text("保存并退出")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissExitConfirm() }) {
                Text("取消")
            }
        }
    )
}
