# ai-stt 模块说明

`ai-stt` 模块提供语音识别能力，包含统一接口、Sherpa 与 Azure 的具体实现，并在关键流程输出中文日志。

## 配置与接口

- `com.halliday.ai.stt.config.SherpaSttProperties`
  - 通过日志记录 WebSocket 地址、帧大小以及超时时间。
- `com.halliday.ai.stt.config.AzureSttProperties`
  - Getter/Setter 记录配置变更，`hasCredentials()` 会输出凭据完整性。
- `com.halliday.ai.stt.core.SpeechToTextClient`
  - 同步转写接口，接口加载时输出调试信息。
- `com.halliday.ai.stt.core.StreamingSpeechToTextClient`
  - 流式识别接口，接口加载即输出调试日志。

## Sherpa 实现

- `SherpaSpeechToTextClient`
  - 使用 WebSocket 发送 PCM 音频并接收 JSON 结果。
  - 构造、发送音频、等待结果及解析最终文本均带有详细日志，WebSocket 回调失败时记录异常信息。
- `SherpaStreamingSpeechToTextClient`
  - 支持流式识别，线程池命名、缓冲区大小、回调事件都会写入日志。

## Azure 实现

- `AzureStreamingSpeechToTextClient`
  - 基于 Azure Speech SDK 实现流式识别，初始化、推流、事件回调全部使用中文日志描述当前状态。
  - `safeAccept` 方法在回调前输出文本长度和是否结束的信息，便于确认回调顺序。

## 关键逻辑

- 所有音频推流均在日志中输出采样率、通道与位深，确保后端参数一致。
- 发生异常时会构造额外元数据写入日志，便于追溯是哪一个提供者或线程出现问题。
