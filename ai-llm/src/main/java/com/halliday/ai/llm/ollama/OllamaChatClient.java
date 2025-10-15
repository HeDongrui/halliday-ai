package com.halliday.ai.llm.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.halliday.ai.common.conversation.ConversationMessage;
import com.halliday.ai.common.conversation.ConversationRole;
import com.halliday.ai.common.exception.AiServiceException;
import com.halliday.ai.llm.config.OllamaLlmProperties;
import com.halliday.ai.llm.core.LanguageModelClient;
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

public class OllamaChatClient implements LanguageModelClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OllamaLlmProperties properties;
    private final ObjectMapper mapper;
    private final OkHttpClient client;

    public OllamaChatClient(OllamaLlmProperties properties, ObjectMapper mapper) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.client = buildClient(properties);
    }

    private OkHttpClient buildClient(OllamaLlmProperties properties) {
        return new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public String chat(List<ConversationMessage> messages) {
        try {
            byte[] payload = mapper.writeValueAsBytes(buildPayload(messages));
            Request.Builder builder = new Request.Builder()
                    .url(properties.getBaseUrl())
                    .post(RequestBody.create(payload, JSON));
            if (StringUtils.hasText(properties.getApiKey())) {
                builder.addHeader("Authorization", "Bearer " + properties.getApiKey());
            }
            try (Response response = client.newCall(builder.build()).execute()) {
                if (!response.isSuccessful()) {
                    throw new AiServiceException("LLM request failed with status " + response.code());
                }
                JsonNode root = mapper.readTree(Objects.requireNonNull(response.body()).string());
                return extractContent(root);
            }
        } catch (IOException ex) {
            throw new AiServiceException("Failed to call LLM", ex);
        }
    }

    private Map<String, Object> buildPayload(List<ConversationMessage> messages) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", properties.getModel());
        payload.put("temperature", properties.getTemperature());
        payload.put("top_p", properties.getTopP());
        payload.put("stream", false);
        payload.put("messages", serializeMessages(messages));
        return payload;
    }

    private List<Map<String, String>> serializeMessages(List<ConversationMessage> messages) {
        List<Map<String, String>> serialized = new ArrayList<>();
        if (StringUtils.hasText(properties.getSystemPrompt())) {
            serialized.add(messageOf(ConversationRole.SYSTEM, properties.getSystemPrompt()));
        }
        if (messages != null) {
            for (ConversationMessage message : messages) {
                if (message == null || !StringUtils.hasText(message.content())) {
                    continue;
                }
                serialized.add(messageOf(message.role(), message.content()));
            }
        }
        return serialized;
    }

    private Map<String, String> messageOf(ConversationRole role, String content) {
        Map<String, String> map = new HashMap<>();
        map.put("role", switch (role) {
            case SYSTEM -> "system";
            case ASSISTANT -> "assistant";
            case USER -> "user";
        });
        map.put("content", content);
        return map;
    }

    private String extractContent(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new AiServiceException("LLM response missing 'choices'");
        }
        JsonNode message = choices.get(0).path("message");
        String content = message.path("content").asText("");
        if (!StringUtils.hasText(content)) {
            throw new AiServiceException("LLM response missing message content");
        }
        return content.trim();
    }
}
