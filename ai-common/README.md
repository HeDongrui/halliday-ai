# ai-common 模块说明

`ai-common` 模块提供跨服务共享的基础数据结构与异常定义，所有类均配备中文日志，便于排查问题。

## 主要类与职责

- `com.halliday.ai.common.audio.AudioFormat`
  - 描述 PCM 音频格式的不可变记录类型，构造时会校验采样率、声道和位深是否合法。
  - `frameSizeBytes()` 计算单帧字节数，并输出调试日志记录计算结果。
- `com.halliday.ai.common.conversation.ConversationInput`
  - 封装一次对话请求的历史消息、音频和文本覆盖。构建器会记录每一步的设置与校验结果。
  - `audioAsBase64()` 与 `textOverride()` 等方法会输出调试日志，方便定位输入缺失问题。
- `com.halliday.ai.common.conversation.ConversationMessage`
  - 使用 Java `record` 表示角色与内容的组合，静态工厂方法会记录消息长度。
- `com.halliday.ai.common.conversation.ConversationResult`
  - 包含用户文本、模型回复、历史记录和可选的语音。构建器在校验必填字段失败时会记录错误日志。
- `com.halliday.ai.common.conversation.ConversationRole`
  - 定义系统、用户、助手三个角色，枚举在初始化时输出可用角色列表。
- `com.halliday.ai.common.exception.AiServiceException`
  - 统一的运行时异常，构造函数会记录错误信息和根因。
- `com.halliday.ai.common.spi.NamedService`
  - 约定具名服务接口，`displayName()` 默认实现会记录生成的展示名称。
- `com.halliday.ai.common.stt.SttResult`
  - 表示语音识别结果的不可变对象。构建器、访问器均带有日志，指明文本长度、索引等信息。

## 关键逻辑

- 所有 Builder 在 `build()` 时进行必要字段校验并记录中文日志，确保调用链中出现不合法数据时可以快速定位。
- 音频相关类统一在日志中输出采样率、声道和位深等关键参数，便于确认音频上下游约束是否一致。
