package com.halliday.ai.stt.sherpa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.halliday.ai.common.spi.NamedService;
import com.halliday.ai.common.stt.SttResult;
import com.halliday.ai.stt.config.SherpaSttProperties;
import com.halliday.ai.stt.core.StreamingSpeechToTextClient;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class SherpaStreamingSpeechToTextClient implements StreamingSpeechToTextClient, NamedService {

    private static final Logger log = LoggerFactory.getLogger(SherpaStreamingSpeechToTextClient.class);

    private final SherpaSttProperties properties;
    private final ObjectMapper mapper;
    private final OkHttpClient client;
    private final ExecutorService executor;

    public SherpaStreamingSpeechToTextClient(SherpaSttProperties properties, ObjectMapper mapper) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        log.debug("【Sherpa 流式识别】初始化客户端，目标地址：{}", properties.getWsUrl());
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
    public String id() {
        log.debug("【Sherpa 流式识别】返回服务标识：sherpa");
        return "sherpa";
    }

    @Override
    public void streamRecognize(InputStream pcmStream, Consumer<SttResult> onResult) {
        Objects.requireNonNull(pcmStream, "pcmStream");
        Objects.requireNonNull(onResult, "onResult");
        log.info("【Sherpa 流式识别】开始流式识别");
        Request request = new Request.Builder().url(properties.getWsUrl()).build();
        WebSocket webSocket = client.newWebSocket(request, new SherpaListener(onResult));
        executor.execute(() -> sendPcm(pcmStream, webSocket));
    }

    private void sendPcm(InputStream pcmStream, WebSocket webSocket) {
        byte[] buffer = new byte[Math.max(1, properties.getFrameBytes())];
        log.debug("【Sherpa 流式识别】使用缓冲区大小：{}", buffer.length);
        try (InputStream input = pcmStream) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (!webSocket.send(ByteString.of(buffer, 0, read))) {
                    log.error("【Sherpa 流式识别】WebSocket 发送失败，读取字节数：{}", read);
                    throw new IOException("WebSocket send failed");
                }
            }
            log.debug("【Sherpa 流式识别】音频发送完毕，准备关闭连接");
            webSocket.close(1000, "eof");
        } catch (IOException ex) {
            log.error("【Sherpa 流式识别】发送音频出现异常，将取消连接", ex);
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
            log.trace("【Sherpa 流式识别】收到消息：{}", text);
            try {
                JsonNode node = mapper.readTree(text);
                String transcript = extractText(node);
                boolean finished = isFinal(node);
                if (!transcript.isEmpty() || finished) {
                    log.debug("【Sherpa 流式识别】输出识别片段，长度：{}，是否结束：{}", transcript.length(), finished);
                    consumer.accept(SttResult.builder().text(transcript).finished(finished).idx(0).build());
                }
            } catch (IOException ex) {
                log.error("【Sherpa 流式识别】解析消息失败", ex);
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            log.error("【Sherpa 流式识别】WebSocket 失败，通知完成", t);
            consumer.accept(SttResult.builder().text("").finished(true).idx(0).build());
        }
    }

    private boolean isFinal(JsonNode node) {
        boolean result = node.path("finished").asBoolean(false) || node.path("final").asBoolean(false) || node.path("is_final").asBoolean(false);
        if (result) {
            log.trace("【Sherpa 流式识别】检测到 finished 标记");
            return true;
        }
        String type = node.path("type").asText("");
        boolean matched = "final".equalsIgnoreCase(type) || "final_result".equalsIgnoreCase(type) || type.toLowerCase().endsWith("_final");
        if (matched) {
            log.trace("【Sherpa 流式识别】根据 type 字段判断为最终结果：{}", type);
        }
        return matched;
    }

    private String extractText(JsonNode node) {
        String text = node.path("text").asText("");
        if (text == null || text.isEmpty()) {
            JsonNode segment = node.path("segment");
            if (segment.isObject()) {
                text = segment.path("text").asText("");
            }
        }
        String result = text == null ? "" : text;
        log.trace("【Sherpa 流式识别】解析文本，长度：{}", result.length());
        return result;
    }
}
