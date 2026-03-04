# AI Proxy Server

本地 AI 代理服务器，用于处理 Android 设备的 AI 请求。

## 功能

- 接收 Android 设备的文本请求
- 转发到 AI API（OpenAI / Kimi / 其他）
- 返回 AI 响应
- 支持流式响应（可选）

## 快速开始

### 1. 安装依赖

```bash
cd server
python -m pip install -r requirements.txt
```

### 2. 配置 API Key

复制 `.env.example` 到 `.env`，填入你的 API Key：

```bash
cp .env.example .env
```

编辑 `.env`：
```
# 选择使用的 AI 提供商: openai / kimi / openrouter
AI_PROVIDER=kimi

# Kimi API (推荐，国内访问稳定)
KIMI_API_KEY=your_kimi_api_key_here
KIMI_MODEL=kimi-k2.5

# 或者 OpenAI
OPENAI_API_KEY=your_openai_api_key_here
OPENAI_MODEL=gpt-4o-mini

# 服务器配置
HOST=0.0.0.0
PORT=8000
```

### 3. 启动服务器

```bash
python server.py
```

服务器启动后会显示：
```
AI Proxy Server running on http://0.0.0.0:8000
```

### 4. 测试

```bash
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "你好"}'
```

## API 文档

### POST /chat

发送消息给 AI

**请求体：**
```json
{
  "message": "用户输入的文本",
  "history": [
    {"role": "user", "content": "之前的消息"},
    {"role": "assistant", "content": "AI的回复"}
  ]
}
```

**响应：**
```json
{
  "response": "AI的回复内容",
  "model": "kimi-k2.5",
  "usage": {
    "prompt_tokens": 10,
    "completion_tokens": 20,
    "total_tokens": 30
  }
}
```

### GET /health

健康检查

**响应：**
```json
{
  "status": "ok",
  "model": "kimi-k2.5"
}
```

## Android 端配置

修改 `ChatRepositoryImpl.kt` 中的 API URL：

```kotlin
// 本地服务器（开发和测试）
private const val API_URL = "http://10.0.2.2:8000/chat"  // Android 模拟器

// 如果是真机，使用电脑 IP
// private const val API_URL = "http://192.168.1.xxx:8000/chat"
```

## 网络说明

- **模拟器**：使用 `10.0.2.2` 访问主机
- **真机**：使用电脑的局域网 IP（确保手机和电脑在同一 WiFi）

## 生产部署

如果要部署到公网服务器：

1. 使用 Gunicorn/Uvicorn 部署
2. 配置 Nginx 反向代理
3. 启用 HTTPS
4. 添加 API 认证（API Key / JWT）

