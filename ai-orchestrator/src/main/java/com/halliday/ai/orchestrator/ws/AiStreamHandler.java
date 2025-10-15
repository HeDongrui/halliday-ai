package com.halliday.ai.orchestrator.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.halliday.ai.common.dto.LlmMessage;
import com.halliday.ai.common.dto.SttResult;
import com.halliday.ai.llm.core.LlmService;
import com.halliday.ai.stt.config.SherpaSttProperties;
import com.halliday.ai.stt.core.SttService;
import com.halliday.ai.tts.config.KokoroTtsProperties;
import com.halliday.ai.tts.core.TtsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket 处理器，实现 STT→LLM→TTS 的实时编排。
 */
@Slf4j
@Component
public class AiStreamHandler extends AbstractWebSocketHandler {

    private final SttService sttService;
    private final LlmService llmService;
    private final TtsService ttsService;
    private final SherpaSttProperties sttProperties;
    private final KokoroTtsProperties ttsProperties;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final Map<String, StreamingContext> contexts = new ConcurrentHashMap<>();
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    public AiStreamHandler(SttService sttService,
                           LlmService llmService,
                           TtsService ttsService,
                           SherpaSttProperties sttProperties,
                           KokoroTtsProperties ttsProperties,
                           ObjectMapper objectMapper) {
        this.sttService = sttService;
        this.llmService = llmService;
        this.ttsService = ttsService;
        this.sttProperties = sttProperties;
        this.ttsProperties = ttsProperties;
        this.objectMapper = objectMapper;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setName("ai-orchestrator-" + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            StreamingContext context = new StreamingContext(sttProperties.getBufferSize());
            contexts.put(session.getId(), context);
            executor.execute(() -> sttService.streamRecognize(context.getAudioInputStream(), result -> handleSttResult(session, context, result)));
            log.info("WebSocket 会话建立: {}", session.getId());
        } catch (IOException ex) {
            log.error("创建音频管道失败", ex);
            sendError(session, "PIPE_INIT_FAILED", "无法初始化音频管道");
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        StreamingContext context = contexts.get(session.getId());
        if (context == null) {
            return;
        }
        byte[] data = new byte[message.getPayload().remaining()];
        message.getPayload().get(data);
        executor.execute(() -> context.writeAudio(data));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        StreamingContext context = contexts.get(session.getId());
        if (context == null) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            String type = node.path("type").asText();
            if ("eos".equalsIgnoreCase(type) || "vad_end".equalsIgnoreCase(type)) {
                context.finalizeInputAsync(executor);
            }
        } catch (IOException ex) {
            log.warn("解析控制消息失败: {}", message.getPayload(), ex);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        StreamingContext context = contexts.remove(session.getId());
        if (context != null) {
            context.close();
        }
        log.info("WebSocket 会话关闭: {}", session.getId());
    }

    private void handleSttResult(WebSocketSession session, StreamingContext context, SttResult result) {
        if (result == null) {
            return;
        }
        try {
            boolean hasText = result.getText() != null && !result.getText().isBlank();
            boolean finalizeBySherpa = result.isFinished();
            boolean finalizeByClient = context.isFinalizationRequested() && hasText;

            if (finalizeBySherpa || finalizeByClient) {
                context.clearFinalizationRequest();
                if (!hasText) {
                    return;
                }
                sendFinalText(session, result);
                context.appendHistory(LlmMessage.builder().role("user").content(result.getText()).build());
                executor.execute(() -> triggerLlm(session, context, result));
            } else if (hasText) {
                sendInterimText(session, result);
            }
        } catch (Exception ex) {
            log.error("处理 STT 结果异常", ex);
            sendError(session, "STT_ERROR", ex.getMessage());
        }
    }

    private void triggerLlm(WebSocketSession session, StreamingContext context, SttResult result) {
        SegmentPipeline pipeline = new SegmentPipeline(session, context, result.getIdx());
        try {
            llmService.streamChat(new ArrayList<>(context.getHistory()), pipeline::onDelta, pipeline::onDone);
        } catch (Exception ex) {
            log.error("调用 LLM 失败", ex);
            sendError(session, "LLM_ERROR", ex.getMessage());
        }
    }

    private void sendInterimText(WebSocketSession session, SttResult result) throws IOException {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "interim_text");
        node.put("text", result.getText());
        node.put("idx", result.getIdx());
        sendJson(session, node);
    }

    private void sendFinalText(WebSocketSession session, SttResult result) throws IOException {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "final_text");
        node.put("text", result.getText());
        node.put("idx", result.getIdx());
        sendJson(session, node);
    }

    private void sendDone(WebSocketSession session, int idx) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "done");
        node.put("idx", idx);
        sendJson(session, node);
    }

    private void sendJson(WebSocketSession session, ObjectNode node) {
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(node)));
            }
        } catch (IOException ex) {
            log.error("发送 JSON 消息失败", ex);
        }
    }

    private void sendError(WebSocketSession session, String code, String message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "error");
        node.put("code", code);
        node.put("message", message);
        sendJson(session, node);
    }

    private void sendAudio(WebSocketSession session, byte[] data) {
        try {
            synchronized (session) {
                session.sendMessage(new BinaryMessage(data));
            }
        } catch (IOException ex) {
            log.error("发送音频失败", ex);
            sendError(session, "WS_SEND_ERROR", ex.getMessage());
        }
    }

    private class SegmentPipeline {

        private final WebSocketSession session;
        private final StreamingContext context;
        private final int idx;
        private final StringBuilder buffer = new StringBuilder();
        private final StringBuilder total = new StringBuilder();
        private CompletableFuture<Void> ttsChain = CompletableFuture.completedFuture(null);
        private int deliveredLength = 0;

        SegmentPipeline(WebSocketSession session, StreamingContext context, int idx) {
            this.session = session;
            this.context = context;
            this.idx = idx;
        }

        void onDelta(String delta) {
            if (delta == null || delta.isEmpty()) {
                return;
            }
            total.append(delta);
            buffer.append(delta);
            flushSentences();
        }

        void onDone(String doneText) {
            String finalText = doneText != null && !doneText.isEmpty() ? doneText : total.toString();
            if (!finalText.isEmpty()) {
                total.setLength(0);
                total.append(finalText);
                String remaining = finalText.substring(Math.min(deliveredLength, finalText.length()));
                if (!remaining.isEmpty()) {
                    buffer.append(remaining);
                }
            }
            flushSentences();
            if (buffer.length() > 0) {
                String leftover = buffer.toString().trim();
                buffer.setLength(0);
                if (!leftover.isEmpty()) {
                    scheduleTts(leftover);
                    deliveredLength += leftover.length();
                }
            }
            context.appendHistory(LlmMessage.builder().role("assistant").content(total.toString()).build());
            ttsChain.whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    log.error("TTS 播放失败", throwable);
                    sendError(session, "TTS_ERROR", throwable.getMessage());
                } else {
                    sendDone(session, idx);
                }
            });
        }

        private void flushSentences() {
            while (buffer.length() > 0) {
                int boundaryIndex = findBoundaryIndex(buffer);
                if (boundaryIndex == -1) {
                    break;
                }
                String sentence = buffer.substring(0, boundaryIndex + 1).trim();
                buffer.delete(0, boundaryIndex + 1);
                if (!sentence.isEmpty()) {
                    scheduleTts(sentence);
                    deliveredLength += sentence.length();
                }
            }
        }

        private int findBoundaryIndex(CharSequence seq) {
            for (int i = 0; i < seq.length(); i++) {
                char ch = seq.charAt(i);
                if (isBoundaryChar(ch)) {
                    return i;
                }
            }
            return -1;
        }

        private boolean isBoundaryChar(char ch) {
            return ch == '。' || ch == '！' || ch == '？' || ch == '.' || ch == '!' || ch == '?';
        }

        private void scheduleTts(String sentence) {
            ttsChain = ttsChain.thenRunAsync(() -> synthesize(sentence), executor);
        }

        private void synthesize(String sentence) {
            try {
                ttsService.streamSynthesize(sentence, ttsProperties.getVoice(), ttsProperties.getFormat(),
                        data -> sendAudio(session, data),
                        () -> {
                        });
            } catch (Exception ex) {
                throw new IllegalStateException("TTS 合成失败", ex);
            }
        }
    }

    private static class StreamingContext {

        private final PipedInputStream audioInputStream;
        private final PipedOutputStream audioOutputStream;
        private final List<LlmMessage> history = new ArrayList<>();
        private final AtomicBoolean finalizationRequested = new AtomicBoolean(false);
        private final AtomicBoolean inputClosed = new AtomicBoolean(false);

        StreamingContext(int bufferSize) throws IOException {
            this.audioInputStream = new PipedInputStream(bufferSize);
            this.audioOutputStream = new PipedOutputStream(audioInputStream);
        }

        InputStream getAudioInputStream() {
            return new NonClosingInputStream(audioInputStream);
        }

        void writeAudio(byte[] data) {
            try {
                audioOutputStream.write(data);
            } catch (IOException ex) {
                log.error("写入音频流失败", ex);
            }
        }

        void closeInput() {
            if (!inputClosed.compareAndSet(false, true)) {
                return;
            }
            try {
                audioOutputStream.flush();
                audioOutputStream.close();
            } catch (IOException ex) {
                log.warn("关闭音频输出失败", ex);
            }
        }

        void requestFinalization() {
            finalizationRequested.set(true);
        }

        boolean isFinalizationRequested() {
            return finalizationRequested.get();
        }

        void clearFinalizationRequest() {
            finalizationRequested.set(false);
        }

        void finalizeInputAsync(ExecutorService executor) {
            executor.execute(() -> {
                try {
                    closeInput();
                } finally {
                    requestFinalization();
                }
            });
        }

        void close() {
            closeInput();
            try {
                audioInputStream.close();
            } catch (IOException ex) {
                log.warn("关闭音频输入失败", ex);
            }
        }

        List<LlmMessage> getHistory() {
            return history;
        }

        void appendHistory(LlmMessage message) {
            history.add(message);
        }
    }

    private static class NonClosingInputStream extends java.io.InputStream {

        private final PipedInputStream delegate;

        NonClosingInputStream(PipedInputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return delegate.read(b, off, len);
        }

        @Override
        public void close() {
            // 由 StreamingContext 统一关闭底层流。
        }
    }
}
