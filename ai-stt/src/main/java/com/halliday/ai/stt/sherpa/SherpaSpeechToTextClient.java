package com.halliday.ai.stt.sherpa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.halliday.ai.common.audio.AudioFormat;
import com.halliday.ai.common.exception.AiServiceException;
import com.halliday.ai.stt.config.SherpaSttProperties;
import com.halliday.ai.stt.core.SpeechToTextClient;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SherpaSpeechToTextClient implements SpeechToTextClient {

    private final SherpaSttProperties properties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient client;

    public SherpaSpeechToTextClient(SherpaSttProperties properties, ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.client = buildClient(properties);
    }

    private OkHttpClient buildClient(SherpaSttProperties properties) {
        return new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(0, TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public String transcribe(byte[] audio, AudioFormat format) {
        Objects.requireNonNull(audio, "audio");
        Objects.requireNonNull(format, "format");
        if (format.bitDepth() != 16) {
            throw new IllegalArgumentException("Sherpa expects 16-bit PCM audio");
        }
        if (format.channels() != 1) {
            throw new IllegalArgumentException("Sherpa demo client currently supports mono audio");
        }
        Request request = new Request.Builder().url(properties.getWsUrl()).build();
        CompletableFuture<String> future = new CompletableFuture<>();
        AtomicReference<StringBuilder> finalBuffer = new AtomicReference<>(new StringBuilder());
        AtomicReference<String> interim = new AtomicReference<>("");
        SherpaListener listener = new SherpaListener(future, finalBuffer, interim);
        WebSocket webSocket = client.newWebSocket(request, listener);
        try {
            sendAudio(webSocket, audio);
        } catch (IOException ex) {
            webSocket.cancel();
            throw new AiServiceException("Failed to stream audio to Sherpa", ex);
        }
        return awaitResult(future);
    }

    private void sendAudio(WebSocket webSocket, byte[] audio) throws IOException {
        int frameBytes = Math.max(1, properties.getFrameBytes());
        int offset = 0;
        while (offset < audio.length) {
            int length = Math.min(frameBytes, audio.length - offset);
            ByteString payload = ByteString.of(audio, offset, length);
            boolean sent = webSocket.send(payload);
            if (!sent) {
                throw new IOException("WebSocket rejected PCM frame");
            }
            offset += length;
        }
        webSocket.close(1000, "eof");
    }

    private String awaitResult(CompletableFuture<String> future) {
        try {
            long timeoutMs = properties.getResultTimeoutMs();
            if (timeoutMs > 0) {
                return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            }
            return future.get();
        } catch (Exception ex) {
            throw new AiServiceException("Failed to receive STT result", ex);
        }
    }

    private class SherpaListener extends WebSocketListener {

        private final CompletableFuture<String> future;
        private final AtomicReference<StringBuilder> finalBuffer;
        private final AtomicReference<String> interim;

        private SherpaListener(CompletableFuture<String> future,
                               AtomicReference<StringBuilder> finalBuffer,
                               AtomicReference<String> interim) {
            this.future = future;
            this.finalBuffer = finalBuffer;
            this.interim = interim;
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                JsonNode node = objectMapper.readTree(text);
                String content = extractText(node);
                if (StringUtils.hasText(content)) {
                    interim.set(content);
                }
                if (isFinal(node) && StringUtils.hasText(content)) {
                    StringBuilder buffer = finalBuffer.get();
                    if (buffer.length() > 0) {
                        buffer.append(' ');
                    }
                    buffer.append(content.trim());
                }
            } catch (IOException ex) {
                fail(ex);
            }
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            completeIfNecessary();
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            completeIfNecessary();
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            fail(t);
        }

        private void completeIfNecessary() {
            if (future.isDone()) {
                return;
            }
            String finalText = finalBuffer.get().toString().trim();
            if (!StringUtils.hasText(finalText)) {
                finalText = interim.get().trim();
            }
            future.complete(finalText);
        }

        private void fail(Throwable t) {
            if (!future.isDone()) {
                future.completeExceptionally(t);
            }
        }
    }

    private boolean isFinal(JsonNode node) {
        if (node.path("finished").asBoolean(false) || node.path("final").asBoolean(false)
                || node.path("is_final").asBoolean(false)) {
            return true;
        }
        String type = node.path("type").asText("");
        return "final".equalsIgnoreCase(type) || "final_result".equalsIgnoreCase(type)
                || type.toLowerCase().endsWith("_final");
    }

    private String extractText(JsonNode node) {
        String text = node.path("text").asText("");
        if (StringUtils.hasText(text)) {
            return text;
        }
        JsonNode segment = node.path("segment");
        if (segment.isObject()) {
            String segText = segment.path("text").asText("");
            if (StringUtils.hasText(segText)) {
                return segText;
            }
        }
        return "";
    }
}
