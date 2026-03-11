package com.example.twinmindrecordingapphomeassignment.presentation.viewmodel



import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
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
                // Delete logic
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
    private var timerJob: Job? = null
    private var recordingObserverJob: Job? = null

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
                    _recordingState.value = RecordingState.Recording(it.currentDuration ?: 0L)
                    startTimer(it.startTime ?: System.currentTimeMillis())
                }
                currentRecordingId?.let { id -> observeRecordingStatus(id) }
            }
        }

        viewModelScope.launch {
            repository.observeSession().collectLatest { session ->
                if (session == null) return@collectLatest
                
                currentRecordingId = session.recordingId
                if (session.isPaused == true) {
                    _recordingState.value = RecordingState.Paused(
                        session.pauseReason ?: "Unknown",
                        session.currentDuration ?: 0L
                    )
                    _duration.value = session.currentDuration ?: 0L
                    timerJob?.cancel()
                } else if (currentRecordingId != null) {
                    _recordingState.value = RecordingState.Recording(session.currentDuration ?: 0L)
                    if (timerJob?.isActive != true) {
                        startTimer(session.startTime ?: System.currentTimeMillis())
                    }
                }
                currentRecordingId?.let { id -> observeRecordingStatus(id) }
            }
        }
    }

    private fun observeRecordingStatus(id: String) {
        recordingObserverJob?.cancel()
        recordingObserverJob = viewModelScope.launch {
            repository.observeRecordingById(id).collect { recording ->
                if (recording == null) return@collect
                if (recording.status == RecordingStatus.PROCESSING || recording.status == RecordingStatus.COMPLETED) {
                    timerJob?.cancel()
                    _recordingState.value = RecordingState.Stopped(id)
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
        observeRecordingStatus(recordingId)
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
                delay(1000)
                val elapsed = System.currentTimeMillis() - startTime
                _duration.value = elapsed
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        recordingObserverJob?.cancel()
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
        recordingObserverJob?.cancel()

        recordingObserverJob = viewModelScope.launch {
            repository.observeRecordingById(recordingId)
                .collect { recording ->
                    _recording.value = recording
                    if (recording == null) {
                        _summaryState.value = SummaryState.Error("Recording not found.")
                        return@collect
                    }

                    // If we are currently streaming, we don't want DB updates to flicker the UI
                    if (isGeneratingSummary) return@collect

                    when (recording.status) {
                        RecordingStatus.PROCESSING -> {
                            _summaryState.value = SummaryState.Processing
                        }
                        RecordingStatus.COMPLETED -> {
                            if (recording.summary != null) {
                                _summaryState.value = SummaryState.Completed(recording.summary!!)
                            } else if (!recording.transcript.isNullOrBlank()) {
                                if (!isGeneratingSummary) {
                                   isGeneratingSummary = true
                                   generateSummary(recording.id, recording.transcript!!)
                                }
                            } else {
                                // Only show NO_AUDIO if the transcript is truly ready and empty
                                _summaryState.value = SummaryState.Error("NO_AUDIO_DETECTED")
                            }
                        }
                        else -> {
                            _summaryState.value = SummaryState.Idle
                        }
                    }
                }
        }
    }

    fun generateSummary(recordingId: String, transcript: String) {
        viewModelScope.launch {
            // isGeneratingSummary is already true from the caller
            _summaryState.value = SummaryState.Loading

            try {
                var latestSummary: MeetingSummary? = null

                repository.generateSummaryStreaming(recordingId, transcript) { partialSummary ->
                    latestSummary = partialSummary
                    // Use streaming state to show the live typing effect
                    _summaryState.value = SummaryState.Streaming(partialSummary)
                }

                if (latestSummary != null) {
                    delay(300) // Smooth transition to final state
                    _summaryState.value = SummaryState.Completed(latestSummary!!)
                } else {
                    _summaryState.value = SummaryState.Error("No summary generated")
                }

            } catch (e: Exception) {
                if (e.message == "NO_AUDIO_DETECTED") {
                    _summaryState.value = SummaryState.Error("NO_AUDIO_DETECTED")
                } else {
                    _summaryState.value = SummaryState.Error(
                        e.message ?: "Failed to generate summary"
                    )
                }
            } finally {
                isGeneratingSummary = false
            }
        }
    }

    fun retrySummary() {
        val currentRecording = _recording.value
        if (currentRecording != null && !currentRecording.transcript.isNullOrBlank()) {
            isGeneratingSummary = true
            generateSummary(currentRecording.id, currentRecording.transcript)
        }
    }
}
