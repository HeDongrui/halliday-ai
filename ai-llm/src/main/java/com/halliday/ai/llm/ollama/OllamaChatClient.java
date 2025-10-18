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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class OllamaChatClient implements LanguageModelClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaChatClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OllamaLlmProperties properties;
    private final ObjectMapper mapper;
    private final OkHttpClient client;

    public OllamaChatClient(OllamaLlmProperties properties, ObjectMapper mapper) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        log.debug("【Ollama 客户端】初始化，目标地址：{}，模型：{}", properties.getBaseUrl(), properties.getModel());
        this.client = buildClient(properties);
    }

    private OkHttpClient buildClient(OllamaLlmProperties properties) {
        log.debug("【Ollama 客户端】构建 OkHttpClient，连接超时：{}ms，读取超时：{}ms",
                properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
        return new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public String chat(List<ConversationMessage> messages) {
        log.info("【Ollama 客户端】开始请求对话接口，历史消息数量：{}", messages == null ? 0 : messages.size());
        try {
            byte[] payload = mapper.writeValueAsBytes(buildPayload(messages));
            Request.Builder builder = new Request.Builder()
                    .url(properties.getBaseUrl())
                    .post(RequestBody.create(payload, JSON));
            if (StringUtils.hasText(properties.getApiKey())) {
                log.debug("【Ollama 客户端】使用 API Key 进行鉴权");
                builder.addHeader("Authorization", "Bearer " + properties.getApiKey());
            }
            try (Response response = client.newCall(builder.build()).execute()) {
                log.debug("【Ollama 客户端】收到响应，HTTP 状态码：{}", response.code());
                if (!response.isSuccessful()) {
                    log.error("【Ollama 客户端】调用失败，状态码：{}", response.code());
                    throw new AiServiceException("LLM request failed with status " + response.code());
                }
                JsonNode root = mapper.readTree(Objects.requireNonNull(response.body()).string());
                String content = extractContent(root);
                log.info("【Ollama 客户端】对话完成，返回文本长度：{}", content.length());
                return content;
            }
        } catch (IOException ex) {
            log.error("【Ollama 客户端】调用接口发生 IO 异常", ex);
            throw new AiServiceException("Failed to call LLM", ex);
        }
    }

    private Map<String, Object> buildPayload(List<ConversationMessage> messages) {
        log.debug("【Ollama 客户端】开始构建请求载荷");
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", properties.getModel());
        payload.put("temperature", properties.getTemperature());
        payload.put("top_p", properties.getTopP());
        payload.put("stream", false);
        payload.put("messages", serializeMessages(messages));
        log.debug("【Ollama 客户端】请求载荷构建完成");
        return payload;
    }

    private List<Map<String, String>> serializeMessages(List<ConversationMessage> messages) {
        log.debug("【Ollama 客户端】开始序列化消息，原始数量：{}", messages == null ? 0 : messages.size());
        List<Map<String, String>> serialized = new ArrayList<>();
        if (StringUtils.hasText(properties.getSystemPrompt())) {
            log.debug("【Ollama 客户端】添加系统提示词");
            serialized.add(messageOf(ConversationRole.SYSTEM, properties.getSystemPrompt()));
        }
        if (messages != null) {
            for (ConversationMessage message : messages) {
                if (message == null || !StringUtils.hasText(message.content())) {
                    log.debug("【Ollama 客户端】跳过空消息或无内容消息");
                    continue;
                }
                serialized.add(messageOf(message.role(), message.content()));
            }
        }
        log.debug("【Ollama 客户端】消息序列化完成，实际数量：{}", serialized.size());
        return serialized;
    }

    private Map<String, String> messageOf(ConversationRole role, String content) {
        log.debug("【Ollama 客户端】转换消息，角色：{}，内容长度：{}", role, content == null ? 0 : content.length());
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
        log.debug("【Ollama 客户端】解析响应体");
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            log.error("【Ollama 客户端】响应缺少 choices 字段");
            throw new AiServiceException("LLM response missing 'choices'");
        }
        JsonNode message = choices.get(0).path("message");
        String content = message.path("content").asText("");
        if (!StringUtils.hasText(content)) {
            log.error("【Ollama 客户端】响应缺少 message.content 字段");
            throw new AiServiceException("LLM response missing message content");
        }
        String result = content.trim();
        log.debug("【Ollama 客户端】解析完成，文本长度：{}", result.length());
        return result;
    }
}
