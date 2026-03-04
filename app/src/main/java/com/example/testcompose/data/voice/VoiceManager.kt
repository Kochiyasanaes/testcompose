package com.example.testcompose.data.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class VoiceManager(private val context: Context) {
    private val appId = "122157371"
    private val apiKey = "DOe8ri0unDm6FjCYDWCQU2I0"
    private val secretKey = "SNEt9EUQI5HKvKhZCso5oAOreRkmt0Xk"

    private val client = OkHttpClient()
    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    // ★ 新增：用于管理协程任务，允许取消
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var currentJob: Job? = null  // 就是这里定义的！

    // 录音参数
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    fun startListening(onResult: (String, Boolean) -> Unit) {
        // 如果之前有任务在跑，取消掉
        currentJob?.cancel()

        currentJob = scope.launch {
            try {
                onResult("正在录音...", false)

                // 1. 录音
                val audioData = withContext(Dispatchers.IO) {
                    startRecordingInternal()
                }

                // 如果录音被取消（用户点了停止），audioData 会是空的
                if (audioData.isEmpty()) {
                    onResult("", true)
                    return@launch
                }

                onResult("正在识别...", false)

                // 2. 获取 Token
                val token = withContext(Dispatchers.IO) {
                    getAccessToken()
                }

                // 3. 识别
                val result = withContext(Dispatchers.IO) {
                    recognize(audioData, token)
                }

                onResult(result, true)

            } catch (e: CancellationException) {
                // 正常取消，不用报错
                onResult("", true)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult("识别错误: ${e.message}", true)
            }
        }
    }

    fun stopListening() {
        isRecording = false
        // 停止录音，但让识别继续跑（因为录音已经录完了，现在在等服务器返回）
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    fun cancel() {
        // 完全取消（录音+识别都停）
        currentJob?.cancel()
        stopListening()
    }

    private suspend fun startRecordingInternal(): ByteArray = withContext(Dispatchers.IO) {
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(bufferSize)

        audioRecord?.startRecording()
        isRecording = true
        val startTime = System.currentTimeMillis()

        // 录音最多60秒，或者直到 isRecording 被设为 false
        while (isRecording && System.currentTimeMillis() - startTime < 60000) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (read > 0) {
                outputStream.write(buffer, 0, read)
            }
            // 让出时间片检查取消状态
            yield()
        }

        audioRecord?.release()
        audioRecord = null

        return@withContext outputStream.toByteArray()
    }

    private fun getAccessToken(): String {
        val url = "https://aip.baidubce.com/oauth/2.0/token" +
                "?grant_type=client_credentials" +
                "&client_id=$apiKey" +
                "&client_secret=$secretKey"

        val request = Request.Builder()
            .url(url)
            .post("".toRequestBody())
            .build()

        client.newCall(request).execute().use { response ->
            val json = JSONObject(response.body?.string() ?: "{}")
            return json.getString("access_token")
        }
    }

    private fun recognize(audioData: ByteArray, token: String): String {
        val speechBase64 = Base64.encodeToString(audioData, Base64.NO_WRAP)

        val jsonBody = JSONObject().apply {
            put("format", "pcm")
            put("rate", 16000)
            put("channel", 1)
            put("cuid", "testcompose_${System.currentTimeMillis()}")
            put("token", token)
            put("speech", speechBase64)
            put("len", audioData.size)
        }

        val request = Request.Builder()
            .url("https://vop.baidu.com/server_api")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val resultJson = JSONObject(response.body?.string() ?: "{}")

            return if (resultJson.optInt("err_no") == 0) {
                val resultList = resultJson.getJSONArray("result")
                resultList.getString(0)
            } else {
                "识别失败: ${resultJson.optString("err_msg")}"
            }
        }
    }
}