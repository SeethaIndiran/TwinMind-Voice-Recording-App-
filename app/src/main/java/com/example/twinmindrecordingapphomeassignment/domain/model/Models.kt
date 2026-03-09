package com.example.twinmindhomeassignmentrecordingapp.domain.model

import java.io.Serializable

// Data Models
data class Recording(
    val id: String,
    val title: String,
    val timestamp: Long,
    val duration: Long,
    val status: RecordingStatus,
    val chunks: List<AudioChunk> = emptyList(),
    val transcript: String? = null,
    val summary: MeetingSummary? = null
) : Serializable

data class AudioChunk(
    val id: String,
    val recordingId: String,
    val filePath: String,
    val sequence: Int,
    val duration: Long,
    val transcribed: Boolean = false,
    val transcript: String? = null
) : Serializable

data class MeetingSummary(
    val title: String,
    val summary: String,
    val actionItems: List<String>,
    val keyPoints: List<String>
) : Serializable

// Enums
enum class RecordingStatus {
    RECORDING,
    PAUSED_PHONE_CALL,
    PAUSED_AUDIO_FOCUS,
    STOPPED,
    PROCESSING,
    COMPLETED,
    ERROR
}

// Sealed Classes - UI States
sealed class RecordingState {
    data object Idle : RecordingState()
    data class Recording(val duration: Long) : RecordingState()
    data class Paused(val reason: String, val duration: Long) : RecordingState()
    data class Stopped(val recordingId: String) : RecordingState()
    data class Error(val message: String) : RecordingState()
    data class Processing(val recordingId: String) : RecordingState()

}

sealed class TranscriptionState {
    data object Idle : TranscriptionState()
    data class InProgress(val progress: Float) : TranscriptionState()
    data class Completed(val transcript: String) : TranscriptionState()
    data class Error(val message: String) : TranscriptionState()
}

sealed class SummaryState {
    data object Idle : SummaryState()
    data object Loading : SummaryState()
    object Processing : SummaryState()
    data class Streaming(val partialSummary: MeetingSummary) : SummaryState()
    data class Completed(val summary: MeetingSummary) : SummaryState()
    data class Error(val message: String) : SummaryState()
}