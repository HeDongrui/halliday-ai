# ai-orchestrator 模块说明

`ai-orchestrator` 模块负责编排语音识别、语言模型与语音合成之间的协作，并对外提供 REST 与 WebSocket 两种接口。

## 启动与配置

- `com.halliday.ai.orchestrator.AiApplication`
  - Spring Boot 应用入口，启动和完成时输出中文日志。
- `com.halliday.ai.orchestrator.config.AiServiceConfiguration`
  - 装配 STT、LLM、TTS 各类 Bean，创建时记录服务名称与校验流程。
- `com.halliday.ai.orchestrator.config.StartupInfoLogger`
  - 在应用就绪后输出 REST 接口访问地址，并在上下文不符合预期时打印警告。
- `com.halliday.ai.orchestrator.config.WebSocketConfiguration`
  - 注册 `/ws/conversation` 端点并输出注册日志。

## REST API

- `com.halliday.ai.orchestrator.web.ConversationController`
  - `converse()` 接收 JSON 请求，构建 `ConversationInput` 后交给服务层处理。
  - 内部记录解析文本、音频格式、历史消息的详细日志，遇到无效 Base64 或角色时抛出带中文说明的异常。
- `com.halliday.ai.orchestrator.service.ConversationService`
  - 串联语音转写、LLM 回复与语音合成，关键步骤（解析用户文本、模型回复、生成音频）均输出日志。

## WebSocket 流程

- `com.halliday.ai.orchestrator.web.StreamingConversationHandler`
  - 管理实时对话会话，支持多种 STT 提供者切换。
  - 处理 `start`/`audio`/`stop` 指令，按阶段发送调试事件与语音片段。
  - 出现异常（如 STT/TTS 失败、无法发送消息）时，会输出中文警告并向客户端返回错误事件。

## 关键逻辑

- 会话上下文 `SessionContext` 在 `@PreDestroy` 阶段集中释放资源，防止线程与流未关闭。
- 所有外部服务调用（STT、LLM、TTS）前后均记录耗时与配置，便于排查性能瓶颈。
