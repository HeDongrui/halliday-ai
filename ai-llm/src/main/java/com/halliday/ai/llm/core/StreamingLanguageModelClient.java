package com.halliday.ai.llm.core;

import com.halliday.ai.common.conversation.ConversationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface StreamingLanguageModelClient {

    Logger log = LoggerFactory.getLogger(StreamingLanguageModelClient.class);

    static void init() {
        log.debug("【大模型接口】StreamingLanguageModelClient 接口已加载，准备处理流式对话");
    }

    void streamChat(List<ConversationMessage> history,
                    Consumer<String> onDelta,
                    Consumer<Completion> onComplete);

    final class Completion {
        private static final Logger log = LoggerFactory.getLogger(Completion.class);

        private final String text;
        private final Map<String, Object> metadata;

        public Completion(String text, Map<String, Object> metadata) {
            log.debug("【流式补全】创建 Completion 对象，文本长度：{}，元数据是否为空：{}",
                    text == null ? 0 : text.length(), metadata == null || metadata.isEmpty());
            this.text = text;
            this.metadata = metadata;
        }

        public String text() {
            log.debug("【流式补全】获取完整文本，长度：{}", text == null ? 0 : text.length());
            return text;
        }

        public Map<String, Object> metadata() {
            log.debug("【流式补全】获取补全元数据，是否为空：{}", metadata == null || metadata.isEmpty());
            return metadata;
        }
    }
}
