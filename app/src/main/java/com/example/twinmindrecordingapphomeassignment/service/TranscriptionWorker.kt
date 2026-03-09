package com.example.twinmindrecordingapphomeassignment.service


import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.twinmindhomeassignmentrecordingapp.domain.model.MeetingSummary
import com.example.twinmindhomeassignmentrecordingapp.domain.model.RecordingStatus
import com.example.twinmindrecordingapphomeassignment.data.remote.api.ChatGptService
import com.example.twinmindrecordingapphomeassignment.data.remote.api.WhisperApiService
import com.example.twinmindrecordingapphomeassignment.data.repository.RecordingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

@HiltWorker
class TranscriptionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: RecordingRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val recordingId = inputData.getString(KEY_RECORDING_ID) ?: return@withContext Result.failure()
        val chunkId = inputData.getString(KEY_CHUNK_ID) ?: return@withContext Result.failure()

        Log.d("TranscriptionWorker", "Starting transcription for chunk: $chunkId, recording: $recordingId")

        try {
            // Transcribe the chunk
            val result = repository.transcribeChunk(chunkId)

            if (result.isSuccess) {
                Log.d("TranscriptionWorker", "Successfully transcribed chunk: $chunkId")
                
                // Check if all chunks are transcribed
                val untranscribedCount = repository.getUntranscribedChunksCount(recordingId)
                Log.d("TranscriptionWorker", "Untranscribed chunks remaining: $untranscribedCount")

                if (untranscribedCount == 0) {
                    Log.d("TranscriptionWorker", "All chunks transcribed. Finalizing recording: $recordingId")
                    
                    // All chunks transcribed - generate full transcript
                    val fullTranscript = repository.getFullTranscript(recordingId)
                    repository.updateRecordingTranscript(recordingId, fullTranscript)
                    repository.updateRecordingStatus(recordingId, RecordingStatus.COMPLETED)

                    Log.d("TranscriptionWorker", "Recording $recordingId status updated to COMPLETED. Enqueueing summary.")
                    // Enqueue summary generation
                    SummaryWorker.enqueueSummaryGeneration(applicationContext, recordingId)
                }

                Result.success()
            } else {
                Log.e("TranscriptionWorker", "Failed to transcribe chunk: $chunkId")
                // Retry on failure
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e("TranscriptionWorker", "Error in TranscriptionWorker", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        private const val KEY_RECORDING_ID = "recording_id"
        private const val KEY_CHUNK_ID = "chunk_id"
        const val WORK_NAME_PREFIX = "transcription_"

        fun enqueueTranscription(context: Context, recordingId: String, chunkId: String) {
            val inputData = Data.Builder()
                .putString(KEY_RECORDING_ID, recordingId)
                .putString(KEY_CHUNK_ID, chunkId)
                .build()

            val request = OneTimeWorkRequestBuilder<TranscriptionWorker>()
                .setInputData(inputData)
                // Removed network constraint for testing/mock purposes
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "$WORK_NAME_PREFIX$chunkId",
                    ExistingWorkPolicy.KEEP,
                    request
                )
        }
    }
}

@HiltWorker
class SummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: RecordingRepository,
    private val chatGptService: ChatGptService
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val recordingId = inputData.getString(KEY_RECORDING_ID) ?: return@withContext Result.failure()

        Log.d("SummaryWorker", "Starting summary generation for recording: $recordingId")

        try {
            val recording = repository.getRecordingById(recordingId)
            val transcript = recording?.transcript

            if (transcript.isNullOrBlank()) {
                Log.e("SummaryWorker", "Transcript is null or blank for recording: $recordingId")
                return@withContext Result.failure()
            }

            // Generate summary
            var finalSummary: MeetingSummary? = null
            repository.generateSummaryStreaming(recordingId, transcript) { summary ->
                finalSummary = summary
            }

            return@withContext if (finalSummary != null) {
                Log.d("SummaryWorker", "Successfully generated summary for recording: $recordingId")
                Result.success()
            } else {
                Log.e("SummaryWorker", "Failed to generate summary for recording: $recordingId")
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }

        } catch (e: Exception) {
            Log.e("SummaryWorker", "Error in SummaryWorker", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val KEY_RECORDING_ID = "recording_id"
        const val WORK_NAME_PREFIX = "summary_"

        fun enqueueSummaryGeneration(context: Context, recordingId: String) {
            val inputData = Data.Builder()
                .putString(KEY_RECORDING_ID, recordingId)
                .build()

            val request = OneTimeWorkRequestBuilder<SummaryWorker>()
                .setInputData(inputData)
                // Removed network constraint for testing/mock purposes
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "$WORK_NAME_PREFIX$recordingId",
                    ExistingWorkPolicy.KEEP,
                    request
                )
        }
    }
}

@HiltWorker
class RecordingTerminationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: RecordingRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val session = repository.getSession()
            Log.d("RecordingTerminationWorker", "Handling unexpected termination. Session: ${session != null}")

            session?.let {
                val recordingId = it.recordingId ?: return@withContext Result.success()

                repository.updateRecordingStatus(recordingId, RecordingStatus.PROCESSING)
                val chunks = repository.getChunksByRecordingId(recordingId)
                Log.d("RecordingTerminationWorker", "Found ${chunks.size} chunks to process for recording: $recordingId")

                chunks.filter { chunk -> !chunk.transcribed }.forEach { chunk ->
                    TranscriptionWorker.enqueueTranscription(
                        applicationContext,
                        recordingId,
                        chunk.id
                    )
                }

                repository.clearSession()
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("RecordingTerminationWorker", "Error in RecordingTerminationWorker", e)
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "recording_termination"

        fun enqueueTerminationWork(context: Context) {
            val request = OneTimeWorkRequestBuilder<RecordingTerminationWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}

@HiltWorker
class WhisperTranscriptionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val apiService: WhisperApiService
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val recordingId = inputData.getString("recording_id") ?: return Result.failure()
        val chunkId = inputData.getString("chunk_id") ?: return Result.failure()

        return try {
            val audioFile = File(applicationContext.filesDir, "recordings/$recordingId/chunk_${recordingId}_$chunkId.pcm")
            if (!audioFile.exists()) return Result.failure()

            val transcription = apiService.transcribe(audioFile)
            // Implementation for saving transcript would go here
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
