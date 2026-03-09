package com.example.twinmindrecordingapphomeassignment.presentation.viewmodel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.twinmindhomeassignmentrecordingapp.domain.model.RecordingState
import com.example.twinmindrecordingapphomeassignment.presentation.viewmodel.RecordingViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSummary: (String) -> Unit,
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val recordingState by viewModel.recordingState.collectAsState()
    val duration by viewModel.duration.collectAsState()

    LaunchedEffect(recordingState) {
        when (recordingState) {
            is RecordingState.Stopped -> {
                val recordingId = (recordingState as RecordingState.Stopped).recordingId
                onNavigateToSummary(recordingId)
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recording") },
                navigationIcon = {
                    if (recordingState is RecordingState.Idle) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->


                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Status Indicator
                    StatusIndicator(recordingState)

                    Spacer(modifier = Modifier.height(32.dp))

                    // Timer
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = when (recordingState) {
                            is RecordingState.Recording -> MaterialTheme.colorScheme.primary
                            is RecordingState.Paused -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Status Text
                    Text(
                        text = when (recordingState) {
                            is RecordingState.Recording -> "Recording..."
                            is RecordingState.Paused -> (recordingState as RecordingState.Paused).reason
                            is RecordingState.Error -> (recordingState as RecordingState.Error).message
                            else -> "Ready to record"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    // Record/Stop Button
                    when (recordingState) {
                        is RecordingState.Idle -> {
                            RecordButton(
                                onClick = { viewModel.startRecording() }
                            )
                        }

                        is RecordingState.Recording,
                        is RecordingState.Paused -> {
                            StopButton(
                                onClick = { viewModel.stopRecording() }
                            )
                        }

                        else -> {}
                    }
                }
            }
        }


@Composable
fun StatusIndicator(state: RecordingState) {
    val color = when (state) {
        is RecordingState.Recording -> MaterialTheme.colorScheme.primary
        is RecordingState.Paused -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = color
            )
        }
    }
}

@Composable
fun RecordButton(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(80.dp),
        containerColor = MaterialTheme.colorScheme.primary
    ) {
        Icon(
            imageVector = Icons.Default.FiberManualRecord,
            contentDescription = "Start Recording",
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
fun StopButton(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(80.dp),
        containerColor = MaterialTheme.colorScheme.error
    ) {
        Icon(
            imageVector = Icons.Default.Stop,
            contentDescription = "Stop Recording",
            modifier = Modifier.size(40.dp)
        )
    }
}

fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60))

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}