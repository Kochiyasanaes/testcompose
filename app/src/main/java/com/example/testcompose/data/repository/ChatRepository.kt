package com.example.testcompose.data.repository

import com.example.testcompose.domain.model.Message
import kotlinx.coroutines.flow.Flow

// 接口定义契约，Domain层只声明"我要什么"，不管"怎么实现"
interface ChatRepository {
    // suspend = 挂起函数（可在协程中暂停，不卡线程）
    // Flow<String> = 流式返回，Kimi每吐一个字，就emit一次
    suspend fun sendMessage(messages: List<Message>): Flow<String>

    // 获取历史记录（后面加Room时用）
    suspend fun getHistory(): List<Message>
}