package com.example.twinmindrecordingapphomeassignment.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.twinmindhomeassignmentrecordingapp.domain.model.AudioChunk
import com.example.twinmindhomeassignmentrecordingapp.domain.model.Recording
import com.example.twinmindhomeassignmentrecordingapp.domain.model.RecordingStatus
import com.example.twinmindrecordingapphomeassignment.MainActivity
import com.example.twinmindrecordingapphomeassignment.R
import com.example.twinmindrecordingapphomeassignment.data.repository.RecordingRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : Service() {

    @Inject
    lateinit var repository: RecordingRepository

    @Inject
    lateinit var audioFocusManager: AudioFocusManager

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var timerJob: Job? = null
    private var isStopping = false

    private var audioInputStream: InputStream? = null
    private val testAudioFiles = listOf(
        R.raw.audio_file1,
        R.raw.audio_file2
    )
    private var currentAudioResource: Int? = null

    private var currentRecordingId: String? = null
    private var chunkSequence = 0
    private var startTime = 0L
    private var pausedDuration = 0L
    private var pauseStartTime = 0L

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration

    private var isPaused = false
    private var pauseReason: String? = null

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    companion object {
        const val CHANNEL_ID = "RecordingServiceChannel"
        const val NOTIFICATION_ID = 1

        const val ACTION_START_RECORDING = "START_RECORDING"
        const val ACTION_STOP_RECORDING = "STOP_RECORDING"
        const val ACTION_PAUSE_RECORDING = "PAUSE_RECORDING"
        const val ACTION_RESUME_RECORDING = "ACTION_RESUME_RECORDING"

        const val EXTRA_RECORDING_ID = "recording_id"

        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_DURATION_MS = 10000L // 10 seconds
        private const val OVERLAP_MS = 1000L
        private const val SILENCE_THRESHOLD = 500
        private const val SILENCE_DURATION_MS = 10000L
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupAudioFocusListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val recordingId = intent.getStringExtra(EXTRA_RECORDING_ID)
                    ?: UUID.randomUUID().toString()
                startRecording(recordingId)
            }
            ACTION_STOP_RECORDING -> stopRecording()
            ACTION_PAUSE_RECORDING -> pauseRecording("Manual pause")
            ACTION_RESUME_RECORDING -> resumeRecording()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
    

    private fun startRecording(recordingId: String) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("RecordingService", "RECORD_AUDIO permission not granted")
            stopSelf()
            return
        }

        currentRecordingId = recordingId
        chunkSequence = 0
        startTime = System.currentTimeMillis()
        pausedDuration = 0L
        isStopping = false

        if (!hasEnoughStorage()) {
            notifyError("Recording stopped - Low storage")
            stopSelf()
            return
        }

        serviceScope.launch {
            val recording = Recording(
                id = recordingId,
                title = "Recording ${Date().time}",
                timestamp = startTime,
                duration = 0L,
                status = RecordingStatus.RECORDING
            )
            repository.insertRecording(recording)
            repository.saveSession(recordingId, startTime, 0L, false, null)
        }

        startForeground(NOTIFICATION_ID, createNotification("Recording...", 0L))
        audioFocusManager.requestAudioFocus()
        startAudioRecording()
        startTimer()
    }

    private fun startAudioRecording() {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = if (minBufferSize > 0) minBufferSize * 2 else 4096

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("RecordingService", "AudioRecord initialization failed. State: ${audioRecord?.state}")
                audioRecord = null
            } else {
                audioRecord?.startRecording()
                if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    Log.e("RecordingService", "AudioRecord failed to start recording. RecordingState: ${audioRecord?.recordingState}")
                    audioRecord?.release()
                    audioRecord = null
                } else {
                    Log.d("RecordingService", "AudioRecord started successfully")
                }
            }
        } catch (e: Exception) {
            Log.e("RecordingService", "Exception initializing AudioRecord", e)
            audioRecord = null
        }

        recordingJob = serviceScope.launch {
            recordAudioChunks(bufferSize)
        }
    }

    private fun initializeRandomTestAudio() {
        try {
            currentAudioResource = testAudioFiles.random()
            audioInputStream?.close()
            audioInputStream = resources.openRawResource(currentAudioResource!!)
            Log.d("RecordingService", "Initialized test audio fallback: $currentAudioResource")
        } catch (e: Exception) {
            Log.e("RecordingService", "Error initializing test audio", e)
        }
    }

  private suspend fun recordAudioChunks(bufferSize: Int) = coroutineScope {
      val buffer = ShortArray(bufferSize)
      var chunkBuffer = mutableListOf<Short>()
      val samplesPerChunk = (SAMPLE_RATE * CHUNK_DURATION_MS / 1000).toInt()
      val overlapSamples = (SAMPLE_RATE * OVERLAP_MS / 1000).toInt()

      var silenceStartTime = 0L
      var isSilent = false

      while (isActive && !isStopping) {
          if (isPaused) {
              delay(100)
              continue
          }

          // Use real MIC if available, otherwise ONLY fallback to test audio if MIC failed to initialize
          val readSize = if (audioRecord != null) {
              val result = audioRecord!!.read(buffer, 0, buffer.size)
              if (result < 0) {
                  Log.e("RecordingService", "AudioRecord read error: $result")
                  0
              } else {
                  // Log amplitude to verify capturing actual audio
                  val max = buffer.take(result).maxOrNull() ?: 0
                  if (max > 0) Log.v("RecordingService", "Captured audio data, max amplitude: $max")
                  result
              }
          } else {
              // Fallback to test audio only if AudioRecord is null
              if (audioInputStream == null) initializeRandomTestAudio()
              val byteBuffer = ByteArray(buffer.size * 2)
              val bytesRead = audioInputStream?.read(byteBuffer) ?: 0
              if (bytesRead > 0) {
                  for (i in 0 until bytesRead / 2) {
                      buffer[i] = ((byteBuffer[i * 2 + 1].toInt() shl 8) or (byteBuffer[i * 2].toInt() and 0xFF)).toShort()
                  }
                  bytesRead / 2
              } else {
                  initializeRandomTestAudio()
                  0
              }
          }

          if (readSize > 0) {
              val avgAmplitude = buffer.take(readSize).map { kotlin.math.abs(it.toInt()) }.average()
              if (avgAmplitude < SILENCE_THRESHOLD) {
                  if (silenceStartTime == 0L) silenceStartTime = System.currentTimeMillis()
                  else if (System.currentTimeMillis() - silenceStartTime > SILENCE_DURATION_MS && !isSilent) {
                      isSilent = true
                      notifyWarning("No audio detected - Check microphone")
                  }
              } else {
                  silenceStartTime = 0L
                  if (isSilent) {
                      isSilent = false
                      updateNotification("Recording...", getCurrentDuration())
                  }
              }

              chunkBuffer.addAll(buffer.take(readSize).toList())

              if (chunkBuffer.size >= samplesPerChunk) {
                  saveChunk(chunkBuffer.toShortArray())
                  chunkBuffer = chunkBuffer.takeLast(overlapSamples).toMutableList()
                  chunkSequence++
              }
          } else {
              delay(50) // Prevent CPU hammering if no data
          }

          if (!hasEnoughStorage()) {
              notifyError("Recording stopped - Low storage")
              withContext(Dispatchers.Main) { stopRecording() }
              break
          }
      }

      if (chunkBuffer.isNotEmpty() && !isPaused) {
          saveChunk(chunkBuffer.toShortArray())
      }
  }

    private suspend fun saveChunk(audioData: ShortArray) {
        val recordingId = currentRecordingId ?: return
        val chunkId = UUID.randomUUID().toString()
        val fileName = "chunk_${recordingId}_$chunkSequence.wav"
        val file = File(getRecordingDir(recordingId), fileName)

        Log.d("RecordingService", "Saving chunk $chunkSequence for $recordingId (Size: ${audioData.size})")

        withContext(Dispatchers.IO) {
            try {
                FileOutputStream(file).use { fos ->
                    // Write WAV Header (44 bytes)
                    writeWavHeader(fos, 1, SAMPLE_RATE, 16, audioData.size * 2L)
                    
                    val bytes = ByteArray(audioData.size * 2)
                    for (i in audioData.indices) {
                        bytes[i * 2] = (audioData[i].toInt() and 0xFF).toByte()
                        bytes[i * 2 + 1] = (audioData[i].toInt() shr 8 and 0xFF).toByte()
                    }
                    fos.write(bytes)
                }

                val chunk = AudioChunk(
                    id = chunkId,
                    recordingId = recordingId,
                    filePath = file.absolutePath,
                    sequence = chunkSequence,
                    duration = (audioData.size * 1000L) / SAMPLE_RATE
                )

                repository.insertChunk(chunk)
                TranscriptionWorker.enqueueTranscription(applicationContext, recordingId, chunkId)
            } catch (e: Exception) {
                Log.e("RecordingService", "Error saving chunk", e)
            }
        }
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        channels: Int,
        sampleRate: Int,
        bitDepth: Int,
        dataLength: Long
    ) {
        val totalLength = dataLength + 36
        val byteRate = (sampleRate * channels * bitDepth / 8).toLong()

        val header = ByteArray(44)
        header[0] = 'R'.toByte()
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'F'.toByte()
        header[4] = (totalLength and 0xffL).toByte()
        header[5] = (totalLength shr 8 and 0xffL).toByte()
        header[6] = (totalLength shr 16 and 0xffL).toByte()
        header[7] = (totalLength shr 24 and 0xffL).toByte()
        header[8] = 'W'.toByte()
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()
        header[12] = 'f'.toByte()
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xffL).toByte()
        header[29] = (byteRate shr 8 and 0xffL).toByte()
        header[30] = (byteRate shr 16 and 0xffL).toByte()
        header[31] = (byteRate shr 24 and 0xffL).toByte()
        header[32] = (channels * bitDepth / 8).toByte()
        header[33] = 0
        header[34] = bitDepth.toByte()
        header[35] = 0
        header[36] = 'd'.toByte()
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()
        header[40] = (dataLength and 0xffL).toByte()
        header[41] = (dataLength shr 8 and 0xffL).toByte()
        header[42] = (dataLength shr 16 and 0xffL).toByte()
        header[43] = (dataLength shr 24 and 0xffL).toByte()
        out.write(header, 0, 44)
    }

    fun pauseRecording(reason: String) {
        if (isPaused) return
        isPaused = true
        pauseReason = reason
        pauseStartTime = System.currentTimeMillis()
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e("RecordingService", "Error stopping AudioRecord during pause", e)
        }
        
        val status = when (reason) {
            "Phone call" -> "Paused - Phone call"
            "Audio focus lost" -> "Paused - Audio focus lost"
            else -> "Paused"
        }
        updateNotification(status, getCurrentDuration())

        serviceScope.launch {
            currentRecordingId?.let { id ->
                repository.updateRecordingStatus(id, if (reason == "Phone call") RecordingStatus.PAUSED_PHONE_CALL else RecordingStatus.PAUSED_AUDIO_FOCUS)
                repository.saveSession(id, startTime, getCurrentDuration(), true, reason)
            }
        }
    }

    fun resumeRecording() {
        if (!isPaused) return
        isPaused = false
        pausedDuration += System.currentTimeMillis() - pauseStartTime
        pauseReason = null
        updateNotification("Recording...", getCurrentDuration())

        serviceScope.launch {
            currentRecordingId?.let { id ->
                repository.updateRecordingStatus(id, RecordingStatus.RECORDING)
                repository.saveSession(id, startTime, getCurrentDuration(), false, null)
            }
        }
        try {
            audioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e("RecordingService", "Error resuming AudioRecord", e)
        }
    }

    private fun stopRecording() {
        if (isStopping) return
        isStopping = true
        isPaused = false

        Log.d("RecordingService", "Stopping recording for $currentRecordingId")

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("RecordingService", "Error releasing AudioRecord", e)
        }
        audioRecord = null

        timerJob?.cancel()
        audioFocusManager.abandonAudioFocus()

        val duration = getCurrentDuration()

        serviceScope.launch {
            currentRecordingId?.let { id ->
                repository.updateRecordingStatus(id, RecordingStatus.PROCESSING)
                recordingJob?.join()

                val chunks = repository.getChunksByRecordingId(id)
                if (chunks.isEmpty()) {
                    Log.d("RecordingService", "No chunks saved, inserting mock silence chunk")
                    saveChunk(ShortArray((SAMPLE_RATE * 2 / 1).toInt()) { 0 })
                }

                repository.updateRecordingDuration(id, duration)
                repository.clearSession()
                
                val untranscribed = repository.getUntranscribedChunksCount(id)
                if (untranscribed == 0) {
                    val finalChunks = repository.getChunksByRecordingId(id)
                    if (finalChunks.isNotEmpty()) {
                        val fullTranscript = repository.getFullTranscript(id)
                        repository.updateRecordingTranscript(id, fullTranscript)
                        repository.updateRecordingStatus(id, RecordingStatus.COMPLETED)
                        SummaryWorker.enqueueSummaryGeneration(applicationContext, id)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun startTimer() {
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                if (!isPaused) {
                    val duration = getCurrentDuration()
                    _recordingDuration.value = duration
                    updateNotification(if (isPaused) "Paused - ${pauseReason ?: "Unknown"}" else "Recording...", duration)
                }
            }
        }
    }

    private fun getCurrentDuration(): Long {
        return if (isPaused) {
            pauseStartTime - startTime - pausedDuration
        } else {
            System.currentTimeMillis() - startTime - pausedDuration
        }
    }

    private fun getRecordingDir(recordingId: String): File {
        val dir = File(filesDir, "recordings/$recordingId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun hasEnoughStorage(): Boolean {
        return filesDir.freeSpace > 50 * 1024 * 1024L
    }

    private fun setupAudioFocusListener() {
        audioFocusManager.setOnAudioFocusChangeListener { hasFocus ->
            if (!hasFocus) pauseRecording("Audio focus lost")
            else if (isPaused && pauseReason == "Audio focus lost") resumeRecording()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Recording Service", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(status: String, duration: Long): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, NotificationActionReceiver::class.java).apply { action = ACTION_STOP_RECORDING }
        val stopPendingIntent = PendingIntent.getBroadcast(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(status)
            .setContentText("Duration: ${formatDuration(duration)}")
            .setSmallIcon(R.drawable.baseline_fiber_manual_record_24)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.baseline_stop_circle_24, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(status: String, duration: Long) {
        notificationManager.notify(NOTIFICATION_ID, createNotification(status, duration))
    }

    private fun notifyWarning(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Warning").setContentText(message).setSmallIcon(R.drawable.ic_launcher_background).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        notificationManager.notify(2, notification)
    }

    private fun notifyError(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Error").setContentText(message).setSmallIcon(R.drawable.ic_launcher_background).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        notificationManager.notify(3, notification)
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))
        return if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
        else String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        audioInputStream?.close()
        serviceScope.cancel()
    }
}
