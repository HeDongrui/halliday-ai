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

    private final KokoroTtsProperties properties;
    private final ObjectMapper mapper;
    private final OkHttpClient client;

    public KokoroStreamingTextToSpeechClient(KokoroTtsProperties properties, ObjectMapper mapper) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
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

        CompletableFuture<Void> completion = new CompletableFuture<>();
        Request request = new Request.Builder().url(properties.getWsUrl()).build();
        WebSocket ws = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                try {
                    webSocket.send(mapper.writeValueAsString(payload));
                } catch (Exception ex) {
                    completion.completeExceptionally(ex);
                    webSocket.close(1011, "payload-error");
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String textMessage) {
                handleStringMessage(textMessage, onChunk, onComplete, completion, webSocket);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                if (bytes == null || bytes.size() == 0) {
                    return;
                }
                byte[] chunk = bytes.toByteArray();
                onChunk.accept(chunk);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                completion.complete(null);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                completion.completeExceptionally(t);
            }
        });

        try {
            completion.orTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS).join();
        } catch (Exception ex) {
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
                case "started", "ready", "begin" -> {
                    // ignore informational frames
                }
                case "chunk", "audio", "data" -> {
                    String base64 = extractAudioBase64(node);
                    if (!base64.isEmpty()) {
                        onChunk.accept(Base64.getDecoder().decode(base64));
                    }
                }
                case "end", "finished", "done", "complete" -> {
                    if (!completion.isDone()) {
                        completion.complete(null);
                    }
                    onComplete.run();
                    ws.close(1000, "done");
                }
                default -> {
                    // Some Kokoro builds send text transcripts as "message" field; treat as completion hint
                    if (node.hasNonNull("message") && !completion.isDone()) {
                        completion.complete(null);
                        onComplete.run();
                        ws.close(1000, "message");
                    }
                }
            }
        } catch (Exception parseError) {
            // Fallback: treat non-JSON string
            if (message.contains("\"event\":\"end\"")) {
                if (!completion.isDone()) {
                    completion.complete(null);
                }
                onComplete.run();
                ws.close(1000, "done");
            } else if (!message.isBlank()) {
                byte[] chunk = message.getBytes(StandardCharsets.UTF_8);
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
