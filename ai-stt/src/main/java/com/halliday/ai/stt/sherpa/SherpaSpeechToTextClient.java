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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SherpaSpeechToTextClient implements SpeechToTextClient {

    private static final Logger log = LoggerFactory.getLogger(SherpaSpeechToTextClient.class);

    private final SherpaSttProperties properties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient client;

    public SherpaSpeechToTextClient(SherpaSttProperties properties, ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        log.debug("【Sherpa 识别】初始化客户端，目标地址：{}", properties.getWsUrl());
        this.client = buildClient(properties);
    }

    private OkHttpClient buildClient(SherpaSttProperties properties) {
        log.debug("【Sherpa 识别】构建 OkHttpClient，连接超时：{}ms，读取超时：{}ms，帧字节数：{}",
                properties.getConnectTimeoutMs(), properties.getReadTimeoutMs(), properties.getFrameBytes());
        return new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(0, TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public String transcribe(byte[] audio, AudioFormat format) {
        log.info("【Sherpa 识别】开始转写音频，数据长度：{}", audio == null ? 0 : audio.length);
        Objects.requireNonNull(audio, "audio");
        Objects.requireNonNull(format, "format");
        if (format.bitDepth() != 16) {
            log.error("【Sherpa 识别】音频位深不符合要求：{}", format.bitDepth());
            throw new IllegalArgumentException("Sherpa expects 16-bit PCM audio");
        }
        if (format.channels() != 1) {
            log.error("【Sherpa 识别】音频通道数不符合要求：{}", format.channels());
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
            log.error("【Sherpa 识别】发送音频失败，将关闭连接", ex);
            webSocket.cancel();
            throw new AiServiceException("Failed to stream audio to Sherpa", ex);
        }
        String result = awaitResult(future);
        log.info("【Sherpa 识别】转写完成，文本长度：{}", result.length());
        return result;
    }

    private void sendAudio(WebSocket webSocket, byte[] audio) throws IOException {
        int frameBytes = Math.max(1, properties.getFrameBytes());
        log.debug("【Sherpa 识别】开始分片发送音频，帧大小：{} 字节", frameBytes);
        int offset = 0;
        while (offset < audio.length) {
            int length = Math.min(frameBytes, audio.length - offset);
            ByteString payload = ByteString.of(audio, offset, length);
            boolean sent = webSocket.send(payload);
            if (!sent) {
                log.error("【Sherpa 识别】WebSocket 拒绝发送帧，偏移：{}，长度：{}", offset, length);
                throw new IOException("WebSocket rejected PCM frame");
            }
            offset += length;
        }
        log.debug("【Sherpa 识别】音频发送完毕，准备关闭 WebSocket");
        webSocket.close(1000, "eof");
    }

    private String awaitResult(CompletableFuture<String> future) {
        try {
            long timeoutMs = properties.getResultTimeoutMs();
            log.debug("【Sherpa 识别】等待识别结果，超时时长：{}ms", timeoutMs);
            if (timeoutMs > 0) {
                return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            }
            return future.get();
        } catch (Exception ex) {
            log.error("【Sherpa 识别】等待识别结果失败", ex);
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
            log.trace("【Sherpa 识别】收到消息：{}", text);
            try {
                JsonNode node = objectMapper.readTree(text);
                String content = extractText(node);
                if (StringUtils.hasText(content)) {
                    interim.set(content);
                    log.debug("【Sherpa 识别】更新临时文本：{}", content);
                }
                if (isFinal(node) && StringUtils.hasText(content)) {
                    StringBuilder buffer = finalBuffer.get();
                    if (buffer.length() > 0) {
                        buffer.append(' ');
                    }
                    buffer.append(content.trim());
                    log.debug("【Sherpa 识别】累计最终文本长度：{}", buffer.length());
                }
            } catch (IOException ex) {
                fail(ex);
            }
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            log.debug("【Sherpa 识别】WebSocket 已关闭，code={}, reason={}", code, reason);
            completeIfNecessary();
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            log.debug("【Sherpa 识别】WebSocket 正在关闭，code={}, reason={}", code, reason);
            completeIfNecessary();
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            log.error("【Sherpa 识别】WebSocket 出现异常", t);
            fail(t);
        }

        private void completeIfNecessary() {
            if (future.isDone()) {
                log.trace("【Sherpa 识别】Future 已完成，忽略重复完成请求");
                return;
            }
            String finalText = finalBuffer.get().toString().trim();
            if (!StringUtils.hasText(finalText)) {
                finalText = interim.get().trim();
            }
            log.debug("【Sherpa 识别】完成识别结果，文本长度：{}", finalText.length());
            future.complete(finalText);
        }

        private void fail(Throwable t) {
            if (!future.isDone()) {
                log.error("【Sherpa 识别】识别失败，准备回调异常", t);
                future.completeExceptionally(t);
            }
        }
    }

    private boolean isFinal(JsonNode node) {
        boolean result = node.path("finished").asBoolean(false) || node.path("final").asBoolean(false)
                || node.path("is_final").asBoolean(false);
        if (result) {
            log.trace("【Sherpa 识别】检测到最终结果标记");
            return true;
        }
        String type = node.path("type").asText("");
        boolean matched = "final".equalsIgnoreCase(type) || "final_result".equalsIgnoreCase(type)
                || type.toLowerCase().endsWith("_final");
        if (matched) {
            log.trace("【Sherpa 识别】根据 type 字段判断为最终结果：{}", type);
        }
        return matched;
    }

    private String extractText(JsonNode node) {
        String text = node.path("text").asText("");
        if (StringUtils.hasText(text)) {
            log.trace("【Sherpa 识别】从 text 字段解析到内容，长度：{}", text.length());
            return text;
        }
        JsonNode segment = node.path("segment");
        if (segment.isObject()) {
            String segText = segment.path("text").asText("");
            if (StringUtils.hasText(segText)) {
                log.trace("【Sherpa 识别】从 segment.text 字段解析到内容，长度：{}", segText.length());
                return segText;
            }
        }
        log.trace("【Sherpa 识别】未解析到有效文本");
        return "";
    }
}
