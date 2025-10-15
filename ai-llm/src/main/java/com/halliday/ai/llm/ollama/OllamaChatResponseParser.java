package com.halliday.ai.llm.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/**
 * 将 Ollama NDJSON 行解析成结构化对象，便于后续处理。
 */
public class OllamaChatResponseParser {

    private final ObjectMapper objectMapper;

    /**
     * 构造方法，注入 Jackson ObjectMapper。
     *
     * @param objectMapper JSON 序列化工具
     */
    public OllamaChatResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将单行 NDJSON 文本解析为 OllamaChatChunk。
     *
     * @param line NDJSON 行内容
     * @return 解析后的数据结构
     * @throws IOException JSON 解析异常
     */
    public OllamaChatChunk parseLine(String line) throws IOException {
        return objectMapper.readValue(line, OllamaChatChunk.class);
    }
}
