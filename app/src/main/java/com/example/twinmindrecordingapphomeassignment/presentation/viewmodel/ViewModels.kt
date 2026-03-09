package com.example.twinmindrecordingapphomeassignment.presentation.viewmodel



import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.twinmindhomeassignmentrecordingapp.domain.model.MeetingSummary
import com.example.twinmindhomeassignmentrecordingapp.domain.model.Recording
import com.example.twinmindhomeassignmentrecordingapp.domain.model.RecordingState
import com.example.twinmindhomeassignmentrecordingapp.domain.model.RecordingStatus
import com.example.twinmindhomeassignmentrecordingapp.domain.model.SummaryState
import com.example.twinmindrecordingapphomeassignment.service.RecordingTerminationWorker
import com.example.twinmindrecordingapphomeassignment.data.repository.RecordingRepository
import com.example.twinmindrecordingapphomeassignment.service.RecordingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

// Dashboard ViewModel
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: RecordingRepository,
    application: Application
) : AndroidViewModel(application) {

    val recordings = repository.getAllRecordings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteRecording(recordingId: String) {
        viewModelScope.launch {
            val recording = repository.getRecordingById(recordingId)
            recording?.let {
                // Delete recording files
                // Delete from database will be handled automatically
            }
        }
    }
}

// Recording ViewModel
@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val repository: RecordingRepository,
    application: Application
) : AndroidViewModel(application) {

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private var currentRecordingId: String? = null
    private var timerJob: kotlinx.coroutines.Job? = null

    init {
        // Restore session if exists
        viewModelScope.launch {
            val session = repository.getSession()
            session?.let {
                currentRecordingId = it.recordingId
                if (it.isPaused == true) {
                    _recordingState.value = RecordingState.Paused(
                        it.pauseReason ?: "Unknown",
                        it.currentDuration ?: 0L
                    )
                    _duration.value = it.currentDuration ?: 0L
                } else {
                    // Resume recording
                    _recordingState.value = RecordingState.Recording(it.currentDuration ?: 0L)
                    startTimer(it.startTime ?: System.currentTimeMillis())
                }
            }
        }

        // Observe session changes
        viewModelScope.launch {
            repository.observeSession().collectLatest { session ->
                session?.let {
                    if (it.isPaused == true) {
                        _recordingState.value = RecordingState.Paused(
                            it.pauseReason ?: "Unknown",
                            it.currentDuration ?: 0L
                        )
                        _duration.value = it.currentDuration ?: 0L
                        timerJob?.cancel()
                    } else if (currentRecordingId != null) {
                        _recordingState.value = RecordingState.Recording(it.currentDuration ?: 0L)
                        if (timerJob?.isActive != true) {
                            startTimer(it.startTime ?: System.currentTimeMillis())
                        }
                    }
                }
            }
        }
    }

    fun startRecording() {
        val recordingId = UUID.randomUUID().toString()
        currentRecordingId = recordingId

        val intent = Intent(getApplication(), RecordingService::class.java).apply {
            action = RecordingService.ACTION_START_RECORDING
            putExtra(RecordingService.EXTRA_RECORDING_ID, recordingId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }

        _recordingState.value = RecordingState.Recording(0L)
        startTimer(System.currentTimeMillis())
    }

    fun stopRecording() {
        val intent = Intent(getApplication(), RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP_RECORDING
        }
        getApplication<Application>().startService(intent)

        timerJob?.cancel()
        currentRecordingId?.let {
            _recordingState.value = RecordingState.Stopped(it)
        }
    }

    private fun startTimer(startTime: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                val elapsed = System.currentTimeMillis() - startTime
                _duration.value = elapsed
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        // Enqueue termination worker to handle process death
        RecordingTerminationWorker.enqueueTerminationWork(getApplication())
    }
}

// Summary ViewModel
@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val repository: RecordingRepository,
    application: Application
) : AndroidViewModel(application) {

    private val _summaryState = MutableStateFlow<SummaryState>(SummaryState.Idle)
    val summaryState: StateFlow<SummaryState> = _summaryState.asStateFlow()

    private val _recording = MutableStateFlow<Recording?>(null)
    val recording: StateFlow<Recording?> = _recording.asStateFlow()



    private var isGeneratingSummary = false
    private var recordingObserverJob: Job? = null

    fun loadRecording(recordingId: String) {
        // Cancel previous observer if any
        recordingObserverJob?.cancel()

        recordingObserverJob = viewModelScope.launch {
            repository.observeRecordingById(recordingId)
                .collect { recording ->
                    _recording.value = recording

                    when {
                        // Summary exists - only update if we're not currently streaming
                        recording?.summary != null -> {
                            if (_summaryState.value !is SummaryState.Streaming) {
                                isGeneratingSummary = false
                                _summaryState.value = SummaryState.Completed(recording.summary!!)
                            }
                            // If we ARE streaming, the streaming state will naturally transition to Completed
                        }

                        // Transcript ready, no summary, not generating
                        recording != null &&
                                !recording.transcript.isNullOrBlank() &&
                                recording.status == RecordingStatus.COMPLETED &&
                                !isGeneratingSummary -> {

                            isGeneratingSummary = true
                            generateSummary(recordingId, recording.transcript!!)
                        }

                        // Waiting for transcript
                        recording?.transcript.isNullOrBlank() -> {
                            if (!isGeneratingSummary) {
                                _summaryState.value = SummaryState.Idle
                            }
                        }
                    }
                }
        }
    }


    /* fun generateSummary(recordingId: String, transcript: String) {
         viewModelScope.launch {
             _summaryState.value = SummaryState.Loading
           //  hasSummaryGenerationStarted = false

             try {
                 repository.generateSummaryStreaming(recordingId, transcript) { summary ->
                     _summaryState.value = SummaryState.Streaming(summary)
                 }

                 // Final state will be set by the streaming function
                 val finalRecording = repository.getRecordingById(recordingId)
                 finalRecording?.summary?.let {
                     _summaryState.value = SummaryState.Completed(it)
                 }
             } catch (e: Exception) {
                 _summaryState.value = SummaryState.Error(
                     e.message ?: "Failed to generate summary"
                 )
             }
         }
     }*/

    fun generateSummary(recordingId: String, transcript: String) {
        viewModelScope.launch {
            _summaryState.value = SummaryState.Loading

            try {
                var finalSummary: MeetingSummary? = null

                // Streaming callback updates partial summary
                repository.generateSummaryStreaming(recordingId, transcript) { partialSummary ->
                    finalSummary = partialSummary
                    _summaryState.value = SummaryState.Streaming(partialSummary)
                }

                // After streaming completes, wait a bit for DB save
                if (finalSummary != null) {
                    delay(100) // Small delay to ensure DB write completes
                    _summaryState.value = SummaryState.Completed(finalSummary!!)
                } else {
                    _summaryState.value = SummaryState.Error("No summary generated")
                }

            } catch (e: Exception) {
                isGeneratingSummary = false
                _summaryState.value = SummaryState.Error(
                    e.message ?: "Failed to generate summary"
                )
            }
        }
    }

    fun retrySummary() {
        val currentRecording = _recording.value
        isGeneratingSummary = false
      //  hasSummaryGenerationStarted = false
        if (currentRecording != null && !currentRecording.transcript.isNullOrBlank()) {
            generateSummary(currentRecording.id, currentRecording.transcript)
        }
    }

    override fun onCleared() {
        super.onCleared()
     //   hasSummaryGenerationStarted = false
    }
}