package com.halliday.ai.stt.sherpa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.halliday.ai.common.dto.SttResult;
import com.halliday.ai.common.metrics.AiMetrics;
import com.halliday.ai.stt.config.SherpaSttProperties;
import com.halliday.ai.stt.core.SttModelInfo;
import com.halliday.ai.stt.core.SttService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 基于 Sherpa WebSocket API 的语音识别服务实现。
 */
@Slf4j
public class SherpaSttService implements SttService {

    private final SherpaSttProperties properties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final ExecutorService streamingExecutor;
    private final Counter sttCounter;

    /**
     * 构造方法，初始化 OkHttp 客户端与线程池。
     *
     * @param properties   Sherpa 配置
     * @param objectMapper JSON 工具
     * @param registry     指标注册器
     */
    public SherpaSttService(SherpaSttProperties properties, ObjectMapper objectMapper, MeterRegistry registry) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
        this.streamingExecutor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setName("sherpa-stt-stream-" + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        this.sttCounter = Counter.builder(AiMetrics.METRIC_STT_SEGMENTS_TOTAL)
                .description("完成识别的段落数量")
                .register(registry);
    }

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    @Override
    public String transcribeFromUrl(String wavUrl) {
        Objects.requireNonNull(wavUrl, "wavUrl");
        Request request = new Request.Builder().url(wavUrl).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("下载音频失败，状态码=" + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IllegalStateException("音频响应体为空");
            }
            try (InputStream stream = body.byteStream()) {
                return transcribeStream(stream);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("离线识别失败", ex);
        }
    }

    private String transcribeStream(InputStream stream) {
        StringBuilder finalText = new StringBuilder();
        CompletableFuture<Void> completed = new CompletableFuture<>();
        Consumer<SttResult> consumer = result -> {
            if (result == null) {
                return;
            }
            if (result.getText() != null && !result.getText().isEmpty()) {
                finalText.append(result.getText());
            }
            if (result.isFinished()) {
                sttCounter.increment();
            }
        };
        startStreaming(stream, consumer, completed);
        completed.join();
        return finalText.toString();
    }

    @Override
    public void streamRecognize(InputStream pcmStream, Consumer<SttResult> onResult) {
        startStreaming(pcmStream, onResult, null);
    }

    private void startStreaming(InputStream stream, Consumer<SttResult> onResult, CompletableFuture<Void> completed) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(onResult, "onResult");
        SherpaWebSocketListener listener = new SherpaWebSocketListener(onResult, completed);
        okhttp3.Request wsRequest = new okhttp3.Request.Builder()
                .url(properties.getWsUrl())
                .build();
        WebSocket webSocket = httpClient.newWebSocket(wsRequest, listener);
        streamingExecutor.execute(() -> sendPcm(stream, webSocket, listener));
    }

    private void sendPcm(InputStream stream, WebSocket webSocket, SherpaWebSocketListener listener) {
        byte[] buffer = new byte[properties.getFrameBytes()];
        try (InputStream input = stream) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                ByteString payload = ByteString.of(buffer, 0, read);
                boolean success = webSocket.send(payload);
                if (!success) {
                    throw new IllegalStateException("WebSocket 发送失败");
                }
            }
            webSocket.close(1000, "eos");
        } catch (IOException ex) {
            log.error("发送 PCM 数据失败", ex);
            listener.fail(ex);
        }
    }

    @Override
    public SttModelInfo getModelInfo() {
        return new SttModelInfo("sherpa", "voice-api", 16000);
    }

    /**
     * WebSocket 监听器，负责解析 Sherpa 返回的 JSON 并回调结果。
     */
    private class SherpaWebSocketListener extends WebSocketListener {

        private final Consumer<SttResult> consumer;
        private final CompletableFuture<Void> completed;

        SherpaWebSocketListener(Consumer<SttResult> consumer, CompletableFuture<Void> completed) {
            this.consumer = consumer;
            this.completed = completed;
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                SttResult result = parseResult(text);
                if (result != null) {
                    consumer.accept(result);
                }
            } catch (IOException ex) {
                log.error("解析 Sherpa 响应失败: {}", text, ex);
                fail(ex);
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(code, reason);
            if (completed != null && !completed.isDone()) {
                completed.complete(null);
            }
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            if (completed != null && !completed.isDone()) {
                completed.complete(null);
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            log.error("Sherpa WebSocket 连接失败", t);
            fail(t);
        }

        void fail(Throwable throwable) {
            if (completed != null) {
                completed.completeExceptionally(throwable);
            }
        }

        private SttResult parseResult(String payload) throws IOException {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("type").asText("");
            if (type.isEmpty()) {
                return null;
            }

            boolean finished = isFinalType(type) || node.path("finished").asBoolean(false)
                    || node.path("final").asBoolean(false) || node.path("is_final").asBoolean(false);

            if (!isResultType(type) && !finished) {
                return null;
            }

            String text = extractText(node);
            int idx = extractSegmentIndex(node);

            return SttResult.builder()
                    .text(text)
                    .finished(finished)
                    .idx(idx)
                    .build();
        }

        private boolean isFinalType(String type) {
            String normalized = type.toLowerCase();
            return normalized.equals("final_result")
                    || normalized.equals("final")
                    || normalized.endsWith("_final");
        }

        private boolean isResultType(String type) {
            return type.toLowerCase().contains("result");
        }

        private String extractText(JsonNode node) {
            String value = node.path("text").asText("");
            if (!value.isEmpty()) {
                return value;
            }
            JsonNode segmentNode = node.path("segment");
            if (segmentNode.isObject()) {
                value = segmentNode.path("text").asText("");
                if (!value.isEmpty()) {
                    return value;
                }
            }
            return value;
        }

        private int extractSegmentIndex(JsonNode node) {
            if (node.has("idx")) {
                return node.path("idx").asInt();
            }
            if (node.has("index")) {
                return node.path("index").asInt();
            }
            if (node.has("seg_index")) {
                return node.path("seg_index").asInt();
            }
            JsonNode segmentNode = node.path("segment");
            if (segmentNode.isObject()) {
                if (segmentNode.has("idx")) {
                    return segmentNode.path("idx").asInt();
                }
                if (segmentNode.has("index")) {
                    return segmentNode.path("index").asInt();
                }
                if (segmentNode.has("segment_id")) {
                    return segmentNode.path("segment_id").asInt();
                }
                if (segmentNode.has("seg_index")) {
                    return segmentNode.path("seg_index").asInt();
                }
            }
            return 0;
        }
    }
}
