# TestCompose

一个基于 Jetpack Compose 的 Android 聊天应用，支持语音识别和语音合成功能。

## 功能特性

- 💬 **聊天界面** - 使用 Jetpack Compose 构建的现代化 UI
- 🎤 **语音识别** - 集成 Vosk 离线语音识别（中文）
- 🔊 **语音合成** - 支持百度语音 TTS
- 🏗️ **Clean Architecture** - 采用分层架构设计

## 技术栈

| 技术 | 说明 |
|------|------|
| Kotlin | 编程语言 |
| Jetpack Compose | UI 框架 |
| MVVM | 架构模式 |
| Vosk | 离线语音识别 |
| 百度语音 SDK | 语音合成 |

## 项目结构

```
app/src/main/java/com/example/testcompose/
├── data/              # 数据层
│   ├── remote/        # 网络请求
│   ├── repository/    # 仓库
│   └── voice/         # 语音管理
├── domain/            # 领域层
│   └── model/         # 数据模型
├── presentation/      # 表现层
│   └── chat/          # 聊天界面
└── MainActivity.kt    # 主入口
```

## 运行要求

- Android 6.0 (API 23) 及以上
- 需要麦克风权限

## 语音识别模型

项目已内置 Vosk 中文小型模型：`vosk-model-small-cn-0.22`

## 依赖配置

```kotlin
// build.gradle.kts
implementation(platform(libs.androidx.compose.bom))
implementation(libs.androidx.ui)
implementation(libs.androidx.material3)
implementation(libs.androidx.lifecycle.viewmodel.compose)
```

## 使用说明

1. 点击麦克风按钮开始语音输入
2. 说话完成后自动识别并发送消息
3. 应用支持语音回复（TTS）

## 许可证

MIT License
