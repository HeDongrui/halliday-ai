# halliday-ai

## 项目概览

`halliday-ai` 是一个基于 **Java 21 / Spring Boot 3** 的多模块编排工程，实现离线 REST 语音识别与实时 WebSocket 音频编排能力。编排链路如下：

```
客户端 PCM → STT (Sherpa) → 文本 → LLM (Chat Completions) → 回复文本 → TTS (Kokoro) → PCM 返回
```

REST 接口返回 `text/plain`，便于命令行工具直接查看。WebSocket 端点 `/ai/stream` 支持 PCM16LE 流式上行，并按 JSON + 二进制帧推送识别文本与合成语音。

## 模块结构

```
halliday-ai/
├─ ai-common/       # DTO、工具与指标常量
├─ ai-stt/          # STT 统一接口与 Sherpa WebSocket 实现
├─ ai-tts/          # TTS 统一接口与 Kokoro HTTP/WS 实现
├─ ai-llm/          # LLM 统一接口与 Chat Completions 实现
└─ ai-orchestrator/ # Spring Boot 应用，提供 REST 与 WebSocket 编排
```

## 快速开始

1. **准备依赖服务**（需提前启动）：
   - Sherpa VoiceAPI WebSocket（示例：`ws://127.0.0.1:8000/asr?samplerate=16000`）。
   - 任意兼容 OpenAI Chat Completions 的服务（示例：`http://127.0.0.1:3000/v1/chat/completions`，模型 `llama3.1`）。
   - Kokoro FastAPI（HTTP `http://127.0.0.1:8880/v1/audio/speech`，WebSocket `ws://127.0.0.1:8880/v1/ws/tts/stream`）。

2. **编译打包**（离线环境可预先下载依赖，示例命令）
   ```bash
   mvn -q -DskipTests package
   ```
   > 在本环境中访问 Maven 中央仓库受限（403），如遇报错请按需配置私有仓库或手动缓存依赖。

3. **启动服务**
   ```bash
   java -jar ai-orchestrator/target/ai-orchestrator-0.1.0-SNAPSHOT.jar
   ```

4. **离线识别示例**（REST，返回纯文本）
   ```bash
   curl "http://127.0.0.1:9099/orchestrator/stt/offline?wavUrl=https://example.com/demo.wav"
   ```

5. **实时流式测试**
   - 使用提供的 `TestWsPusher` 将本地 WAV 推送至编排服务，并收集返回的 PCM：
     ```bash
     java -cp ai-orchestrator/target/ai-orchestrator-0.1.0-SNAPSHOT.jar \
       com.halliday.ai.orchestrator.tool.TestWsPusher ./samples/demo.wav ws://127.0.0.1:9099/ai/stream
     ffplay -f s16le -ar 16000 -ac 1 out.pcm
     ```
   - WebSocket 下行示例：
     ```json
     {"type":"interim_text","text":"你好","idx":0}
     {"type":"final_text","text":"你好。","idx":0}
     # 后续为二进制 PCM 帧
     {"type":"done","idx":0}
     ```

## 配置说明

`ai-orchestrator/src/main/resources/application.yml` 提供默认配置，可通过环境变量或外部配置文件覆盖：

- `ai.stt.*`：Sherpa WebSocket 地址、帧大小、缓冲区等。
- `ai.llm.*`：Chat Completions 接口地址、模型、API Key 与采样参数。
- `ai.tts.*`：Kokoro WebSocket/HTTP 地址、默认音色与格式。
- `management.endpoints.web.exposure.include`：暴露 `health`、`info`、`prometheus`。

## 关键实现细节

- **并发**：
  - STT、LLM、TTS 均使用独立线程池异步执行，避免阻塞 Netty I/O 线程。
  - WebSocket 侧采用 `PipedInputStream` + 可配置缓冲区解耦上游推流与 STT 消费。

- **流式编排**：
  - STT interim → `interim_text`，final → `final_text`，并触发 LLM。
  - LLM NDJSON 增量在句末切分（`。！？.!?`），逐句调用 TTS，音频以二进制帧推送。
  - 每段播报完成后发送 `done`，异常时返回结构化 `error`。

- **重试与超时**：
  - OkHttp 统一配置连接/读取超时（STT 10 分钟、LLM/TTS 5 分钟）。
  - 重试工具类 `RetryUtils` 提供指数退避策略，可在扩展实现中使用。

- **监控与日志**：
  - Micrometer + Prometheus，指标包括 `stt_segments_total`、`llm_tokens_total`、`tts_bytes_streamed_total`。
  - 关键链路输出中文日志，便于运维排查。

## 测试

- `ai-common`：`TextUtilsTest` 覆盖句末判断逻辑。
- `ai-llm`：`OllamaLlmServiceTest` 验证 Chat Completions 调用与消息体构造。
- `ai-orchestrator`：`AiOrchestratorIntegrationTest` 通过 MockWebServer 模拟 LLM/TTS，验证从 STT 最终触发音频回推的全链路。

执行全部测试：
```bash
mvn test
```
（如网络受限，请预先准备依赖或使用离线仓库。）

## 目录清单

详见仓库文件树；核心源码位于 `ai-*/src/main/java`，测试位于各模块 `src/test/java`，编排配置与示例脚本位于 `ai-orchestrator` 模块。

## 备注

- 项目遵循生产级标准，所有核心类与方法均附带中文注释。
- 若需自定义声学模型、语言模型或音色，可直接修改对应模块配置类或在外部配置文件中覆盖。
- REST 接口默认返回 `text/plain`，如需 JSON，可在 `OfflineController` 调整 `produces` 配置。
