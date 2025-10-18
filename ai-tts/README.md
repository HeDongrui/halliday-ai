# ai-tts 模块说明

`ai-tts` 模块负责文本转语音能力，当前包含 Kokoro 的同步与流式客户端，并提供统一接口。

## 接口与配置

- `com.halliday.ai.tts.core.TextToSpeechClient`
  - 定义阻塞式合成接口，接口加载时输出调试日志。
- `com.halliday.ai.tts.core.StreamingTextToSpeechClient`
  - 定义流式合成接口，接口加载时输出调试日志。
- `com.halliday.ai.tts.config.KokoroTtsProperties`
  - 记录 HTTP/WS 地址、音频格式及超时设置，所有 getter/setter 输出当前值。

## Kokoro 实现

- `KokoroTextToSpeechClient`
  - 基于 HTTP 的阻塞式合成实现，构造时记录目标服务、输出格式等信息。
  - `synthesize()` 会详细记录请求载荷、响应状态及音频字节数，异常时抛出带日志的 `AiServiceException`。
- `KokoroStreamingTextToSpeechClient`
  - 基于 WebSocket 的流式实现，初始化时输出 WS 地址与缓冲区大小。
  - 在连接建立、消息处理、回退逻辑中均提供中文日志，便于还原流式推送过程。

## 关键逻辑

- 所有合成方法在检测到文本为空时都会记录错误并拒绝执行，保证下游不会收到非法请求。
- 流式客户端在解析 JSON 消息失败时会记录警告并尝试降级，将纯文本片段转换为字节下发。
