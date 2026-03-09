package com.example.twinmindrecordingapphomeassignment.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.twinmindhomeassignmentrecordingapp.domain.model.RecordingStatus
import com.example.twinmindrecordingapphomeassignment.data.local.entities.AudioChunkEntity
import com.example.twinmindrecordingapphomeassignment.data.local.entities.RecordingEntity
import com.example.twinmindrecordingapphomeassignment.data.local.entities.RecordingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getRecordingById(id: String): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun observeRecordingById(id: String): Flow<RecordingEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity)

    @Update
    suspend fun updateRecording(recording: RecordingEntity)

    @Query("UPDATE recordings SET status = :status WHERE id = :id")
    suspend fun updateRecordingStatus(id: String, status: RecordingStatus)

    @Query("UPDATE recordings SET transcript = :transcript WHERE id = :id")
    suspend fun updateRecordingTranscript(id: String, transcript: String)

    @Query("""
        UPDATE recordings 
        SET summaryTitle = :title,
            summaryText = :summary,
            actionItems = :actionItems,
            keyPoints = :keyPoints,
            status = :status
        WHERE id = :id
    """)
    suspend fun updateRecordingSummary(
        id: String,
        title: String,
        summary: String,
        actionItems: String,
        keyPoints: String,
        status: RecordingStatus
    )

    @Delete
    suspend fun deleteRecording(recording: RecordingEntity)
}

@Dao
interface AudioChunkDao {
    @Query("SELECT * FROM audio_chunks WHERE recordingId = :recordingId ORDER BY sequence ASC")
    suspend fun getChunksByRecordingId(recordingId: String): List<AudioChunkEntity>

    @Query("SELECT * FROM audio_chunks WHERE recordingId = :recordingId ORDER BY sequence ASC")
    fun observeChunksByRecordingId(recordingId: String): Flow<List<AudioChunkEntity>>

    @Query("SELECT * FROM audio_chunks WHERE id = :id")
    suspend fun getChunkById(id: String): AudioChunkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: AudioChunkEntity)

    @Update
    suspend fun updateChunk(chunk: AudioChunkEntity)

    @Query("UPDATE audio_chunks SET transcribed = :transcribed, transcript = :transcript WHERE id = :id")
    suspend fun updateChunkTranscript(id: String, transcribed: Boolean, transcript: String)

    @Query("SELECT COUNT(*) FROM audio_chunks WHERE recordingId = :recordingId AND transcribed = 0")
    suspend fun getUntranscribedChunksCount(recordingId: String): Int

    @Delete
    suspend fun deleteChunk(chunk: AudioChunkEntity)
}

@Dao
interface RecordingSessionDao {
    @Query("SELECT * FROM recording_session WHERE id = 1")
    suspend fun getSession(): RecordingSessionEntity?

    @Query("SELECT * FROM recording_session WHERE id = 1")
    fun observeSession(): Flow<RecordingSessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: RecordingSessionEntity)

    @Query("DELETE FROM recording_session")
    suspend fun clearSession()
}