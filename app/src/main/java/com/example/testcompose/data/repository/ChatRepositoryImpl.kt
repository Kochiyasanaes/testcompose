package com.example.testcompose.data.repository

import com.example.testcompose.data.remote.dto.ChatRequest
import com.example.testcompose.data.remote.dto.toDto
import com.example.testcompose.domain.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ChatRepositoryImpl(private val apiKey: String) : ChatRepository {

    // OkHttp客户端，配置超时时间
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // 流式需要长连接
        .build()

    override suspend fun sendMessage(messages: List<Message>): Flow<String> = flow {
        // 1. 把Domain Model转成API需要的DTO格式
        val dtoMessages = messages.map { it.toDto() }
        val requestBody = ChatRequest(
            messages = dtoMessages,
            stream = true  // 开流式
        )

        // 2. 手动拼JSON（避免Gson依赖，先用原生JSONObject）
        val json = JSONObject().apply {
            put("model", "moonshot-v1-8k")
            put("stream", true)
            put("messages", org.json.JSONArray().apply {
                dtoMessages.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            })
        }

        // 3. 构建HTTP请求
        val request = Request.Builder()
            .url("https://api.moonshot.cn/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        // 4. 执行请求（IO线程）
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}")
            }

            // 5. 读取SSE流（Server-Sent Events）
            val source = response.body?.source() ?: return@use
            val buffer = okio.Buffer()

            while (!source.exhausted()) {
                source.readUtf8Line()?.let { line ->
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6)
                        if (data == "[DONE]") return@use

                        // 解析JSON拿到content字段
                        try {
                            val jsonObj = JSONObject(data)
                            val choices = jsonObj.getJSONArray("choices")
                            if (choices.length() > 0) {
                                val delta = choices.getJSONObject(0).optJSONObject("delta")
                                val content = delta?.optString("content", "")
                                if (!content.isNullOrEmpty()) {
                                    emit(content) // 发射这个字给ViewModel
                                }
                            }
                        } catch (e: Exception) {
                            // 忽略解析错误，继续读
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO) // 确保整个flow在IO线程执行

    override suspend fun getHistory(): List<Message> {
        // 暂时返回空，后面加Room再实现
        return emptyList()
    }
}