package com.example.testcompose.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testcompose.data.repository.ChatRepository
import com.example.testcompose.domain.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(private val repository: ChatRepository) : ViewModel() {

    // 私有可变状态
    private val _state = MutableStateFlow(ChatUiState())
    // 对外暴露只读状态（UI只能观察，不能修改）
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    // 用户输入文字时调用
    fun updateInput(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    // 点击发送时调用
    fun sendMessage() {
        val content = _state.value.inputText.trim()
        if (content.isEmpty()) return

        viewModelScope.launch {
            // 1. 添加用户消息到列表
            val userMessage = Message(
                id = UUID.randomUUID().toString(),
                role = "user",
                content = content
            )

            _state.update { current ->
                current.copy(
                    messages = current.messages + userMessage.toUiModel(),
                    inputText = "", // 清空输入框
                    isLoading = true
                )
            }

            // 2. 准备历史记录（包含刚加的用户消息）
            val history = _state.value.messages.map {
                Message(
                    id = it.id,
                    role = it.role,
                    content = it.content,
                    timestamp = it.timestamp
                )
            }

            // 3. 先加一个空的AI消息（正在生成）
            val aiMessageId = UUID.randomUUID().toString()
            _state.update { current ->
                current.copy(
                    messages = current.messages + ChatMessageUiModel(
                        id = aiMessageId,
                        role = "assistant",
                        content = "",
                        timestamp = System.currentTimeMillis(),
                        isGenerating = true
                    )
                )
            }

            // 4. 调用Repository流式获取回复
            try {
                repository.sendMessage(history).collect { chunk ->
                    // 每收到一个字，更新AI消息内容
                    _state.update { current ->
                        val updatedMessages = current.messages.map { msg ->
                            if (msg.id == aiMessageId) {
                                msg.copy(
                                    content = msg.content + chunk,
                                    isGenerating = true
                                )
                            } else msg
                        }
                        current.copy(messages = updatedMessages)
                    }
                }

                // 5. 标记生成完成
                _state.update { current ->
                    val finalMessages = current.messages.map { msg ->
                        if (msg.id == aiMessageId) msg.copy(isGenerating = false) else msg
                    }
                    current.copy(messages = finalMessages, isLoading = false)
                }
            } catch (e: Exception) {
                // 错误处理：显示错误信息
                _state.update { current ->
                    val errorMessages = current.messages.map { msg ->
                        if (msg.id == aiMessageId) {
                            msg.copy(
                                content = "Error: ${e.message}",
                                isGenerating = false
                            )
                        } else msg
                    }
                    current.copy(messages = errorMessages, isLoading = false)
                }
            }
        }
    }
}