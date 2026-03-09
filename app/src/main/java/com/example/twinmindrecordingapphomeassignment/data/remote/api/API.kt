package com.example.twinmindrecordingapphomeassignment.data.remote.api

import com.example.twinmindhomeassignmentrecordingapp.domain.model.MeetingSummary
import com.example.twinmindrecordingapphomeassignment.BuildConfig
import com.example.twinmindrecordingapphomeassignment.data.remote.dto.SummaryRequest
import com.example.twinmindrecordingapphomeassignment.data.remote.dto.SummaryResponse
import com.example.twinmindrecordingapphomeassignment.data.remote.dto.TranscriptionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named

interface TranscriptionApi {
    @Multipart
    @POST("transcribe")
    suspend fun transcribeAudio(
        @Part audio: MultipartBody.Part,
        @Part("language") language: String = "en"
    ): TranscriptionResponse

    @POST("summarize")
    suspend fun generateSummary(
        @Body request: SummaryRequest
    ): SummaryResponse
}

// Mock Implementation
class MockTranscriptionService {

    suspend fun transcribeAudio(audioFile: File): String {
        // Simulate API delay
        delay(2000)

        // Mock transcription based on file sequence
        val sequence = audioFile.nameWithoutExtension.substringAfterLast("_").toIntOrNull() ?: 0

        return when {
            sequence % 3 == 0 -> "This is a discussion about project timelines and deliverables. "
            sequence % 3 == 1 -> "We need to focus on the key milestones for the next quarter. "
            else -> "The team agreed to review the action items by end of week. "
        }
    }

    suspend fun generateSummary(transcript: String): SummaryResponse {
        // Simulate API delay
        delay(3000)

        return SummaryResponse(
            title = "Team Meeting Discussion",
            summary = "The meeting covered project timelines, key milestones, and upcoming deliverables. " +
                    "The team discussed various action items and agreed on priorities for the next quarter. " +
                    "Focus areas include project delivery, resource allocation, and timeline management.",
            actionItems = listOf(
                "Review project timeline by end of week",
                "Allocate resources for Q2 deliverables",
                "Schedule follow-up meeting for next month",
                "Update documentation with latest changes"
            ),
            keyPoints = listOf(
                "Project timeline needs adjustment for Q2",
                "Team capacity is at 85% for next quarter",
                "Key deliverables identified for upcoming sprint",
                "Documentation updates required by month end",
                "Resource allocation approved for new initiatives"
            )
        )
    }

    // Streaming summary simulation
    suspend fun generateSummaryStreaming(
        transcript: String,
        onUpdate: suspend (SummaryResponse) -> Unit
    ) {
        val title = "Team Meeting Discussion"
        val summaryWords = "The meeting covered project timelines, key milestones, and upcoming deliverables. " +
                "The team discussed various action items and agreed on priorities for the next quarter. " +
                "Focus areas include project delivery, resource allocation, and timeline management."

        val actionItems = listOf(
            "Review project timeline by end of week",
            "Allocate resources for Q2 deliverables",
            "Schedule follow-up meeting for next month",
            "Update documentation with latest changes"
        )

        val keyPoints = listOf(
            "Project timeline needs adjustment for Q2",
            "Team capacity is at 85% for next quarter",
            "Key deliverables identified for upcoming sprint",
            "Documentation updates required by month end",
            "Resource allocation approved for new initiatives"
        )

        // Stream title first
        delay(500)
        onUpdate(SummaryResponse(title, "", emptyList(), emptyList()))

        // Stream summary word by word
        val words = summaryWords.split(" ")
        var currentSummary = ""
        words.forEachIndexed { index, word ->
            delay(100)
            currentSummary += if (index == 0) word else " $word"
            onUpdate(SummaryResponse(title, currentSummary, emptyList(), emptyList()))
        }

        delay(300)

        // Stream action items one by one
        actionItems.forEachIndexed { index, item ->
            delay(200)
            onUpdate(SummaryResponse(
                title,
                summaryWords,
                actionItems.take(index + 1),
                emptyList()
            ))
        }

        delay(300)

        // Stream key points one by one
        keyPoints.forEachIndexed { index, point ->
            delay(200)
            onUpdate(SummaryResponse(
                title,
                summaryWords,
                actionItems,
                keyPoints.take(index + 1)
            ))
        }
    }
}

// WhisperApiService.kt
class WhisperApiService @Inject constructor(
    @Named("openai_api_key") private val apiKey: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(audioFile: File): String {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/wav".toMediaTypeOrNull())
            )
            .addFormDataPart("model", "whisper-1")
            .build()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
            .post(requestBody)
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
                val responseBody = response.body?.string() ?: throw IOException("Empty response")
                JSONObject(responseBody).getString("text")
            }
        }
    }
}

class ChatGptService @Inject constructor(
    @Named("openai_api_key") private val apiKey: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateSummary(transcript: String, onUpdate: suspend (MeetingSummary) -> Unit) {
        withContext(Dispatchers.IO) {
            val messages = listOf(
                mapOf(
                    "role" to "system",
                    "content" to "You are a meeting summarizer. Generate a concise summary with the following format: Title, Summary, Key Points (as bullet points), and Action Items (as bullet points)."
                ),
                mapOf(
                    "role" to "user",
                    "content" to transcript
                )
            )

            val requestBody = JSONObject().apply {
                put("model", "gpt-4-turbo")
                put("messages", JSONArray(messages))
                put("temperature", 0.7)
                put("stream", true)
            }.toString()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")

                val reader = response.body?.charStream() ?: throw IOException("Empty response")
                val buffer = StringBuilder()

                reader.useLines { lines ->
                    lines.forEach { line ->
                        if (line.startsWith("data: ") && line != "data: [DONE]") {
                            val content = JSONObject(line.substring(6))
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("delta")
                                .optString("content", "")

                            buffer.append(content)

                            // Try to parse complete sections
                            val summary = parseSummary(buffer.toString())
                            if (summary != null) {
                                withContext(Dispatchers.Main) {
                                    onUpdate(summary)
                                }
                            }
                        }
                    }
                }

                // Final update with complete summary
                val finalSummary = parseSummary(buffer.toString())
                if (finalSummary != null) {
                    withContext(Dispatchers.Main) {
                        onUpdate(finalSummary)
                    }
                }
            }
        }
    }

    private fun parseSummary(text: String): MeetingSummary? {
        try {
            var title = ""
            var summary = ""
            val keyPoints = mutableListOf<String>()
            val actionItems = mutableListOf<String>()

            // Simple parsing logic
            val lines = text.split("\n")
            var currentSection = ""

            for (line in lines) {
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty()) continue

                when {
                    trimmedLine.startsWith("Title:", ignoreCase = true) -> {
                        title = trimmedLine.substringAfter(":").trim()
                        currentSection = "title"
                    }
                    trimmedLine.startsWith("Summary:", ignoreCase = true) -> {
                        summary = trimmedLine.substringAfter(":").trim()
                        currentSection = "summary"
                    }
                    trimmedLine.startsWith("Key Points:", ignoreCase = true) -> {
                        currentSection = "keyPoints"
                    }
                    trimmedLine.startsWith("Action Items:", ignoreCase = true) -> {
                        currentSection = "actionItems"
                    }
                    trimmedLine.startsWith("-") -> {
                        val item = trimmedLine.substringAfter("-").trim()
                        if (currentSection == "keyPoints") keyPoints.add(item)
                        else if (currentSection == "actionItems") actionItems.add(item)
                    }
                    else -> {
                        if (currentSection == "summary") summary += " $trimmedLine"
                    }
                }
            }

            return if (title.isNotEmpty() || summary.isNotEmpty()) {
                MeetingSummary(title, summary, actionItems, keyPoints)
            } else null
        } catch (e: Exception) {
            return null
        }
    }
}
