package com.example.testcompose.data.repository

import com.example.testcompose.domain.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * ChatRepository 实现 - 连接本地 AI 代理服务器
 * 
 * 本地服务器地址配置：
 * - 模拟器: http://10.0.2.2:8000/chat (10.0.2.2 是模拟器访问主机的特殊 IP)
 * - 真机: http://<电脑IP>:8000/chat (确保手机和电脑在同一 WiFi)
 * - 远程服务器: http://your-server.com:8000/chat
 */
class ChatRepositoryImpl : ChatRepository {

    // 本地服务器地址配置
    companion object {
        // 模拟器使用这个地址访问主机
        private const val LOCAL_SERVER_URL = "http://10.0.2.2:8000"
        
        // 真机使用时，改成电脑的局域网 IP，例如：
        // private const val LOCAL_SERVER_URL = "http://192.168.1.100:8000"
    }

    // OkHttp客户端
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun sendMessage(messages: List<Message>): Flow<String> = flow {
        // 1. 准备请求数据
        val lastMessage = messages.lastOrNull()?.content ?: ""
        
        // 构建历史记录（排除最后一条）
        val history = messages.dropLast(1).map { msg ->
            JSONObject().apply {
                put("role", if (msg.isFromUser) "user" else "assistant")
                put("content", msg.content)
            }
        }

        // 2. 构建请求 JSON
        val json = JSONObject().apply {
            put("message", lastMessage)
            put("history", JSONArray(history))
        }

        // 3. 发送到本地服务器（流式接口）
        val request = Request.Builder()
            .url("$LOCAL_SERVER_URL/chat/stream")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        // 4. 执行请求
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("服务器错误: HTTP ${response.code}")
            }

            // 5. 读取 SSE 流
            val source = response.body?.source() ?: return@use

            while (!source.exhausted()) {
                source.readUtf8Line()?.let { line ->
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6)
                        
                        try {
                            val jsonObj = JSONObject(data)
                            
                            // 检查错误
                            if (jsonObj.has("error")) {
                                throw Exception(jsonObj.getString("error"))
                            }
                            
                            // 提取内容
                            val content = jsonObj.optString("content", "")
                            if (content.isNotEmpty()) {
                                emit(content)
                            }
                        } catch (e: Exception) {
                            // 忽略解析错误
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 非流式请求（备用方案）
     */
    suspend fun sendMessageSync(messages: List<Message>): String {
        val lastMessage = messages.lastOrNull()?.content ?: ""
        
        val history = messages.dropLast(1).map { msg ->
            JSONObject().apply {
                put("role", if (msg.isFromUser) "user" else "assistant")
                put("content", msg.content)
            }
        }

        val json = JSONObject().apply {
            put("message", lastMessage)
            put("history", JSONArray(history))
        }

        val request = Request.Builder()
            .url("$LOCAL_SERVER_URL/chat")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("服务器错误: HTTP ${response.code}")
            }
            
            val responseBody = response.body?.string() ?: "{}"
            val jsonObj = JSONObject(responseBody)
            
            if (jsonObj.has("response")) {
                return jsonObj.getString("response")
            } else {
                throw Exception("响应格式错误")
            }
        }
    }

    /**
     * 检查服务器健康状态
     */
    suspend fun checkHealth(): Boolean {
        return try {
            val request = Request.Builder()
                .url("$LOCAL_SERVER_URL/health")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getHistory(): List<Message> {
        // 暂不实现本地历史，后面可加 Room 数据库
        return emptyList()
    }
}
