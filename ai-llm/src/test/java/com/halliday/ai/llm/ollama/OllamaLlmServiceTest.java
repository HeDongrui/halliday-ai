package com.halliday.ai.llm.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.halliday.ai.common.dto.LlmMessage;
import com.halliday.ai.llm.config.OllamaLlmProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单元测试：验证 Chat Completions 调用逻辑。
 */
class OllamaLlmServiceTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void shouldCallChatCompletionsEndpoint() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"你好。\"}}]}")
                .addHeader("Content-Type", "application/json"));

        OllamaLlmProperties properties = new OllamaLlmProperties();
        properties.setBaseUrl(server.url("/v1/chat/completions").toString());
        properties.setApiKey("dummy-key");
        properties.setModel("llama3.1");
        properties.setSystemPrompt("系统提示");

        OllamaLlmService service = new OllamaLlmService(properties, new ObjectMapper(), new SimpleMeterRegistry());

        AtomicReference<String> delta = new AtomicReference<>();
        AtomicReference<String> done = new AtomicReference<>();
        service.streamChat(List.of(LlmMessage.builder().role("user").content("你好?").build()), delta::set, done::set);

        Assertions.assertEquals("你好。", delta.get());
        Assertions.assertEquals("你好。", done.get());

        RecordedRequest request = server.takeRequest();
        Assertions.assertEquals("POST", request.getMethod());
        Assertions.assertEquals("Bearer dummy-key", request.getHeader("Authorization"));

        String body = request.getBody().readUtf8();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode messages = mapper.readTree(body).path("messages");
        Assertions.assertEquals("system", messages.get(0).path("role").asText());
        Assertions.assertEquals("系统提示", messages.get(0).path("content").asText());
        Assertions.assertEquals("user", messages.get(1).path("role").asText());
        Assertions.assertEquals("你好?", messages.get(1).path("content").asText());
    }
}
