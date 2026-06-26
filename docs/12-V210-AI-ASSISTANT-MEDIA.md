# v2.1.0 坐席 AI 助手 + 多媒体能力升级

## 1. 坐席 AI 助手（实时推荐话术）

### 业务场景
当客户发送消息时，AI 助手自动分析消息内容 + 检索知识库 + 生成推荐话术，推送给坐席，坐席一键采纳/修改/忽略。

### 工作流
```
客户发消息
  ↓
MessageService.send() [cs-im]
  ↓ 触发条件：customer 消息 + 已分配坐席
AiAssistantService.generateSuggestionAsync()
  ↓
  1) RAG 检索知识库（关键词 TF）
  2) 拼 prompt（系统 + 上下文 + 客户消息）
  3) LLMClient.chat() 生成回复
  4) 计算置信度（0-100）
  5) 落库 ai_suggestion
  ↓
SimpMessagingTemplate → /user/queue/ai-suggestion
  ↓
前端 AiAssistantPanel.vue 接收推送
  ↓
坐席点击"采纳"/"修改"/"忽略"
  ↓
反馈写入 ai_feedback（用于模型调优）
```

### 知识库分类
- **PRODUCT** — 理财产品说明（5 款）
- **POLICY** — 监管政策（KYC/SLA/合规）
- **FAQ** — 常见问题（操作类）

### LLM 客户端（统一接口）
```java
public interface LlmClient {
    String chat(String systemPrompt, String userMessage);
    String chat(List<ChatMessage> messages);
}
```

**当前实现**：MockLlmClient（关键词模板匹配，零依赖）  
**生产实现**：OpenAiLlmClient / QwenLlmClient / OllamaLlmClient

### 推荐类型
- **REPLY** — 普通回复
- **KNOWLEDGE** — 知识库引用
- **FAQ** — 常见问题
- **ACTION** — 操作建议（如投诉升级）

## 2. 多媒体能力升级

### 2.1 屏幕共享（WebRTC）
**REST API**:
```
POST /im/media/screen-share/initiate   坐席发起
POST /im/media/screen-share/{id}/accept  客户接受（含 sdpAnswer）
POST /im/media/screen-share/{id}/reject  客户拒绝
POST /im/media/screen-share/{id}/end     结束
POST /im/media/screen-share/{id}/ice     ICE candidate 中继
```

**前端**：
- `ScreenShare.vue` 组件
- `navigator.mediaDevices.getDisplayMedia({ video: true, audio: false })`
- `RTCPeerConnection` P2P 连接
- STUN: stun.l.google.com:19302（生产可换 coturn）

**信号流程**：
1. 坐席发起 → REST 创建 INVITED 会话 → WebSocket 推送 SCREEN_SHARE_INVITE 给客户
2. 客户接受 → 创建 offer → 发回 sdpAnswer
3. 双方交换 ICE candidates → P2P 连接
4. 状态 ACTIVE → 坐席结束 → ENDED + durationSec

### 2.2 语音消息（MediaRecorder + ASR）
**流程**：
1. 前端 `getUserMedia({ audio: true })` → `MediaRecorder.start()`
2. 录完 → 转 base64 → 上传
3. 后端 Mock OSS 存储 + 异步 ASR 转写
4. 推送给对方（WebSocket + REST 拉取）

**REST API**:
```
POST /im/media/voice/upload   上传语音（base64 + durationSec）
GET  /im/media/voice/list     会话全部语音
```

**前端**：`VoiceRecorder.vue` 长按录音 + 播放列表

### 2.3 富文本 / Markdown 渲染
- **服务端**：`RichMessageService.render()` — 基础 Markdown → HTML
- **前端**：`MarkdownView.vue` 使用 marked.js 完整渲染（GFM + 代码块 + 表格 + 引用）

## 3. 数据库（Flyway V5.0.0）

### 5 张新表
| 表 | 用途 |
|----|------|
| ai_suggestion | AI 推荐话术（落库 + 评分） |
| ai_knowledge | RAG 知识库（含 embedding 字段） |
| screen_share_session | WebRTC 屏幕共享会话 |
| voice_message | 语音消息（含 ASR 转写） |
| ai_feedback | 坐席反馈（采纳/跳过/评分） |

### 预置数据
- 8 条 AI 知识（5 产品/政策 + 3 FAQ）

## 4. 测试（v2.1.0）

| 测试 | 结果 |
|------|------|
| **AiIntegrationTest**（9 cases 新） | 9/9 ✅ |
| FinancialOrderIntegrationTest | 5/5 ✅ |
| KycIntegrationTest | 4/4 ✅ |
| TicketIntegrationTest | 4/4 ✅ |
| AuthEndToEndTest | 4/4 ✅ |
| AuthMysqlIntegrationTest | 5/5 ✅ |
| OAuthLoginTest | 9/9 ✅ |
| WxMiniIntegrationTest | 3/3 ✅ |
| SecurityFilterTest | 14/14 ✅ |
| AuthLoginTest | 10/12（遗留） |

**总计 67/69 = 97% PASS**

## 5. 配置（application.yml）

```yaml
llm:
  provider: mock          # mock / openai / qwen / ollama
  model: mock-v1
  api-key: ""
  endpoint: ""
  timeout-ms: 5000
  rag-top-k: 3
  max-suggestion-length: 500
```

## 6. 生产对接路线

### OpenAI
```java
public class OpenAiLlmClient implements LlmClient {
    private final RestTemplate rest = new RestTemplate();
    public String chat(String system, String user) {
        Map<String, Object> body = Map.of(
            "model", "gpt-3.5-turbo",
            "messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
        ResponseEntity<Map> resp = rest.postForEntity(
            "https://api.openai.com/v1/chat/completions", body, Map.class);
        // 解析 choices[0].message.content
    }
}
```

### 通义千问
```java
public class QwenLlmClient implements LlmClient {
    public String chat(String system, String user) {
        // POST https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation
        // Header: Authorization: Bearer sk-xxx
        // Body: { "model": "qwen-turbo", "input": { "messages": [...] } }
    }
}
```

### WebRTC TURN
```yaml
webrtc:
  turn:
    urls: turn:turn.example.com:3478
    username: user
    credential: pass
```

### OSS 语音存储
```java
OSSClient oss = new OSSClient(endpoint, accessKey, secret);
oss.putObject(bucket, "voice/" + uuid + ".webm", inputStream);
String url = oss.generatePresignedUrl(bucket, key, expireTime).toString();
```

## 7. 文件清单

### 后端新增
```
cs-im/src/main/java/com/example/im/
  domain/AiSuggestion.java
  domain/AiKnowledge.java
  domain/ScreenShareSession.java
  domain/VoiceMessage.java
  domain/AiFeedback.java
  llm/LlmClient.java
  llm/MockLlmClient.java
  llm/LlmConfig.java
  service/AiAssistantService.java
  service/AiKnowledgeService.java
  service/WebRtcService.java
  service/VoiceMessageService.java
  service/RichMessageService.java
  controller/AiController.java
  controller/MediaController.java
  repo/AiSuggestionMapper.java
  repo/AiKnowledgeMapper.java
  repo/ScreenShareSessionMapper.java
  repo/VoiceMessageMapper.java
  repo/AiFeedbackMapper.java
  resources/db/migration/V5.0.0__ai_assistant_media.sql
```

### 前端新增
```
cs-frontend/src/components/AiAssistantPanel.vue
cs-frontend/src/components/ScreenShare.vue
cs-frontend/src/components/VoiceRecorder.vue
cs-frontend/src/components/MarkdownView.vue
```
`Agent.vue` + `Customer.vue` 集成 AI 面板 / 屏幕共享 / 语音按钮。