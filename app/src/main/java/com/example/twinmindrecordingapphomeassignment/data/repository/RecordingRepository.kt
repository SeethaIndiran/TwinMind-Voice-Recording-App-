package com.example.twinmindrecordingapphomeassignment.data.repository


import android.util.Log
import com.example.twinmindhomeassignmentrecordingapp.domain.model.AudioChunk
import com.example.twinmindhomeassignmentrecordingapp.domain.model.MeetingSummary
import com.example.twinmindhomeassignmentrecordingapp.domain.model.Recording
import com.example.twinmindhomeassignmentrecordingapp.domain.model.RecordingStatus
import com.example.twinmindrecordingapphomeassignment.data.local.dao.AudioChunkDao
import com.example.twinmindrecordingapphomeassignment.data.local.dao.RecordingDao
import com.example.twinmindrecordingapphomeassignment.data.local.dao.RecordingSessionDao
import com.example.twinmindrecordingapphomeassignment.data.local.entities.AudioChunkEntity
import com.example.twinmindrecordingapphomeassignment.data.local.entities.RecordingEntity
import com.example.twinmindrecordingapphomeassignment.data.local.entities.RecordingSessionEntity
import com.example.twinmindrecordingapphomeassignment.data.remote.api.ChatGptService
import com.example.twinmindrecordingapphomeassignment.data.remote.api.WhisperApiService
import com.example.twinmindrecordingapphomeassignment.data.remote.dto.SummaryResponse
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepository @Inject constructor(
    private val recordingDao: RecordingDao,
    private val audioChunkDao: AudioChunkDao,
    private val sessionDao: RecordingSessionDao,
    private val chatGptService: ChatGptService,
    private val transcriptionService: WhisperApiService,
    private val gson: Gson
) {

    // Recording operations
    fun getAllRecordings(): Flow<List<Recording>> {
        return recordingDao.getAllRecordings().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getRecordingById(id: String): Recording? {
        return recordingDao.getRecordingById(id)?.toDomain()
    }

    fun observeRecordingById(id: String): Flow<Recording?> {
        return recordingDao.observeRecordingById(id).map { it?.toDomain() }
    }

    suspend fun insertRecording(recording: Recording) {
        recordingDao.insertRecording(recording.toEntity())
    }

    suspend fun updateRecordingStatus(id: String, status: RecordingStatus) {
        recordingDao.updateRecordingStatus(id, status)
    }

    suspend fun updateRecordingDuration(id: String, duration: Long) {
        val recording = recordingDao.getRecordingById(id)
        recording?.let {
            recordingDao.updateRecording(it.copy(duration = duration))
        }
    }

    // Chunk operations
    suspend fun insertChunk(chunk: AudioChunk) {
        audioChunkDao.insertChunk(chunk.toEntity())
    }

    suspend fun getChunksByRecordingId(recordingId: String): List<AudioChunk> {
        return audioChunkDao.getChunksByRecordingId(recordingId).map { it.toDomain() }
    }

    fun observeChunksByRecordingId(recordingId: String): Flow<List<AudioChunk>> {
        return audioChunkDao.observeChunksByRecordingId(recordingId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    // Session operations
    suspend fun saveSession(
        recordingId: String,
        startTime: Long,
        currentDuration: Long,
        isPaused: Boolean,
        pauseReason: String?
    ) {
        sessionDao.saveSession(
            RecordingSessionEntity(
                recordingId = recordingId,
                startTime = startTime,
                currentDuration = currentDuration,
                isPaused = isPaused,
                pauseReason = pauseReason
            )
        )
    }

    suspend fun getSession(): RecordingSessionEntity? {
        return sessionDao.getSession()
    }

    suspend fun clearSession() {
        sessionDao.clearSession()
    }

    fun observeSession(): Flow<RecordingSessionEntity?> {
        return sessionDao.observeSession()
    }

    // Transcription operations
    suspend fun transcribeChunk(chunkId: String): Result<String> {
        return try {
            val chunk = audioChunkDao.getChunkById(chunkId)
                ?: return Result.failure(Exception("Chunk not found"))

            val audioFile = File(chunk.filePath)
            val transcript = try {
                if (audioFile.exists()) {
                    transcriptionService.transcribe(audioFile)
                } else {
                    throw Exception("Audio file not found at ${chunk.filePath}")
                }
            } catch (e: Exception) {
                Log.e("RecordingRepository", "REAL API Transcription failed. Using fallback mock. Error: ${e.message}", e)
                getMockTranscript(chunk.sequence)
            }
            
            audioChunkDao.updateChunkTranscript(chunkId, true, transcript)
            Result.success(transcript)
        } catch (e: Exception) {
            Log.e("RecordingRepository", "Transcription error for chunk $chunkId", e)
            Result.failure(e)
        }
    }

    private fun getMockTranscript(sequence: Int): String {
        return "This is a fallback transcript for chunk $sequence. The real API failed, likely due to a missing or invalid OpenAI API key."
    }

    suspend fun getFullTranscript(recordingId: String): String {
        val chunks = audioChunkDao.getChunksByRecordingId(recordingId)
        return chunks.sortedBy { it.sequence }
            .mapNotNull { it.transcript }
            .joinToString(" ")
    }

    suspend fun updateRecordingTranscript(recordingId: String, transcript: String) {
        recordingDao.updateRecordingTranscript(recordingId, transcript)
    }

    suspend fun getUntranscribedChunksCount(recordingId: String): Int {
        return audioChunkDao.getUntranscribedChunksCount(recordingId)
    }

    // Summary operations
    suspend fun generateSummary(
        recordingId: String,
        meetingSummary: MeetingSummary
    ): Result<MeetingSummary> {
        return try {
            recordingDao.updateRecordingSummary(
                id = recordingId,
                title = meetingSummary.title,
                summary = meetingSummary.summary,
                actionItems = gson.toJson(meetingSummary.actionItems),
                keyPoints = gson.toJson(meetingSummary.keyPoints),
                status = RecordingStatus.COMPLETED
            )
            Result.success(meetingSummary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateSummaryStreaming(
        recordingId: String,
        transcript: String,
        onUpdate: suspend (MeetingSummary) -> Unit
    ) {
        try {
            chatGptService.generateSummary(transcript) { summary ->
                onUpdate(summary)

                // Save summary to database
                recordingDao.updateRecordingSummary(
                    id = recordingId,
                    title = summary.title,
                    summary = summary.summary,
                    actionItems = gson.toJson(summary.actionItems),
                    keyPoints = gson.toJson(summary.keyPoints),
                    status = RecordingStatus.COMPLETED
                )
            }
        } catch (e: Exception) {
            Log.e("RecordingRepository", "REAL API Summary generation failed. Using fallback mock. Error: ${e.message}", e)
            val mockSummary = MeetingSummary(
                title = "API Error: Summary Fallback",
                summary = "The real OpenAI API call failed. Please check your API key in secret.properties. Error: ${e.message}",
                actionItems = listOf("Verify API Key", "Check Internet Connection"),
                keyPoints = listOf("Fallback mode active")
            )
            delay(1000)
            onUpdate(mockSummary)
            generateSummary(recordingId, mockSummary)
        }
    }
}

// Extension functions for mapping
private fun RecordingEntity.toDomain(): Recording {
    val gson = Gson()
    val summary = if (summaryTitle != null && summaryText != null) {
        MeetingSummary(
            title = summaryTitle,
            summary = summaryText,
            actionItems = try {
                gson.fromJson(actionItems, Array<String>::class.java).toList()
            } catch (e: Exception) {
                emptyList()
            },
            keyPoints = try {
                gson.fromJson(keyPoints, Array<String>::class.java).toList()
            } catch (e: Exception) {
                emptyList()
            }
        )
    } else null

    return Recording(
        id = id,
        title = title,
        timestamp = timestamp,
        duration = duration,
        status = status,
        transcript = transcript,
        summary = summary
    )
}

private fun Recording.toEntity(): RecordingEntity {
    val gson = Gson()
    return RecordingEntity(
        id = id,
        title = title,
        timestamp = timestamp,
        duration = duration,
        status = status,
        transcript = transcript,
        summaryTitle = summary?.title,
        summaryText = summary?.summary,
        actionItems = summary?.actionItems?.let { gson.toJson(it) },
        keyPoints = summary?.keyPoints?.let { gson.toJson(it) }
    )
}

private fun AudioChunkEntity.toDomain(): AudioChunk {
    return AudioChunk(
        id = id,
        recordingId = recordingId,
        filePath = filePath,
        sequence = sequence,
        duration = duration,
        transcribed = transcribed,
        transcript = transcript
    )
}

private fun AudioChunk.toEntity(): AudioChunkEntity {
    return AudioChunkEntity(
        id = id,
        recordingId = recordingId,
        filePath = filePath,
        sequence = sequence,
        duration = duration,
        transcribed = transcribed,
        transcript = transcript
    )
}

private fun SummaryResponse.toMeetingSummary(): MeetingSummary {
    return MeetingSummary(
        title = title,
        summary = summary,
        actionItems = actionItems,
        keyPoints = keyPoints
    )
}
