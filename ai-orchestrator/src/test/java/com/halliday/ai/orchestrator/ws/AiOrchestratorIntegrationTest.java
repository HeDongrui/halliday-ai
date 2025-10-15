package com.halliday.ai.orchestrator.ws;

import com.halliday.ai.common.dto.SttResult;
import com.halliday.ai.stt.core.SttModelInfo;
import com.halliday.ai.stt.core.SttService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.WebSocketListener;
import okio.ByteString;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 集成测试：模拟 STT/LLM/TTS，验证 WebSocket 编排链路。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = AiOrchestratorIntegrationTest.MockServerInitializer.class)
class AiOrchestratorIntegrationTest {

    private static MockWebServer llmServer;
    private static MockWebServer ttsServer;

    @LocalServerPort
    private int port;

    @Test
    void shouldPipelineAudioFromSttToTts() throws Exception {
        CountDownLatch doneLatch = new CountDownLatch(1);
        List<String> textMessages = new ArrayList<>();
        List<byte[]> audioChunks = new ArrayList<>();
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.doHandshake(new AbstractWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                textMessages.add(message.getPayload());
                if (message.getPayload().contains("\"done\"")) {
                    doneLatch.countDown();
                }
            }

            @Override
            protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
                ByteBuffer buffer = message.getPayload();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                audioChunks.add(bytes);
            }
        }, new WebSocketHttpHeaders(), URI.create("ws://127.0.0.1:" + port + "/ai/stream")).get(5, TimeUnit.SECONDS);

        session.sendMessage(new BinaryMessage(ByteBuffer.wrap(new byte[640])));
        session.sendMessage(new TextMessage("{\"type\":\"eos\"}"));
        doneLatch.await(5, TimeUnit.SECONDS);
        session.close();

        Assertions.assertTrue(textMessages.stream().anyMatch(msg -> msg.contains("final_text")));
        Assertions.assertTrue(textMessages.stream().anyMatch(msg -> msg.contains("done")));
        Assertions.assertFalse(audioChunks.isEmpty());
        RecordedRequest request = llmServer.takeRequest(5, TimeUnit.SECONDS);
        Assertions.assertEquals("POST", request.getMethod());
    }

    private static void prepareLlmResponse() {
        String body = "{\"message\":{\"role\":\"assistant\",\"content\":\"你好\"},\"done\":false}\n" +
                "{\"message\":{\"role\":\"assistant\",\"content\":\"。\"},\"done\":false}\n" +
                "{\"message\":{\"role\":\"assistant\",\"content\":\"你好。\"},\"done\":true}\n";
        llmServer.enqueue(new MockResponse().setBody(body).addHeader("Content-Type", "application/json"));
    }

    private static void prepareTtsResponse() {
        ttsServer.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override
            public void onMessage(okhttp3.WebSocket webSocket, String text) {
                webSocket.send(ByteString.of(new byte[]{1, 2, 3}));
                webSocket.send("{\"event\":\"end\"}");
                webSocket.close(1000, "ok");
            }
        }));
    }

    /**
     * 使用 TestConfiguration 覆盖 STT 服务，避免依赖真实后端。
     */
    @TestConfiguration
    static class StubConfig {

        @Bean
        SttService sttService() {
            return new SttService() {
                private final ExecutorService executor = Executors.newSingleThreadExecutor();

                @Override
                public String transcribeFromUrl(String wavUrl) {
                    return "测试";
                }

                @Override
                public void streamRecognize(InputStream pcmStream, Consumer<SttResult> onResult) {
                    executor.execute(() -> {
                        try (InputStream input = pcmStream) {
                            byte[] buffer = new byte[256];
                            while (input.read(buffer) != -1) {
                                // 丢弃数据，仅用于疏通管道。
                            }
                        } catch (IOException ignored) {
                        }
                    });
                    onResult.accept(new SttResult("你好。", false, 0));
                    onResult.accept(new SttResult("你好。", true, 0));
                }

                @Override
                public SttModelInfo getModelInfo() {
                    return new SttModelInfo("stub", "unit", 16000);
                }
            };
        }
    }

    /**
     * 动态注入外部依赖地址。
     */
    static class MockServerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            try {
                llmServer = new MockWebServer();
                ttsServer = new MockWebServer();
                llmServer.start();
                ttsServer.start();
                prepareLlmResponse();
                prepareTtsResponse();
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
            TestPropertyValues.of(
                    "ai.llm.baseUrl=" + llmServer.url("/").toString(),
                    "ai.tts.wsUrl=" + ttsServer.url("/v1/ws/tts/stream").toString().replace("http", "ws"),
                    "ai.tts.httpUrl=" + ttsServer.url("/v1/audio/speech").toString()
            ).applyTo(applicationContext.getEnvironment());
        }
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (llmServer != null) {
            llmServer.shutdown();
        }
        if (ttsServer != null) {
            ttsServer.shutdown();
        }
    }
}
