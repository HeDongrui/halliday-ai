package com.halliday.ai.llm.core;

import com.halliday.ai.common.dto.LlmMessage;
import java.util.List;
import java.util.function.Consumer;

/**
 * 大语言模型服务接口，定义流式对话能力。
 */
public interface LlmService {

    /**
     * 以流式方式进行聊天，逐步输出模型增量内容。
     *
     * @param history 历史上下文消息
     * @param onDelta 收到增量 token 时的回调
     * @param onDone  所有数据完成时的回调
     */
    void streamChat(List<LlmMessage> history, Consumer<String> onDelta, Consumer<String> onDone);
}
