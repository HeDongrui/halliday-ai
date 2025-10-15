package com.halliday.ai.orchestrator.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.halliday.ai.common.audio.AudioFormat;
import com.halliday.ai.common.conversation.ConversationMessage;
import com.halliday.ai.common.conversation.ConversationRole;
import com.halliday.ai.llm.core.LanguageModelClient;
import com.halliday.ai.stt.core.SpeechToTextClient;
import com.halliday.ai.tts.core.TextToSpeechClient;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class StreamingConversationHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(StreamingConversationHandler.class);
    private static final AudioFormat DEFAULT_INPUT_FORMAT = AudioFormat.PCM16_MONO_16K;
    private static final int STREAM_CHUNK_DURATION_MS = 100;

    private final SpeechToTextClient speechToTextClient;
    private final LanguageModelClient languageModelClient;
    private final TextToSpeechClient textToSpeechClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public StreamingConversationHandler(SpeechToTextClient speechToTextClient,
                                        LanguageModelClient languageModelClient,
                                        TextToSpeechClient textToSpeechClient,
                                        ObjectMapper objectMapper) {
        this.speechToTextClient = Objects.requireNonNull(speechToTextClient, "speechToTextClient");
        this.languageModelClient = Objects.requireNonNull(languageModelClient, "languageModelClient");
        this.textToSpeechClient = Objects.requireNonNull(textToSpeechClient, "textToSpeechClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), new SessionState());
        sendEvent(session, event("ready"));
        log.debug("WebSocket session {} established", session.getId());
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = objectMapper.readTree(message.getPayload());
        String type = node.path("type").asText("");
        SessionState state = sessions.get(session.getId());
        if (state == null) {
            log.warn("Received message for unknown session {}", session.getId());
            sendError(session, "SESSION_NOT_FOUND", "会话不存在或已关闭");
            return;
        }
        switch (type) {
            case "start" -> handleStart(session, state, node);
            case "audio" -> handleAudio(state, node);
            case "stop" -> handleStop(session, state);
            case "reset_history" -> handleResetHistory(state);
            default -> sendError(session, "UNSUPPORTED_TYPE", "不支持的消息类型: " + type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SessionState state = sessions.remove(session.getId());
        if (state != null) {
            state.dispose();
        }
        log.debug("WebSocket session {} closed", session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("Transport error on session {}", session.getId(), exception);
        sendError(session, "TRANSPORT_ERROR", exception.getMessage());
    }

    private void handleStart(WebSocketSession session, SessionState state, JsonNode node) throws IOException {
        AudioFormat format = parseAudioFormat(node);
        state.setInputFormat(format);
        if (node.hasNonNull("history")) {
            List<ConversationMessage> history = parseHistory(node.get("history"));
            state.setHistory(history);
        }
        sendEvent(session, event("listening"));
    }

    private void handleAudio(SessionState state, JsonNode node) {
        if (!node.hasNonNull("chunk")) {
            return;
        }
        String base64 = node.get("chunk").asText("");
        if (!StringUtils.hasText(base64)) {
            return;
        }
        byte[] bytes = Base64.getDecoder().decode(base64);
        state.appendAudio(bytes);
    }

    private void handleStop(WebSocketSession session, SessionState state) throws IOException {
        if (state.isBusy()) {
            sendError(session, "PIPELINE_BUSY", "请等待当前对话完成后再开始新的输入");
            return;
        }
        byte[] audio = state.consumeAudio();
        if (audio.length == 0) {
            sendEvent(session, event("no_speech"));
            return;
        }
        state.setBusy(true);
        CompletableFuture
                .supplyAsync(() -> transcribe(state, audio), executor)
                .thenCompose(userText -> handleUserText(session, state, userText))
                .exceptionally(ex -> {
                    log.warn("Streaming pipeline failed", ex);
                    try {
                        sendError(session, "PIPELINE_ERROR", ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
                    } catch (IOException ioException) {
                        log.error("Failed to report pipeline error", ioException);
                    }
                    state.setBusy(false);
                    return null;
                });
    }

    private CompletableFuture<Void> handleUserText(WebSocketSession session, SessionState state, String userText) {
        if (!StringUtils.hasText(userText)) {
            return CompletableFuture.runAsync(() -> {
                try {
                    sendEvent(session, event("no_speech"));
                } catch (IOException e) {
                    log.error("Failed to notify no speech", e);
                }
                state.setBusy(false);
            }, executor);
        }
        ObjectNode transcript = event("transcript");
        transcript.put("text", userText);
        try {
            sendEvent(session, transcript);
        } catch (IOException ex) {
            return CompletableFuture.failedFuture(ex);
        }
        state.appendHistory(new ConversationMessage(ConversationRole.USER, userText));
        return CompletableFuture
                .supplyAsync(() -> languageModelClient.chat(state.getHistory()), executor)
                .thenCompose(assistantText -> handleAssistantText(session, state, assistantText));
    }

    private CompletableFuture<Void> handleAssistantText(WebSocketSession session, SessionState state, String assistantText) {
        ObjectNode reply = event("assistant_text");
        reply.put("text", assistantText);
        try {
            sendEvent(session, reply);
        } catch (IOException ex) {
            return CompletableFuture.failedFuture(ex);
        }
        state.appendHistory(new ConversationMessage(ConversationRole.ASSISTANT, assistantText));
        return CompletableFuture
                .supplyAsync(() -> textToSpeechClient.synthesize(assistantText, null), executor)
                .thenAccept(audio -> {
                    try {
                        streamTts(session, state, audio);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                })
                .whenComplete((ignored, throwable) -> state.setBusy(false));
    }

    private String transcribe(SessionState state, byte[] audio) {
        return speechToTextClient.transcribe(audio, state.getInputFormat());
    }

    private void streamTts(WebSocketSession session, SessionState state, byte[] audio) throws IOException {
        AudioFormat format = textToSpeechClient.outputFormat();
        if (audio == null || audio.length == 0) {
            ObjectNode complete = event("tts_complete");
            complete.set("history", toHistoryArray(state.getHistory()));
            sendEvent(session, complete);
            return;
        }
        int bytesPerSample = Math.max(1, format.bitDepth() / 8);
        int bytesPerFrame = Math.max(1, bytesPerSample * Math.max(1, format.channels()));
        int chunkSize = Math.max(bytesPerFrame, (int) ((long) format.sampleRate() * bytesPerFrame * STREAM_CHUNK_DURATION_MS / 1000));
        int offset = 0;
        int sequence = 0;
        Base64.Encoder encoder = Base64.getEncoder();
        while (offset < audio.length) {
            int length = Math.min(chunkSize, audio.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(audio, offset, chunk, 0, length);
            offset += length;
            ObjectNode node = event("tts_chunk");
            node.put("sequence", sequence++);
            node.put("audioBase64", encoder.encodeToString(chunk));
            node.put("sampleRate", format.sampleRate());
            node.put("channels", format.channels());
            node.put("bitDepth", format.bitDepth());
            sendEvent(session, node);
        }
        ObjectNode complete = event("tts_complete");
        complete.set("history", toHistoryArray(state.getHistory()));
        complete.put("sampleRate", format.sampleRate());
        complete.put("channels", format.channels());
        sendEvent(session, complete);
    }

    private ArrayNode toHistoryArray(List<ConversationMessage> history) {
        ArrayNode array = objectMapper.createArrayNode();
        for (ConversationMessage message : history) {
            ObjectNode node = array.addObject();
            node.put("role", message.role().name().toLowerCase());
            node.put("content", message.content());
        }
        return array;
    }

    private void handleResetHistory(SessionState state) {
        state.setHistory(Collections.emptyList());
    }

    private AudioFormat parseAudioFormat(JsonNode node) {
        int sampleRate = node.path("sampleRate").asInt(DEFAULT_INPUT_FORMAT.sampleRate());
        int channels = node.path("channels").asInt(DEFAULT_INPUT_FORMAT.channels());
        int bitDepth = node.path("bitDepth").asInt(DEFAULT_INPUT_FORMAT.bitDepth());
        AudioFormat.Endianness endianness = DEFAULT_INPUT_FORMAT.endianness();
        return new AudioFormat(sampleRate, channels, bitDepth, endianness);
    }

    private List<ConversationMessage> parseHistory(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<ConversationMessage> history = new ArrayList<>();
        node.forEach(item -> {
            String role = item.path("role").asText("");
            String content = item.path("content").asText("");
            if (!StringUtils.hasText(role) || !StringUtils.hasText(content)) {
                return;
            }
            ConversationRole conversationRole;
            try {
                conversationRole = ConversationRole.valueOf(role.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                log.debug("Ignore unsupported role {}", role, ex);
                return;
            }
            history.add(new ConversationMessage(conversationRole, content));
        });
        return history;
    }

    private ObjectNode event(String type) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", type);
        return node;
    }

    private void sendEvent(WebSocketSession session, ObjectNode node) throws IOException {
        if (!session.isOpen()) {
            return;
        }
        session.sendMessage(new TextMessage(node.toString()));
    }

    private void sendError(WebSocketSession session, String code, String message) throws IOException {
        ObjectNode node = event("error");
        node.put("code", code);
        if (StringUtils.hasText(message)) {
            node.put("message", message);
        }
        sendEvent(session, node);
    }

    private static class SessionState {
        private final ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
        private final List<ConversationMessage> history = new ArrayList<>();
        private AudioFormat inputFormat = DEFAULT_INPUT_FORMAT;
        private volatile boolean busy;

        void setInputFormat(AudioFormat format) {
            this.inputFormat = format == null ? DEFAULT_INPUT_FORMAT : format;
        }

        AudioFormat getInputFormat() {
            return inputFormat;
        }

        void setHistory(List<ConversationMessage> items) {
            history.clear();
            if (items != null) {
                history.addAll(items);
            }
        }

        void appendHistory(ConversationMessage message) {
            history.add(message);
        }

        List<ConversationMessage> getHistory() {
            return List.copyOf(history);
        }

        void appendAudio(byte[] data) {
            try {
                audioBuffer.write(data);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to append audio", ex);
            }
        }

        byte[] consumeAudio() {
            byte[] data = audioBuffer.toByteArray();
            audioBuffer.reset();
            return data;
        }

        boolean isBusy() {
            return busy;
        }

        void setBusy(boolean busy) {
            this.busy = busy;
        }

        void dispose() {
            try {
                audioBuffer.close();
            } catch (IOException ignored) {
            }
            history.clear();
        }
    }
}
