package com.halliday.ai.tts.kokoro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.halliday.ai.common.exception.AiServiceException;
import com.halliday.ai.tts.config.KokoroTtsProperties;
import com.halliday.ai.tts.core.StreamingTextToSpeechClient;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class KokoroStreamingTextToSpeechClient implements StreamingTextToSpeechClient {

    private static final Logger log = LoggerFactory.getLogger(KokoroStreamingTextToSpeechClient.class);

    private final KokoroTtsProperties properties;
    private final ObjectMapper mapper;
    private final OkHttpClient client;

    public KokoroStreamingTextToSpeechClient(KokoroTtsProperties properties, ObjectMapper mapper) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        log.debug("【Kokoro 流式合成】初始化客户端，WebSocket 地址：{}", properties.getWsUrl());
        this.client = new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public void streamSynthesize(String text, String voice, Consumer<byte[]> onChunk, Runnable onComplete) {
        Objects.requireNonNull(onChunk, "onChunk");
        Objects.requireNonNull(onComplete, "onComplete");
        if (!StringUtils.hasText(text)) {
            log.error("【Kokoro 流式合成】输入文本为空，拒绝合成");
            throw new IllegalArgumentException("text must not be blank");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", "kokoro");
        payload.put("input", text);
        payload.put("voice", StringUtils.hasText(voice) ? voice : properties.getVoice());
        payload.put("format", properties.getFormat()); // legacy field for older backends
        payload.put("response_format", properties.getFormat());
        payload.put("sample_rate", properties.getSampleRate());
        payload.put("stream", true);
        log.debug("【Kokoro 流式合成】发送请求，文本长度：{}，目标音色：{}", text.length(), payload.get("voice"));

        CompletableFuture<Void> completion = new CompletableFuture<>();
        Request request = new Request.Builder().url(properties.getWsUrl()).build();
        WebSocket ws = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                try {
                    log.debug("【Kokoro 流式合成】连接建立成功，发送初始化载荷");
                    webSocket.send(mapper.writeValueAsString(payload));
                } catch (Exception ex) {
                    log.error("【Kokoro 流式合成】发送初始化载荷失败", ex);
                    completion.completeExceptionally(ex);
                    webSocket.close(1011, "payload-error");
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String textMessage) {
                log.trace("【Kokoro 流式合成】收到文本消息：{}", textMessage);
                handleStringMessage(textMessage, onChunk, onComplete, completion, webSocket);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                if (bytes == null || bytes.size() == 0) {
                    return;
                }
                byte[] chunk = bytes.toByteArray();
                log.trace("【Kokoro 流式合成】收到二进制音频片段，长度：{}", chunk.length);
                onChunk.accept(chunk);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                log.debug("【Kokoro 流式合成】WebSocket 已关闭，code={}，reason={}", code, reason);
                completion.complete(null);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log.error("【Kokoro 流式合成】WebSocket 发生异常", t);
                completion.completeExceptionally(t);
            }
        });

        try {
            completion.orTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS).join();
            log.info("【Kokoro 流式合成】流式合成流程完成");
        } catch (Exception ex) {
            log.error("【Kokoro 流式合成】流式合成超时或失败", ex);
            ws.cancel();
            throw new AiServiceException("Streaming TTS timed out", ex);
        }
    }

    private void handleStringMessage(String message,
                                     Consumer<byte[]> onChunk,
                                     Runnable onComplete,
                                     CompletableFuture<Void> completion,
                                     WebSocket ws) {
        try {
            JsonNode node = mapper.readTree(message);
            String marker = resolveMarker(node);
            switch (marker) {
                case "started", "ready", "begin" -> log.trace("【Kokoro 流式合成】收到状态帧：{}", marker);
                case "chunk", "audio", "data" -> {
                    String base64 = extractAudioBase64(node);
                    if (!base64.isEmpty()) {
                        byte[] audio = Base64.getDecoder().decode(base64);
                        log.trace("【Kokoro 流式合成】收到音频片段（Base64），解码后长度：{}", audio.length);
                        onChunk.accept(audio);
                    }
                }
                case "end", "finished", "done", "complete" -> {
                    log.debug("【Kokoro 流式合成】收到结束标记：{}", marker);
                    if (!completion.isDone()) {
                        completion.complete(null);
                    }
                    onComplete.run();
                    ws.close(1000, "done");
                }
                default -> {
                    if (node.hasNonNull("message") && !completion.isDone()) {
                        log.debug("【Kokoro 流式合成】收到 message 字段，提前结束：{}", node.get("message").asText());
                        completion.complete(null);
                        onComplete.run();
                        ws.close(1000, "message");
                    }
                }
            }
        } catch (Exception parseError) {
            log.warn("【Kokoro 流式合成】解析文本消息失败，将尝试降级处理", parseError);
            if (message.contains("\"event\":\"end\"")) {
                if (!completion.isDone()) {
                    completion.complete(null);
                }
                onComplete.run();
                ws.close(1000, "done");
            } else if (!message.isBlank()) {
                byte[] chunk = message.getBytes(StandardCharsets.UTF_8);
                log.trace("【Kokoro 流式合成】将纯文本消息作为音频片段处理，长度：{}", chunk.length);
                onChunk.accept(chunk);
            }
        }
    }

    private String resolveMarker(JsonNode node) {
        if (node.hasNonNull("event")) {
            return node.get("event").asText("").toLowerCase();
        }
        if (node.hasNonNull("type")) {
            return node.get("type").asText("").toLowerCase();
        }
        return "";
    }

    private String extractAudioBase64(JsonNode node) {
        if (node.hasNonNull("data")) {
            return node.get("data").asText("");
        }
        if (node.hasNonNull("audio")) {
            return node.get("audio").asText("");
        }
        if (node.hasNonNull("chunk")) {
            return node.get("chunk").asText("");
        }
        if (node.hasNonNull("audio_chunk")) {
            return node.get("audio_chunk").asText("");
        }
        if (node.hasNonNull("audioChunk")) {
            return node.get("audioChunk").asText("");
        }
        return "";
    }
}
