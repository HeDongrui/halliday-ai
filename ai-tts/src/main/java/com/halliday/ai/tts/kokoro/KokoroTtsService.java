package com.halliday.ai.tts.kokoro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.halliday.ai.common.metrics.AiMetrics;
import com.halliday.ai.tts.config.KokoroTtsProperties;
import com.halliday.ai.tts.core.TtsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 基于 Kokoro FastAPI 的 TTS 实现，支持文件与流式两种模式。
 */
@Slf4j
public class KokoroTtsService implements TtsService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final KokoroTtsProperties properties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private final Counter ttsBytesCounter;

    /**
     * 构造方法，初始化网络客户端与线程池。
     *
     * @param properties   TTS 配置
     * @param objectMapper JSON 工具
     * @param registry     指标注册器
     */
    public KokoroTtsService(KokoroTtsProperties properties, ObjectMapper objectMapper, MeterRegistry registry) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setName("kokoro-tts-" + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        this.ttsBytesCounter = Counter.builder(AiMetrics.METRIC_TTS_BYTES_STREAMED_TOTAL)
                .description("下发给客户端的音频字节数")
                .register(registry);
    }

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    @Override
    public Path synthesizeToFile(String text, String voice, String format) {
        Objects.requireNonNull(text, "text");
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", "kokoro");
        payload.put("voice", voice != null ? voice : properties.getVoice());
        payload.put("input", text);
        payload.put("response_format", format != null ? format : properties.getFormat());
        payload.put("stream", false);
        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);
            Request request = new Request.Builder()
                    .url(properties.getHttpUrl())
                    .post(RequestBody.create(body, JSON))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("调用 Kokoro TTS 文件接口失败，状态码=" + response.code());
                }
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new IllegalStateException("Kokoro TTS 响应体为空");
                }
                Path tempFile = Files.createTempFile("kokoro-", "." + payload.get("response_format"));
                Files.write(tempFile, responseBody.bytes());
                return tempFile;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("调用 Kokoro TTS 文件接口失败", ex);
        }
    }

    @Override
    public void streamSynthesize(String text, String voice, String format, Consumer<byte[]> onChunk, Runnable onEnd) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(onChunk, "onChunk");
        Objects.requireNonNull(onEnd, "onEnd");
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", "kokoro");
        payload.put("voice", voice != null ? voice : properties.getVoice());
        payload.put("input", text);
        payload.put("response_format", format != null ? format : properties.getFormat());
        CompletableFuture<Void> completed = new CompletableFuture<>();
        KokoroWebSocketListener listener = new KokoroWebSocketListener(onChunk, onEnd, completed);
        okhttp3.Request wsRequest = new okhttp3.Request.Builder()
                .url(properties.getWsUrl())
                .build();
        WebSocket webSocket = httpClient.newWebSocket(wsRequest, listener);
        executor.execute(() -> sendInitMessage(webSocket, payload, listener));
        completed.join();
    }

    private void sendInitMessage(WebSocket webSocket, Map<String, Object> payload, KokoroWebSocketListener listener) {
        try {
            String message = objectMapper.writeValueAsString(payload);
            boolean success = webSocket.send(message);
            if (!success) {
                throw new IllegalStateException("Kokoro TTS WebSocket 初始化失败");
            }
        } catch (Exception ex) {
            log.error("发送 Kokoro 初始化消息失败", ex);
            listener.fail(ex);
        }
    }

    private class KokoroWebSocketListener extends WebSocketListener {

        private final Consumer<byte[]> chunkConsumer;
        private final Runnable endCallback;
        private final CompletableFuture<Void> completed;

        KokoroWebSocketListener(Consumer<byte[]> chunkConsumer, Runnable endCallback, CompletableFuture<Void> completed) {
            this.chunkConsumer = chunkConsumer;
            this.endCallback = endCallback;
            this.completed = completed;
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            if (text.contains("\"event\":\"end\"")) {
                endCallback.run();
                if (!completed.isDone()) {
                    completed.complete(null);
                }
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            byte[] chunk = bytes.toByteArray();
            ttsBytesCounter.increment(chunk.length);
            chunkConsumer.accept(chunk);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            if (!completed.isDone()) {
                completed.complete(null);
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            log.error("Kokoro WebSocket 失败", t);
            fail(t);
        }

        void fail(Throwable throwable) {
            if (!completed.isDone()) {
                completed.completeExceptionally(throwable);
            }
        }
    }
}
