package com.halliday.ai.llm.core;

import com.halliday.ai.common.conversation.ConversationMessage;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface StreamingLanguageModelClient {

    void streamChat(List<ConversationMessage> history,
                    Consumer<String> onDelta,
                    Consumer<Completion> onComplete);

    final class Completion {
        private final String text;
        private final Map<String, Object> metadata;

        public Completion(String text, Map<String, Object> metadata) {
            this.text = text;
            this.metadata = metadata;
        }

        public String text() {
            return text;
        }

        public Map<String, Object> metadata() {
            return metadata;
        }
    }
}
