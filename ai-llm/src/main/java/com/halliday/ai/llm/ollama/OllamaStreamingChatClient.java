package com.halliday.ai.llm.ollama;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.halliday.ai.common.conversation.ConversationMessage;
import com.halliday.ai.common.conversation.ConversationRole;
import com.halliday.ai.common.exception.AiServiceException;
import com.halliday.ai.llm.config.OllamaLlmProperties;
import com.halliday.ai.llm.core.StreamingLanguageModelClient;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class OllamaStreamingChatClient implements StreamingLanguageModelClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OllamaLlmProperties properties;
    private final ObjectMapper mapper;
    private final OkHttpClient client;

    public OllamaStreamingChatClient(OllamaLlmProperties properties, ObjectMapper mapper) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.client = new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
    }

//    @Override
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Override
    public void streamChat(List<ConversationMessage> history,
                           Consumer<String> onDelta,
                           Consumer<StreamingLanguageModelClient.Completion> onComplete) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", properties.getModel());
            payload.put("temperature", properties.getTemperature());
            payload.put("top_p", properties.getTopP());
            payload.put("stream", true);
            Map<String, Object> streamOptions = new HashMap<>();
            streamOptions.put("include_usage", true);
            payload.put("stream_options", streamOptions);
            payload.put("messages", serializeMessages(history));

            RequestBody body = RequestBody.create(mapper.writeValueAsBytes(payload), JSON);
            Request.Builder builder = new Request.Builder().url(properties.getBaseUrl()).post(body);
            if (StringUtils.hasText(properties.getApiKey())) {
                builder.addHeader("Authorization", "Bearer " + properties.getApiKey());
            }

            try (Response response = client.newCall(builder.build()).execute()) {
                if (!response.isSuccessful()) {
                    throw new AiServiceException("LLM streaming failed with status " + response.code());
                }
                StringBuilder complete = new StringBuilder();
                Map<String, Object> metadata = new LinkedHashMap<>();
                List<String> rawEvents = new ArrayList<>();
                metadata.put("events", rawEvents);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(response.body()).byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) {
                            continue;
                        }
                        String json = line.startsWith("data:") ? line.substring(5).trim() : line.trim();
                        if ("[DONE]".equals(json)) {
                            break;
                        }
                        rawEvents.add(json);
                        JsonNode root = mapper.readTree(json);
                        JsonNode choices = root.path("choices");
                        if (choices.isArray() && !choices.isEmpty()) {
                            JsonNode delta = choices.get(0).path("delta");
                            String content = delta.path("content").asText("");
                            if (StringUtils.hasText(content)) {
                                complete.append(content);
                                onDelta.accept(content);
                            }
                            String finish = choices.get(0).path("finish_reason").asText("");
                            if (StringUtils.hasText(finish)) {
                                metadata.put("finish_reason", finish);
                            }
                        }
                        if (root.hasNonNull("usage")) {
                            metadata.put("usage", mapper.convertValue(root.get("usage"), MAP_TYPE));
                        }
                        if (root.hasNonNull("created")) {
                            metadata.put("created", root.get("created").asLong());
                        }
                        if (root.hasNonNull("id")) {
                            metadata.putIfAbsent("id", root.get("id").asText());
                        }
                        if (root.hasNonNull("model")) {
                            metadata.putIfAbsent("model", root.get("model").asText());
                        }
                        if (root.path("done").asBoolean(false)) {
                            metadata.put("done", true);
                            metadata.put("done_reason", root.path("done_reason").asText(""));
                            if (root.hasNonNull("total_duration")) {
                                metadata.put("total_duration", root.get("total_duration").asLong());
                            }
                            if (root.hasNonNull("load_duration")) {
                                metadata.put("load_duration", root.get("load_duration").asLong());
                            }
                            if (root.hasNonNull("prompt_eval_count")) {
                                metadata.put("prompt_eval_count", root.get("prompt_eval_count").asLong());
                            }
                            if (root.hasNonNull("prompt_eval_duration")) {
                                metadata.put("prompt_eval_duration", root.get("prompt_eval_duration").asLong());
                            }
                            if (root.hasNonNull("eval_count")) {
                                metadata.put("eval_count", root.get("eval_count").asLong());
                            }
                            if (root.hasNonNull("eval_duration")) {
                                metadata.put("eval_duration", root.get("eval_duration").asLong());
                            }
                            break;
                        }
                    }
                }
                onComplete.accept(new StreamingLanguageModelClient.Completion(complete.toString(), metadata));
            }
        } catch (IOException ex) {
            throw new AiServiceException("Failed to stream LLM response", ex);
        }
    }

    private List<Map<String, String>> serializeMessages(List<ConversationMessage> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(properties.getSystemPrompt())) {
            messages.add(messageOf(ConversationRole.SYSTEM, properties.getSystemPrompt()));
        }
        if (history != null) {
            for (ConversationMessage message : history) {
                if (message == null || !StringUtils.hasText(message.content())) {
                    continue;
                }
                messages.add(messageOf(message.role(), message.content()));
            }
        }
        return messages;
    }

    private Map<String, String> messageOf(ConversationRole role, String content) {
        Map<String, String> map = new HashMap<>();
        map.put("role", switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        });
        map.put("content", content);
        return map;
    }
}
