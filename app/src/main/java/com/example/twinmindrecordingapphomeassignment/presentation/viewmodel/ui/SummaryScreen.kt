package com.example.twinmindrecordingapphomeassignment.presentation.viewmodel.ui


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.twinmindhomeassignmentrecordingapp.domain.model.MeetingSummary
import com.example.twinmindhomeassignmentrecordingapp.domain.model.Recording
import com.example.twinmindhomeassignmentrecordingapp.domain.model.SummaryState
import com.example.twinmindrecordingapphomeassignment.presentation.viewmodel.SummaryViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    recordingId: String,
    onNavigateBack: () -> Unit,
    viewModel: SummaryViewModel = hiltViewModel()
) {
    val summaryState by viewModel.summaryState.collectAsState()
    val recording by viewModel.recording.collectAsState()

    LaunchedEffect(recordingId) {
        viewModel.loadRecording(recordingId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meeting Summary") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = summaryState) {
                is SummaryState.Idle -> {
                  //  LoadingView("Waiting for transcription...")
                    if (recording?.transcript?.isNotBlank() == true) {
                        LoadingView("Preparing summary...")
                    } else {
                        LoadingView("Waiting for transcription...")
                    }
                }
                is SummaryState.Loading -> {
                    LoadingView("Generating summary...")
                }
                is SummaryState.Streaming -> {
                    SummaryContent(
                        summary = state.partialSummary,
                        recording = recording!!,
                        isStreaming = true
                    )
                }
                is SummaryState.Completed -> {
                    SummaryContent(
                        summary = state.summary,
                        recording = recording!!,
                        isStreaming = false
                    )
                }
                is SummaryState.Error -> {

                     /*   ErrorView(
                            message = state.message,
                            onRetry = { viewModel.retrySummary() }
                        )*/

                }

                is SummaryState.Processing -> {
                    LoadingView("Processing transcription...")
                }
            }
        }
    }
}

@Composable
fun LoadingView(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Error",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
fun SummaryContent(
    summary: MeetingSummary,
    recording:Recording,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Title Section
        item {
            SummarySection(
                icon = Icons.Default.Title,
                title = "Title",
                isStreaming = isStreaming && summary.title.isNotEmpty()
            ) {
                if (summary.title.isNotEmpty()) {
                    Text(
                        text = summary.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Summary Section
        item {
            SummarySection(
                icon = Icons.Default.Description,
                title = "Summary",
                isStreaming = isStreaming && summary.summary.isNotEmpty()
            ) {
                if (summary.summary.isNotEmpty()) {
                    Text(
                        text = summary.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                    )
                }
            }
        }

        // Action Items Section
        if (summary.actionItems.isNotEmpty()) {
            item {
                SummarySection(
                    icon = Icons.Default.TaskAlt,
                    title = "Action Items",
                    isStreaming = isStreaming
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        summary.actionItems.forEach { item ->
                            ActionItemCard(item)
                        }
                    }
                }
            }
        }

        // Key Points Section
        if (summary.keyPoints.isNotEmpty()) {
            item {
                SummarySection(
                    icon = Icons.Default.Lock,
                    title = "Key Points",
                    isStreaming = isStreaming
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        summary.keyPoints.forEach { point ->
                            KeyPointCard(point)
                        }
                    }
                }
            }
        }

        // Full Transcript Section
        item {


            SummarySection(
                icon = Icons.Default.Description,
                title = "Full Transcript",
                isStreaming = false
            ) {
                Text(
                    text = recording?.transcript?.ifBlank {
                        "No transcript available. Transcription may still be processing."
                    } ?: "Transcript not ready",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                )
            }
        }

        // Streaming indicator
        if (isStreaming) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Generating...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun SummarySection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (isStreaming) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            content()
        }
    }
}

@Composable
fun ActionItemCard(item: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun KeyPointCard(point: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        MaterialTheme.colorScheme.tertiary,
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = point,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}