package com.halliday.ai.llm.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.halliday.ai.common.conversation.ConversationMessage;
import com.halliday.ai.common.conversation.ConversationRole;
import com.halliday.ai.llm.config.OllamaLlmProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OllamaChatClientTest {

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
    void returnsAssistantReply() {
        server.enqueue(new MockResponse()
                .setBody("{\"choices\":[{\"message\":{\"content\":\"你好\"}}]}")
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        OllamaLlmProperties props = new OllamaLlmProperties();
        props.setBaseUrl(server.url("/v1/chat/completions").toString());
        props.setSystemPrompt("你是测试助手");

        OllamaChatClient client = new OllamaChatClient(props, new ObjectMapper());
        String reply = client.chat(List.of(new ConversationMessage(ConversationRole.USER, "hello")));

        assertEquals("你好", reply);
    }
}
