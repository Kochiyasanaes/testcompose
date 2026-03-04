package com.example.testcompose.presentation.chat

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testcompose.data.voice.TtsManager
import com.example.testcompose.data.voice.VoiceManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // 语音输入（你说）
    val voiceManager = remember { VoiceManager(context) }

    // 语音朗读（Kimi说）
    val ttsManager = remember { TtsManager(context) }

    // 录音状态（用于语音输入）
    var isRecording by remember { mutableStateOf(false) }

    // 自动朗读开关
    var autoSpeak by remember { mutableStateOf(true) }

    // 已朗读过的消息ID（避免重复朗读同一条）
    val spokenMessageIds = remember { mutableSetOf<String>() }

    // 当前正在生成的消息ID
    var currentGeneratingId by remember { mutableStateOf<String?>(null) }

    // 清理资源（页面销毁时）
    DisposableEffect(Unit) {
        onDispose {
            voiceManager.cancel()
            ttsManager.destroy()
        }
    }

    // 自动滚动到底部
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    // ★★★ 自动朗读Kimi的回复 ★★★
    LaunchedEffect(state.messages) {
        val lastMessage = state.messages.lastOrNull()

        // 检测条件：Kimi的消息、生成完毕、有内容、没朗读过、开启了自动朗读
        if (lastMessage?.role == "assistant" &&
            !lastMessage.isGenerating &&
            lastMessage.content.isNotBlank() &&
            !spokenMessageIds.contains(lastMessage.id) &&
            autoSpeak
        ) {
            // 标记为已朗读
            spokenMessageIds.add(lastMessage.id)

            // 清理格式（去掉Markdown符号，手表不用读这些）
            val plainText = lastMessage.content
                .replace(Regex("[*#`>\\[\\]()\\-]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(150)  // 手表只读前150字

            if (plainText.isNotBlank()) {
                delay(300)  // 稍微延迟，让用户先看一眼文字
                ttsManager.speak(plainText)
            }
        }

        // 用户发起新提问时，停止之前的朗读（避免重叠）
        val generatingMsg = state.messages.find { it.isGenerating && it.role == "assistant" }
        if (generatingMsg != null && generatingMsg.id != currentGeneratingId) {
            currentGeneratingId = generatingMsg.id
            ttsManager.stop()  // 有新问题就停止旧朗读
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // 顶部标题栏 + 语音朗读开关
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Kimi",
                style = MaterialTheme.typography.titleMedium,
                fontSize = 16.sp
            )

            // 自动朗读开关（喇叭图标）
            IconButton(
                onClick = {
                    autoSpeak = !autoSpeak
                    if (!autoSpeak) ttsManager.stop()  // 关闭时立即停止
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (autoSpeak) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = if (autoSpeak) "朗读开" else "朗读关",
                    tint = if (autoSpeak) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 消息列表（点击消息可停止朗读）
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                items = state.messages,
                key = { it.id }
            ) { message ->
                MessageBubble(
                    message = message,
                    onClick = { ttsManager.stop() }  // 点击停止朗读
                )
            }
        }

        // 底部输入区（保留原有语音输入功能）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 输入框或录音状态提示
            if (!isRecording) {
                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = { newText: String ->    // ← 加上 : String
                        viewModel.updateInput(newText)
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "输入或点击麦克风...",
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    enabled = !state.isLoading,
                    singleLine = true,
//                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
//                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                )
            } else {
                // 录音中提示（红色）
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "正在听...松开发送",
                            color = Color.Red,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 发送/录音按钮（保留原有逻辑）
            IconButton(
                onClick = {
                    when {
                        // 情况1：正在录音 -> 停止并发送
                        isRecording -> {
                            println("DEBUG: 停止录音")
                            isRecording = false
                            voiceManager.stopListening()
                            // 注意：识别结果在回调里处理，这里不直接send
                        }

                        // 情况2：输入框为空 -> 开始语音输入
                        state.inputText.isBlank() -> {
                            println("DEBUG: 开始录音")
                            // 开始录音时停止正在播放的朗读（避免冲突）
                            ttsManager.stop()

                            isRecording = true
                            voiceManager.startListening { text, isLast ->
                                println("DEBUG: 识别结果: $text, isLast: $isLast")

                                // 实时更新输入框（显示识别过程）
                                viewModel.updateInput(text)

                                // 识别完成且是最终结果 -> 自动发送
                                if (isLast && text.isNotBlank()) {
                                    isRecording = false
                                    // 延迟一点确保状态更新
                                    viewModel.sendMessage()
                                }
                            }
                        }

                        // 情况3：输入框有文字 -> 直接发送
                        else -> {
                            viewModel.sendMessage()
                        }
                    }
                },
                enabled = !state.isLoading,
                modifier = Modifier.size(40.dp)
            ) {
                when {
                    // 加载中显示转圈
                    state.isLoading -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    // 录音中显示红色麦克风
                    isRecording -> Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "录音中",
                        tint = Color.Red,
                        modifier = Modifier.size(22.dp)
                    )
                    // 输入框为空显示麦克风（可点击说话）
                    state.inputText.isBlank() -> Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "点击说话",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    // 输入框有文字显示发送
                    else -> Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "发送",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// 消息气泡组件
@Composable
fun MessageBubble(
    message: ChatMessageUiModel,
    onClick: () -> Unit = {}
) {
    val isUser = message.role == "user"
    val backgroundColor = if (isUser)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        horizontalAlignment = alignment
    ) {
        Surface(
            color = backgroundColor,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.widthIn(max = 220.dp)  // 手表上窄一点
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = message.content,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                // 正在生成时显示光标闪烁
                if (message.isGenerating) {
                    Text(
                        text = "▋",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        // 时间戳
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(Date(message.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            fontSize = 9.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}