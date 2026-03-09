package com.example.twinmindrecordingapphomeassignment.service



import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.telephony.TelephonyManager
import javax.inject.Inject
import javax.inject.Singleton

// Audio Focus Manager
@Singleton
class AudioFocusManager @Inject constructor(
    private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var focusChangeListener: ((Boolean) -> Unit)? = null

    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                focusChangeListener?.invoke(false)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusChangeListener?.invoke(true)
            }
        }
    }

    fun setOnAudioFocusChangeListener(listener: (Boolean) -> Unit) {
        focusChangeListener = listener
    }

    fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(afChangeListener)
                .build()

            audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                afChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(afChangeListener)
        }
    }
}

// Phone Call Receiver
class PhoneCallReceiver : BroadcastReceiver() {

    companion object {
        private var wasRecording = false
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING,
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Phone call started - pause recording
                context?.let {
                    val serviceIntent = Intent(it, RecordingService::class.java).apply {
                        action = RecordingService.ACTION_PAUSE_RECORDING
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        it.startForegroundService(serviceIntent)
                    } else {
                        it.startService(serviceIntent)
                    }
                    wasRecording = true
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Phone call ended - resume recording if it was recording
                if (wasRecording) {
                    context?.let {
                        val serviceIntent = Intent(it, RecordingService::class.java).apply {
                            action = RecordingService.ACTION_RESUME_RECORDING
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            it.startForegroundService(serviceIntent)
                        } else {
                            it.startService(serviceIntent)
                        }
                    }
                    wasRecording = false
                }
            }
        }
    }
}

// Notification Action Receiver
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return

        when (intent?.action) {
            RecordingService.ACTION_STOP_RECORDING -> {
                val serviceIntent = Intent(context, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_STOP_RECORDING
                }
                context.startService(serviceIntent)
            }
            RecordingService.ACTION_RESUME_RECORDING -> {
                val serviceIntent = Intent(context, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_RESUME_RECORDING
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}