package com.halliday.ai.llm.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Ollama 流式响应中的单行数据结构，兼容 NDJSON 结构。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaChatChunk {

    /**
     * 嵌套消息对象，包含角色与增量内容。
     */
    @JsonProperty("message")
    private OllamaMessage message;

    /**
     * 标识该流是否已经结束。
     */
    @JsonProperty("done")
    private boolean done;

    /**
     * 如果响应结束，给出结束原因。
     */
    @JsonProperty("done_reason")
    private String doneReason;

    /**
     * 嵌套消息内容。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OllamaMessage {
        /**
         * 消息角色。 
         */
        private String role;
        /**
         * 增量文本内容。
         */
        private String content;
    }
}
