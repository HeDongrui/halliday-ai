package com.halliday.ai.llm.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Ollama NDJSON 解析器单元测试，验证解析与字段映射。
 */
class OllamaChatResponseParserTest {

    @Test
    void shouldParseNdjsonLine() throws Exception {
        String jsonLine = "{" +
                "\"message\":{\"role\":\"assistant\",\"content\":\"你好\"}," +
                "\"done\":false}";
        OllamaChatResponseParser parser = new OllamaChatResponseParser(new ObjectMapper());
        OllamaChatChunk chunk = parser.parseLine(jsonLine);
        Assertions.assertNotNull(chunk.getMessage());
        Assertions.assertEquals("assistant", chunk.getMessage().getRole());
        Assertions.assertEquals("你好", chunk.getMessage().getContent());
        Assertions.assertFalse(chunk.isDone());
    }
}
