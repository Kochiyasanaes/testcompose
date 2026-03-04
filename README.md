# TestCompose

一个基于 Jetpack Compose 的 Android 聊天应用，支持语音识别和语音合成。

## 功能特性

- 💬 **聊天界面** - 使用 Jetpack Compose 构建的现代化 UI
- 🎤 **语音识别** - 集成 Vosk 离线语音识别引擎（中文支持）
- 🔊 **语音合成** - 支持 TTS 语音播报
- 🏗️ **Clean Architecture** - 采用分层架构设计（Data/Domain/Presentation）

## 技术栈

| 层级 | 技术 |
|------|------|
| UI | Jetpack Compose |
| 架构 | MVVM + Clean Architecture |
| 依赖注入 | 手动注入（无 Hilt） |
| 网络 | 原生 HttpURLConnection |
| 语音识别 | Vosk (离线) + 百度语音 SDK |
| 语音合成 | Android TTS |

## 项目结构

```
app/src/main/java/com/example/testcompose/
├── data/                    # 数据层
│   ├── remote/dto/         # API 数据传输对象
│   ├── repository/         # 仓库接口和实现
│   └── voice/              # 语音管理（TTS/ASR）
├── domain/                  # 领域层
│   └── model/              # 领域模型
└── presentation/            # 表现层
    └── chat/               # 聊天界面相关
```

## 语音识别模型

项目集成了 Vosk 中文小模型 `vosk-model-small-cn-0.22`，位于：
```
app/src/main/assets/vosk-model-small-cn-0.22/
```

## 构建要求

- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 35
- Gradle 8.0+

## 快速开始

1. 克隆仓库
```bash
git clone https://github.com/Kochiyasanaes/testcompose.git
```

2. 在 Android Studio 中打开项目

3. 同步 Gradle 并运行

## 配置说明

### 语音 API
在 `ChatRepositoryImpl.kt` 中配置你的 API 端点：

```kotlin
private const val API_URL = "你的API地址"
```

### Vosk 模型
首次运行时会自动从 assets 加载中文语音模型。

## 截图

（待添加）

## 许可证

MIT License

## 作者

[@Kochiyasanaes](https://github.com/Kochiyasanaes)
