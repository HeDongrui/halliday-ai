package com.halliday.ai.llm.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.halliday.ai.common.dto.LlmMessage;
import com.halliday.ai.common.metrics.AiMetrics;
import com.halliday.ai.llm.config.OllamaLlmProperties;
import com.halliday.ai.llm.core.LlmService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 基于 Ollama /api/chat 接口的流式 LLM 实现。
 */
@Slf4j
public class OllamaLlmService implements LlmService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final OllamaChatResponseParser parser;
    private final OllamaLlmProperties properties;
    private final Counter tokenCounter;

    /**
     * 构造方法，初始化 HTTP 客户端与度量指标。
     *
     * @param properties   配置参数
     * @param objectMapper JSON 工具
     * @param registry     指标注册器
     */
    public OllamaLlmService(OllamaLlmProperties properties, ObjectMapper objectMapper, MeterRegistry registry) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.client = buildClient(properties);
        this.parser = new OllamaChatResponseParser(objectMapper);
        this.tokenCounter = Counter.builder(AiMetrics.METRIC_LLM_TOKENS_TOTAL)
                .description("LLM 流式输出的 token 数量")
                .register(registry);
    }

    private OkHttpClient buildClient(OllamaLlmProperties properties) {
        return new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public void streamChat(List<LlmMessage> history, Consumer<String> onDelta, Consumer<String> onDone) {
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(onDelta, "onDelta");
        Objects.requireNonNull(onDone, "onDone");
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", properties.getModel());
            payload.put("stream", true);
            payload.put("messages", history);
            byte[] bodyBytes = objectMapper.writeValueAsBytes(payload);
            Request request = new Request.Builder()
                    .url(properties.getBaseUrl() + "/api/chat")
                    .post(RequestBody.create(bodyBytes, JSON))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("调用 Ollama 失败，状态码=" + response.code());
                }
                handleStream(response.body(), onDelta, onDone);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Ollama 流式聊天失败", ex);
        }
    }

    private void handleStream(ResponseBody responseBody, Consumer<String> onDelta, Consumer<String> onDone) throws IOException {
        StringBuilder finalText = new StringBuilder();
        try (ResponseBody body = responseBody; BufferedSource source = body.source()) {
            while (!source.exhausted()) {
                String line = source.readUtf8Line();
                if (line == null) {
                    break;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                OllamaChatChunk chunk = parser.parseLine(trimmed);
                if (chunk.getMessage() != null && chunk.getMessage().getContent() != null) {
                    String delta = chunk.getMessage().getContent();
                    tokenCounter.increment(delta.codePointCount(0, delta.length()));
                    onDelta.accept(delta);
                    finalText.append(delta);
                }
                if (chunk.isDone()) {
                    onDone.accept(finalText.toString());
                    return;
                }
            }
            onDone.accept(finalText.toString());
        }
    }
}
