package com.halliday.ai.llm.core;

import com.halliday.ai.common.conversation.ConversationMessage;

import java.util.List;
import java.util.function.Consumer;

public interface StreamingLanguageModelClient {

    void streamChat(List<ConversationMessage> history, Consumer<String> onDelta, Consumer<String> onComplete);
}
