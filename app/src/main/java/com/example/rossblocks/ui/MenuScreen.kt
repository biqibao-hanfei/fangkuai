package com.example.rossblocks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.rossblocks.game.GameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MenuScreen(
    repository: GameRepository,
    onStartNew: () -> Unit,
    onContinue: () -> Unit,
    onExitApp: () -> Unit
) {
    var showStartChoice by remember { mutableStateOf(false) }
    var hasSave by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, repository) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch(Dispatchers.IO) {
                    val h = repository.hasSavedGame()
                    withContext(Dispatchers.Main) { hasSave = h }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0EA5E9), Color(0xFF0B1B3A), Color(0xFF020617))
                )
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "罗斯方块",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                color = Color.White
            )
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = { showStartChoice = true },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF38BDF8),
                contentColor = Color(0xFF0F172A)
            ),
            modifier = Modifier.height(52.dp)
        ) {
            Text("开始游戏", fontSize = 18.sp)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onExitApp,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF334155),
                contentColor = Color.White
            ),
            modifier = Modifier.height(52.dp)
        ) {
            Text("退出游戏", fontSize = 18.sp)
        }
    }

    if (showStartChoice) {
        AlertDialog(
            onDismissRequest = { showStartChoice = false },
            title = { Text("选择开局") },
            text = { Text("开始新局将清除未结束的进度存档（最高分仍会保留）。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStartChoice = false
                        onStartNew()
                    }
                ) { Text("新局") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showStartChoice = false
                        onContinue()
                    },
                    enabled = hasSave
                ) { Text("继续之前") }
            }
        )
    }
}
