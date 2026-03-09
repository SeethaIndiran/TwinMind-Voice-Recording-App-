package com.example.twinmindrecordingapphomeassignment.data.remote.dto

data class TranscriptionResponse(
    val text: String,
    val duration: Double
)

data class SummaryRequest(
    val transcript: String
)

data class SummaryResponse(
    val title: String,
    val summary: String,
    val actionItems: List<String>,
    val keyPoints: List<String>
)