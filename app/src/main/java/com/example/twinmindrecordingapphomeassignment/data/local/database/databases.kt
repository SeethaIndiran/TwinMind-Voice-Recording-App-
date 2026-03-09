package com.example.twinmindrecordingapphomeassignment.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.twinmindhomeassignmentrecordingapp.domain.model.RecordingStatus
import com.example.twinmindrecordingapphomeassignment.data.local.dao.AudioChunkDao
import com.example.twinmindrecordingapphomeassignment.data.local.dao.RecordingDao
import com.example.twinmindrecordingapphomeassignment.data.local.dao.RecordingSessionDao
import com.example.twinmindrecordingapphomeassignment.data.local.entities.AudioChunkEntity
import com.example.twinmindrecordingapphomeassignment.data.local.entities.RecordingEntity
import com.example.twinmindrecordingapphomeassignment.data.local.entities.RecordingSessionEntity

@Database(
    entities = [
        RecordingEntity::class,
        AudioChunkEntity::class,
        RecordingSessionEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class VoiceRecorderDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun audioChunkDao(): AudioChunkDao
    abstract fun recordingSessionDao(): RecordingSessionDao
}

class Converters {
    @TypeConverter
    fun fromRecordingStatus(value: RecordingStatus): String = value.name

    @TypeConverter
    fun toRecordingStatus(value: String): RecordingStatus =
        RecordingStatus.valueOf(value)
}