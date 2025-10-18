# ai-llm 模块说明

`ai-llm` 模块封装与大语言模型交互的客户端逻辑，当前针对 Ollama 兼容接口实现同步与流式两种调用方式。

## 配置与核心接口

- `com.halliday.ai.llm.config.OllamaLlmProperties`
  - 通过 Spring Boot 属性绑定模型地址、温度、API Key 等参数，所有 getter/setter 均记录中文日志便于排查配置问题。
- `com.halliday.ai.llm.core.LanguageModelClient`
  - 定义同步对话接口，接口加载时输出调试日志提醒已准备处理请求。
- `com.halliday.ai.llm.core.StreamingLanguageModelClient`
  - 定义流式对话接口，内部 `Completion` 对象在创建、访问元数据时均输出调试日志。

## 客户端实现

- `com.halliday.ai.llm.ollama.OllamaChatClient`
  - 使用 OkHttp 同步调用 Ollama Chat Completions 接口。
  - 在构造、构建请求、序列化消息、解析响应等关键步骤输出中文日志，记录请求体大小、鉴权情况以及失败原因。
- `com.halliday.ai.llm.ollama.OllamaStreamingChatClient`
  - 通过 WebSocket/HTTP 长连接实现流式对话，详细记录请求参数、收到的事件及结束标记。
  - 针对每个原始事件写入 debug 日志，便于还原流式交互过程。

## 关键逻辑

- 所有 OkHttp 客户端均在初始化时输出连接与读取超时，方便确认网络配置。
- 流式客户端在解析 SSE 文本时对 `done`、`usage` 等字段逐一记录，出现异常将写入中文错误日志。
