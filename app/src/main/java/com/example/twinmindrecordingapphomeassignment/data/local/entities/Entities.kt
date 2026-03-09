package com.example.twinmindrecordingapphomeassignment.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.twinmindhomeassignmentrecordingapp.domain.model.RecordingStatus

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val id: String,
    val title: String,
    val timestamp: Long,
    val duration: Long,
    val status: RecordingStatus,
    val transcript: String? = null,
    val summaryTitle: String? = null,
    val summaryText: String? = null,
    val actionItems: String? = null,
    val keyPoints: String? = null
)

@Entity(
    tableName = "audio_chunks",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("recordingId")]
)
data class AudioChunkEntity(
    @PrimaryKey val id: String,
    val recordingId: String,
    val filePath: String,
    val sequence: Int,
    val duration: Long,
    val transcribed: Boolean = false,
    val transcript: String? = null,
    val uploadedAt: Long? = null
)

@Entity(tableName = "recording_session")
data class RecordingSessionEntity(
    @PrimaryKey val id: Int = 1,
    val recordingId: String?,
    val startTime: Long?,
    val currentDuration: Long?,
    val isPaused: Boolean?,
    val pauseReason: String?
)