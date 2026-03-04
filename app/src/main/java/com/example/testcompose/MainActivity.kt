package com.example.testcompose

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.baidu.speech.aidl.EventManagerFactory
import com.example.testcompose.data.repository.ChatRepositoryImpl
import com.example.testcompose.presentation.chat.ChatScreen
import com.example.testcompose.presentation.chat.ChatViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 Repository - 连接本地 AI 服务器
        // 确保服务器已启动: python server/server.py
        val repository = ChatRepositoryImpl()
        val viewModel = ChatViewModel(repository)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            // 申请权限
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
        }
        setContent {
            // Material 3 主题
            MaterialTheme {
                Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatScreen(viewModel = viewModel)
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                println("DEBUG: 录音权限已授予")
            } else {
                println("DEBUG: 录音权限被拒绝")
                // 可以弹 Toast 提示用户去设置里手动开启
            }
        }
    }
}