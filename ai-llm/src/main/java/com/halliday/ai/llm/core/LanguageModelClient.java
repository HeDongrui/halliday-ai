package com.halliday.ai.llm.core;

import com.halliday.ai.common.conversation.ConversationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public interface LanguageModelClient {

    Logger log = LoggerFactory.getLogger(LanguageModelClient.class);

    static {
        log.debug("【大模型接口】LanguageModelClient 接口已加载，准备处理对话请求");
    }

    /**
     * Produce an assistant reply given the existing conversation history.
     *
     * @param messages chronological conversation messages
     * @return assistant reply text
     */
    String chat(List<ConversationMessage> messages);
}
