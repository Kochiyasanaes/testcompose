package com.example.testcompose.presentation.chat

import com.example.testcompose.domain.model.Message

data class ChatUiState(
    val messages: List<ChatMessageUiModel> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false
)

// 这才是带isGenerating的（UI层专用）
data class ChatMessageUiModel(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val isGenerating: Boolean = false  // 只有UI关心这个
)

// 转换函数：Domain -> UI
fun Message.toUiModel(isGenerating: Boolean = false): ChatMessageUiModel {
    return ChatMessageUiModel(
        id = this.id,
        role = this.role,
        content = this.content,
        timestamp = this.timestamp,
        isGenerating = isGenerating
    )
}