package com.example.lab4.chat

data class ChatMessage(
    val id: String,
    val userId: String,
    val userName: String,
    val text: String,
    val timestampMs: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyM: Float? = null,
)

