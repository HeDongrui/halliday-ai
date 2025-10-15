package com.halliday.ai.llm.core;

import com.halliday.ai.common.conversation.ConversationMessage;

import java.util.List;

public interface LanguageModelClient {

    /**
     * Produce an assistant reply given the existing conversation history.
     *
     * @param messages chronological conversation messages
     * @return assistant reply text
     */
    String chat(List<ConversationMessage> messages);
}
