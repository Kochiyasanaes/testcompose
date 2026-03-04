package com.example.testcompose.domain.model

import java.util.UUID

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: String,      // "user" | "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)