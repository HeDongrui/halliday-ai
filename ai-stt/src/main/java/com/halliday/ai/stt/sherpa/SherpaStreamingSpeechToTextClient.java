package com.halliday.ai.stt.sherpa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.halliday.ai.common.stt.SttResult;
import com.halliday.ai.stt.config.SherpaSttProperties;
import com.halliday.ai.stt.core.StreamingSpeechToTextClient;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class SherpaStreamingSpeechToTextClient implements StreamingSpeechToTextClient {

    private final SherpaSttProperties properties;
    private final ObjectMapper mapper;
    private final OkHttpClient client;
    private final ExecutorService executor;

    public SherpaStreamingSpeechToTextClient(SherpaSttProperties properties, ObjectMapper mapper) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.client = new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(0, TimeUnit.MILLISECONDS)
                .build();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setName("sherpa-stream-" + THREAD_COUNTER.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    @Override
    public void streamRecognize(InputStream pcmStream, Consumer<SttResult> onResult) {
        Objects.requireNonNull(pcmStream, "pcmStream");
        Objects.requireNonNull(onResult, "onResult");
        Request request = new Request.Builder().url(properties.getWsUrl()).build();
        WebSocket webSocket = client.newWebSocket(request, new SherpaListener(onResult));
        executor.execute(() -> sendPcm(pcmStream, webSocket));
    }

    private void sendPcm(InputStream pcmStream, WebSocket webSocket) {
        byte[] buffer = new byte[Math.max(1, properties.getFrameBytes())];
        try (InputStream input = pcmStream) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (!webSocket.send(ByteString.of(buffer, 0, read))) {
                    throw new IOException("WebSocket send failed");
                }
            }
            webSocket.close(1000, "eof");
        } catch (IOException ex) {
            webSocket.cancel();
        }
    }

    private class SherpaListener extends WebSocketListener {

        private final Consumer<SttResult> consumer;

        private SherpaListener(Consumer<SttResult> consumer) {
            this.consumer = consumer;
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                JsonNode node = mapper.readTree(text);
                String transcript = extractText(node);
                boolean finished = isFinal(node);
                if (!transcript.isEmpty() || finished) {
                    consumer.accept(SttResult.builder().text(transcript).finished(finished).idx(0).build());
                }
            } catch (IOException ignored) {
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            consumer.accept(SttResult.builder().text("").finished(true).idx(0).build());
        }
    }

    private boolean isFinal(JsonNode node) {
        if (node.path("finished").asBoolean(false) || node.path("final").asBoolean(false) || node.path("is_final").asBoolean(false)) {
            return true;
        }
        String type = node.path("type").asText("");
        return "final".equalsIgnoreCase(type) || "final_result".equalsIgnoreCase(type) || type.toLowerCase().endsWith("_final");
    }

    private String extractText(JsonNode node) {
        String text = node.path("text").asText("");
        if (String.valueOf(text).isEmpty()) {
            JsonNode segment = node.path("segment");
            if (segment.isObject()) {
                text = segment.path("text").asText("");
            }
        }
        return text == null ? "" : text;
    }
}
