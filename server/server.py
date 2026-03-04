"""
AI Proxy Server - 本地 AI 代理服务器
处理 Android 设备的 AI 请求，转发到 AI API
"""

import os
import httpx
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
from dotenv import load_dotenv

# 加载环境变量
load_dotenv()

app = FastAPI(title="AI Proxy Server", version="1.0.0")

# 允许 CORS（Android 访问需要）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 生产环境应该限制域名
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 数据模型
class Message(BaseModel):
    role: str  # "user" or "assistant"
    content: str

class ChatRequest(BaseModel):
    message: str
    history: Optional[List[Message]] = []

class ChatResponse(BaseModel):
    response: str
    model: str
    usage: Optional[Dict[str, int]] = None

class HealthResponse(BaseModel):
    status: str
    model: str
    provider: str

# 配置
AI_PROVIDER = os.getenv("AI_PROVIDER", "kimi")
HOST = os.getenv("HOST", "0.0.0.0")
PORT = int(os.getenv("PORT", "8000"))

# AI 提供商配置
PROVIDERS = {
    "kimi": {
        "url": "https://api.moonshot.cn/v1/chat/completions",
        "key": os.getenv("KIMI_API_KEY"),
        "model": os.getenv("KIMI_MODEL", "kimi-k2.5"),
        "headers": lambda key: {
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json"
        }
    },
    "openai": {
        "url": "https://api.openai.com/v1/chat/completions",
        "key": os.getenv("OPENAI_API_KEY"),
        "model": os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
        "headers": lambda key: {
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json"
        }
    },
    "openrouter": {
        "url": "https://openrouter.ai/api/v1/chat/completions",
        "key": os.getenv("OPENROUTER_API_KEY"),
        "model": os.getenv("OPENROUTER_MODEL", "anthropic/claude-3.5-sonnet"),
        "headers": lambda key: {
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json",
            "HTTP-Referer": "http://localhost:8000",
            "X-Title": "AI Proxy Server"
        }
    }
}

def get_provider_config():
    """获取当前 AI 提供商配置"""
    if AI_PROVIDER not in PROVIDERS:
        raise HTTPException(status_code=500, detail=f"Unknown AI provider: {AI_PROVIDER}")
    
    config = PROVIDERS[AI_PROVIDER]
    if not config["key"]:
        raise HTTPException(
            status_code=500, 
            detail=f"{AI_PROVIDER.upper()}_API_KEY not configured"
        )
    return config

@app.get("/", response_model=HealthResponse)
async def root():
    """根路径 - 返回健康状态"""
    return HealthResponse(
        status="ok",
        model=PROVIDERS[AI_PROVIDER]["model"],
        provider=AI_PROVIDER
    )

@app.get("/health", response_model=HealthResponse)
async def health():
    """健康检查"""
    return HealthResponse(
        status="ok", 
        model=PROVIDERS[AI_PROVIDER]["model"],
        provider=AI_PROVIDER
    )

@app.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    """
    处理聊天请求
    - 接收用户消息和历史记录
    - 转发到 AI API
    - 返回 AI 响应
    """
    try:
        provider = get_provider_config()
        
        # 构建消息列表
        messages = []
        
        # 添加系统提示
        messages.append({
            "role": "system",
            "content": "你是一个有帮助的 AI 助手。请用中文回答问题，回答要简洁明了。"
        })
        
        # 添加历史记录
        if request.history:
            for msg in request.history:
                messages.append({
                    "role": msg.role,
                    "content": msg.content
                })
        
        # 添加当前消息
        messages.append({
            "role": "user",
            "content": request.message
        })
        
        # 构建请求体
        payload = {
            "model": provider["model"],
            "messages": messages,
            "temperature": 0.7,
            "max_tokens": 2048
        }
        
        # 发送请求到 AI API
        async with httpx.AsyncClient(timeout=60.0) as client:
            response = await client.post(
                provider["url"],
                headers=provider["headers"](provider["key"]),
                json=payload
            )
            response.raise_for_status()
            data = response.json()
        
        # 提取 AI 回复
        ai_message = data["choices"][0]["message"]["content"]
        usage = data.get("usage", {})
        
        return ChatResponse(
            response=ai_message.strip(),
            model=provider["model"],
            usage={
                "prompt_tokens": usage.get("prompt_tokens", 0),
                "completion_tokens": usage.get("completion_tokens", 0),
                "total_tokens": usage.get("total_tokens", 0)
            }
        )
        
    except httpx.HTTPError as e:
        raise HTTPException(status_code=502, detail=f"AI API error: {str(e)}")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Server error: {str(e)}")

@app.post("/chat/stream")
async def chat_stream(request: ChatRequest):
    """
    流式聊天（SSE）
    用于实时返回 AI 生成的内容
    """
    from fastapi.responses import StreamingResponse
    import json
    
    async def generate():
        try:
            provider = get_provider_config()
            
            messages = []
            messages.append({
                "role": "system",
                "content": "你是一个有帮助的 AI 助手。请用中文回答问题。"
            })
            
            if request.history:
                for msg in request.history:
                    messages.append({"role": msg.role, "content": msg.content})
            
            messages.append({"role": "user", "content": request.message})
            
            payload = {
                "model": provider["model"],
                "messages": messages,
                "temperature": 0.7,
                "stream": True
            }
            
            async with httpx.AsyncClient(timeout=60.0) as client:
                async with client.stream(
                    "POST",
                    provider["url"],
                    headers=provider["headers"](provider["key"]),
                    json=payload
                ) as response:
                    async for line in response.aiter_lines():
                        if line.startswith("data: "):
                            data = line[6:]
                            if data == "[DONE]":
                                break
                            try:
                                chunk = json.loads(data)
                                if delta := chunk["choices"][0]["delta"].get("content"):
                                    yield f"data: {json.dumps({'content': delta})}\n\n"
                            except:
                                pass
                            
        except Exception as e:
            yield f"data: {json.dumps({'error': str(e)})}\n\n"
    
    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "Connection": "keep-alive"}
    )

if __name__ == "__main__":
    import uvicorn
    
    print(f"[OK] AI Proxy Server starting...")
    print(f"   Provider: {AI_PROVIDER}")
    print(f"   Model: {PROVIDERS[AI_PROVIDER]['model']}")
    print(f"   URL: http://{HOST}:{PORT}")
    print(f"   Health: http://{HOST}:{PORT}/health")
    print(f"   Chat: http://{HOST}:{PORT}/chat")
    print()
    
    uvicorn.run(app, host=HOST, port=PORT)
