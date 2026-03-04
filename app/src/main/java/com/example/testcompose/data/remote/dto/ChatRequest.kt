package com.example.testcompose.data.remote.dto

data class ChatRequest(
    val model: String = "moonshot-v1-8k",
    val messages: List<ChatMessageDto>,
    val stream: Boolean = true
)

data class ChatMessageDto(
    val role: String,
    val content: String
)

// 转换函数：Domain -> DTO
fun com.example.testcompose.domain.model.Message.toDto(): ChatMessageDto {
    return ChatMessageDto(role = this.role, content = this.content)
}