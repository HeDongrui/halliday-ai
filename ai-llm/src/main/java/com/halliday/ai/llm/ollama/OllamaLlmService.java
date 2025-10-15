package com.halliday.ai.llm.ollama;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 基于 Chat Completions 兼容接口的大模型调用实现。
 */
@Slf4j
public class OllamaLlmService implements LlmService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
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
            payload.put("stream", false);
            payload.put("temperature", properties.getTemperature());
            payload.put("top_p", properties.getTopP());
            payload.put("messages", buildMessages(history));
            byte[] bodyBytes = objectMapper.writeValueAsBytes(payload);
            Request.Builder requestBuilder = new Request.Builder()
                    .url(properties.getBaseUrl())
                    .post(RequestBody.create(bodyBytes, JSON))
                    .addHeader("Content-Type", "application/json");
            if (StringUtils.hasText(properties.getApiKey())) {
                requestBuilder.addHeader("Authorization", "Bearer " + properties.getApiKey());
            }
            try (Response response = client.newCall(requestBuilder.build()).execute()) {
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("调用 LLM 失败，状态码=" + response.code());
                }
                String responseText = response.body() != null ? response.body().string() : "";
                if (!StringUtils.hasText(responseText)) {
                    throw new IllegalStateException("LLM 返回内容为空");
                }
                String content = extractContent(responseText);
                if (StringUtils.hasText(content)) {
                    tokenCounter.increment(content.codePointCount(0, content.length()));
                    onDelta.accept(content);
                }
                onDone.accept(content);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("调用 Chat Completions 失败", ex);
        }
    }

    private List<Map<String, String>> buildMessages(List<LlmMessage> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(properties.getSystemPrompt())) {
            Map<String, String> system = new HashMap<>();
            system.put("role", "system");
            system.put("content", properties.getSystemPrompt());
            messages.add(system);
        }
        if (history != null) {
            for (LlmMessage message : history) {
                if (message == null || !StringUtils.hasText(message.getContent())) {
                    continue;
                }
                Map<String, String> map = new HashMap<>();
                map.put("role", StringUtils.hasText(message.getRole()) ? message.getRole() : "user");
                map.put("content", message.getContent());
                messages.add(map);
            }
        }
        return messages;
    }

    private String extractContent(String responseText) throws IOException {
        JsonNode root = objectMapper.readTree(responseText);
        JsonNode choicesNode = root.path("choices");
        if (!choicesNode.isArray() || choicesNode.isEmpty()) {
            throw new IllegalStateException("LLM 返回缺少 choices 字段");
        }
        JsonNode messageNode = choicesNode.get(0).path("message");
        return messageNode.path("content").asText("");
    }
}
