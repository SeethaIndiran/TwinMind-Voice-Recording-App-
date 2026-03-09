package com.example.twinmindrecordingapphomeassignment.di



import android.content.Context
import androidx.room.Room
import com.example.twinmindrecordingapphomeassignment.data.local.dao.AudioChunkDao
import com.example.twinmindrecordingapphomeassignment.data.local.dao.RecordingDao
import com.example.twinmindrecordingapphomeassignment.data.local.dao.RecordingSessionDao
import com.example.twinmindrecordingapphomeassignment.data.local.database.VoiceRecorderDatabase
import com.example.twinmindrecordingapphomeassignment.data.remote.api.ChatGptService
import com.example.twinmindrecordingapphomeassignment.data.remote.api.MockTranscriptionService
import com.example.twinmindrecordingapphomeassignment.data.remote.api.WhisperApiService
import com.example.twinmindrecordingapphomeassignment.data.repository.RecordingRepository
import com.example.twinmindrecordingapphomeassignment.service.AudioFocusManager
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): VoiceRecorderDatabase {
        return Room.databaseBuilder(
            context,
            VoiceRecorderDatabase::class.java,
            "voice_recorder_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideRecordingDao(database: VoiceRecorderDatabase): RecordingDao {
        return database.recordingDao()
    }

    @Provides
    @Singleton
    fun provideAudioChunkDao(database: VoiceRecorderDatabase): AudioChunkDao {
        return database.audioChunkDao()
    }

    @Provides
    @Singleton
    fun provideRecordingSessionDao(database: VoiceRecorderDatabase): RecordingSessionDao {
        return database.recordingSessionDao()
    }

    @Provides
    @Singleton
    fun provideMockTranscriptionService(): MockTranscriptionService {
        return MockTranscriptionService()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }

    @Provides
    @Singleton
    fun provideRecordingRepository(
        recordingDao: RecordingDao,
        audioChunkDao: AudioChunkDao,
        sessionDao: RecordingSessionDao,
         chatGptService: ChatGptService,
        transcriptionService: WhisperApiService,
        gson: Gson
    ): RecordingRepository {
        return RecordingRepository(
            recordingDao,
            audioChunkDao,
            sessionDao,
            chatGptService,
            transcriptionService,
            gson
        )
    }

    @Provides
    @Singleton
    fun provideAudioFocusManager(
        @ApplicationContext context: Context
    ): AudioFocusManager {
        return AudioFocusManager(context)
    }
}