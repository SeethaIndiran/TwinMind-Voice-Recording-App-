package com.example.twinmindrecordingapphomeassignment


import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.twinmindrecordingapphomeassignment.presentation.viewmodel.ui.DashboardScreen
import com.example.twinmindrecordingapphomeassignment.presentation.viewmodel.ui.RecordingScreen
import com.example.twinmindrecordingapphomeassignment.presentation.viewmodel.ui.SummaryScreen
import com.example.twinmindrecordingapphomeassignment.ui.theme.TwinMindRecordingAppHomeAssignmentTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            // Handle permission denied
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()

        setContent {
            TwinMindRecordingAppHomeAssignmentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VoiceRecorderApp()
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@Composable
fun VoiceRecorderApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {
        composable("dashboard") {
            DashboardScreen(
                onNavigateToRecording = {
                    navController.navigate("recording")
                },
                onNavigateToSummary = { recordingId ->
                    navController.navigate("summary/$recordingId")
                }
            )
        }

        composable("recording") {
            RecordingScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSummary = { recordingId ->
                    navController.navigate("summary/$recordingId") {
                        popUpTo("dashboard")
                    }
                }
            )
        }

        composable(
            route = "summary/{recordingId}",
            arguments = listOf(
                navArgument("recordingId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val recordingId = backStackEntry.arguments?.getString("recordingId") ?: return@composable
            SummaryScreen(
                recordingId = recordingId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}