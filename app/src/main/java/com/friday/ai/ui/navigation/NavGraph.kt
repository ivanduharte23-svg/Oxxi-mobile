package com.friday.ai.ui.navigation

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.friday.ai.MainActivity
import com.friday.ai.core.FileAnalyzer
import com.friday.ai.service.capture.ScreenCapture
import com.friday.ai.ui.chat.ChatViewModel
import com.friday.ai.ui.diagnostics.DiagnosticsScreen
import com.friday.ai.ui.modes.ModesScreen
import com.friday.ai.ui.notebook.NotebookScreen
import com.friday.ai.ui.oxxi.OxxiMainScreen
import com.friday.ai.ui.settings.SettingsScreen
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun FridayNavGraph(activity: MainActivity) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "chat") {
        composable("chat") {
            val viewModel: ChatViewModel = koinViewModel()
            val screenCapture: ScreenCapture = koinInject()
            val fileAnalyzer: FileAnalyzer = koinInject()
            val screenCaptureLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result -> viewModel.onScreenConsent(result.resultCode, result.data) }
            val filePickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let(viewModel::onFilePicked)
            }

            LaunchedEffect(Unit) {
                activity.onWakeWordActivated = { viewModel.onMicClick() }
                viewModel.onAnalyzeScreenRequested = {
                    screenCaptureLauncher.launch(screenCapture.consentIntent(activity))
                }
                viewModel.onAnalyzeFileRequested = {
                    filePickerLauncher.launch(fileAnalyzer.getFilePickerIntent())
                }
            }

            OxxiMainScreen(
                onNavigateToSettings = { navController.navigate("settings") },
                viewModel = viewModel
            )
        }
        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenDashboard = { navController.navigate("lazuri_dashboard") },
                onOpenDiagnostics = { navController.navigate("diagnostics") },
                onOpenModes = { navController.navigate("modes") }
            )
        }
        composable("notebook") {
            NotebookScreen(
                onNavigateBack = { navController.popBackStack() },
                onAsk = { navController.popBackStack() }
            )
        }
        composable("modes") {
            ModesScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable("diagnostics") {
            DiagnosticsScreen(
                viewModel = koinViewModel(),
                onNavigateBack = { navController.popBackStack() },
                onOpenSettings = {
                    if (!navController.popBackStack("settings", inclusive = false)) {
                        navController.navigate("settings")
                    }
                }
            )
        }
        composable("lazuri_dashboard") {
            com.friday.ai.ui.dashboard.LazuriDashboardScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
