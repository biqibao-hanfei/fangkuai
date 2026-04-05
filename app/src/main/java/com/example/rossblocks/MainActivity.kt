package com.example.rossblocks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.rossblocks.game.GameRepository
import com.example.rossblocks.game.GameViewModel
import com.example.rossblocks.ui.GameScreen
import com.example.rossblocks.ui.MenuScreen
import com.example.rossblocks.ui.theme.RossBlocksTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RossBlocksTheme {
                val navController = rememberNavController()
                val context = LocalContext.current
                val repository = remember { GameRepository(context.applicationContext) }

                NavHost(
                    navController = navController,
                    startDestination = "menu"
                ) {
                    composable("menu") {
                        MenuScreen(
                            repository = repository,
                            onStartNew = { navController.navigate("game/new") },
                            onContinue = { navController.navigate("game/continue") },
                            onExitApp = { finish() }
                        )
                    }
                    composable(
                        route = "game/{mode}",
                        arguments = listOf(
                            navArgument("mode") {
                                type = NavType.StringType
                                defaultValue = "new"
                            }
                        )
                    ) { entry ->
                        val mode = entry.arguments?.getString("mode") ?: "new"
                        val vm: GameViewModel = viewModel(
                            factory = GameViewModel.createFactory(context.applicationContext as android.app.Application)
                        )
                        LaunchedEffect(mode) {
                            vm.startSession(newGame = mode == "new")
                        }
                        GameScreen(
                            viewModel = vm,
                            onLeaveAfterSave = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
