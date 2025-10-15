# halliday-ai

## 项目概览

`halliday-ai` 基于 **Java 21 / Spring Boot 3**，提供一个最小可用的“语音对话”后端：

```
语音（PCM16） → Sherpa ASR → 文本 → Chat Completions → 答案文本 → Kokoro TTS → 语音（PCM16）
```

项目专注于**单轮/多轮语音对话**的基础能力，所有交互通过一个 REST 接口 `/api/conversation` 完成：

1. 客户端上传音频（Base64）或直接提供文本。
2. 服务端调用 Sherpa 完成语音识别。
3. 将对话历史交给任意兼容 OpenAI Chat Completions 的 LLM。
4. 通过 Kokoro TTS 返回语音回复，同时以 Base64 编码的方式返还给调用方。

## 模块结构

```
halliday-ai/
├─ ai-common/       # 音频格式与对话 DTO、通用异常
├─ ai-stt/          # 语音识别客户端（Sherpa WebSocket 简化封装）
├─ ai-llm/          # LLM 客户端（Chat Completions 封装）
├─ ai-tts/          # 文本转语音客户端（Kokoro HTTP 封装）
└─ ai-orchestrator/ # Spring Boot 应用，对外暴露 REST 接口
```

## 快速开始

1. **准备依赖服务**（需提前启动）：
   - Sherpa VoiceAPI WebSocket：例如 `ws://127.0.0.1:8000/asr?samplerate=16000`
   - 任意兼容 OpenAI Chat Completions 的服务：例如 `http://127.0.0.1:3000/v1/chat/completions`
   - Kokoro FastAPI HTTP 接口：例如 `http://127.0.0.1:8880/v1/audio/speech`

2. **编译打包**（离线环境可预先下载依赖，示例命令）
   ```bash
   mvn -q -DskipTests package
   ```
   > 在本环境中访问 Maven 中央仓库受限（403），如遇报错请按需配置私有仓库或手动缓存依赖。

3. **启动服务**
   ```bash
   java -jar ai-orchestrator/target/ai-orchestrator-0.1.0-SNAPSHOT.jar
   ```

4. **发起一次语音对话**

   将 16kHz 单声道 PCM16 音频编码为 Base64，构造请求：

   ```bash
   curl -X POST "http://127.0.0.1:9099/api/conversation" \
        -H "Content-Type: application/json" \
        -d '{
              "audioBase64": "<BASE64_PCM>",
              "sampleRate": 16000,
              "channels": 1,
              "bitDepth": 16,
              "history": []
            }'
   ```

   返回内容示例：

   ```json
   {
     "userText": "你好",
     "assistantText": "你好，很高兴见到你！",
     "assistantAudioBase64": "<BASE64_PCM>",
     "sampleRate": 16000,
     "channels": 1,
     "bitDepth": 16,
     "history": [
       {"role": "user", "content": "你好"},
       {"role": "assistant", "content": "你好，很高兴见到你！"}
     ]
   }
   ```

   下次调用时，可直接把 `history` 原样带回，实现多轮语音对话。如果没有音频，亦可仅发送 `text` 字段从而进行纯文本对话。

## 配置说明

`ai-orchestrator/src/main/resources/application.yml` 提供可覆盖配置：

- `ai.stt`：Sherpa WebSocket 地址、上传帧尺寸、结果等待超时。
- `ai.llm`：Chat Completions 地址、模型、API Key、采样参数与系统提示词。
- `ai.tts`：Kokoro HTTP 地址、默认音色、输出格式及采样参数。

## 关键实现细节

- **单接口编排**：`ConversationService` 顺序调用 STT→LLM→TTS，统一封装在 `ConversationResult` 中返回。
- **解耦客户端**：每个后端服务使用独立模块封装，核心接口仅暴露同步方法，便于未来替换实现。
- **语义历史**：对话历史在服务端/客户端之间以结构化形式传递，可轻松扩展多轮对话策略。

## 测试

- `ai-common`：`ConversationInputTest` 覆盖构建器约束。
- `ai-llm`：`OllamaChatClientTest` 使用 MockWebServer 校验 Chat Completions 协议。
- `ai-orchestrator`：`ConversationServiceTest` 与 `ConversationControllerTest` 验证编排逻辑和 REST 映射。

执行全部测试：
```bash
mvn test
```
（如网络受限，请预先准备依赖或使用离线仓库。）

## 目录清单

详见仓库文件树；核心源码位于 `ai-*/src/main/java`，测试位于各模块 `src/test/java`，编排配置与示例脚本位于 `ai-orchestrator` 模块。

## 备注

- 默认假定输入/输出均为 16kHz、单声道、16-bit PCM little-endian，如需其它格式，可调整配置或扩展转换逻辑。
- 真实环境下建议引入鉴权、速率限制、日志脱敏等增强能力，本项目聚焦核心链路示例。
