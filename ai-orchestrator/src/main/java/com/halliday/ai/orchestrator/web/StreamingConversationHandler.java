package com.halliday.ai.orchestrator.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.halliday.ai.common.audio.AudioFormat;
import com.halliday.ai.common.conversation.ConversationMessage;
import com.halliday.ai.common.conversation.ConversationRole;
import com.halliday.ai.common.stt.SttResult;
import com.halliday.ai.llm.core.StreamingLanguageModelClient;
import com.halliday.ai.stt.core.StreamingSpeechToTextClient;
import com.halliday.ai.tts.config.KokoroTtsProperties;
import com.halliday.ai.tts.core.StreamingTextToSpeechClient;
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

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class StreamingConversationHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(StreamingConversationHandler.class);
    private static final String SENTENCE_BOUNDARY = "。！？.!?";

    private final ObjectMapper mapper;
    private final StreamingSpeechToTextClient sttClient;
    private final StreamingLanguageModelClient llmClient;
    private final StreamingTextToSpeechClient streamingTtsClient;
    private final TextToSpeechClient blockingTtsClient;
    private final KokoroTtsProperties ttsProperties;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, SessionContext> sessions = new ConcurrentHashMap<>();

    public StreamingConversationHandler(ObjectMapper mapper,
                                        StreamingSpeechToTextClient sttClient,
                                        StreamingLanguageModelClient llmClient,
                                        StreamingTextToSpeechClient streamingTtsClient,
                                        TextToSpeechClient blockingTtsClient,
                                        KokoroTtsProperties ttsProperties) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.sttClient = Objects.requireNonNull(sttClient, "sttClient");
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.streamingTtsClient = Objects.requireNonNull(streamingTtsClient, "streamingTtsClient");
        this.blockingTtsClient = Objects.requireNonNull(blockingTtsClient, "blockingTtsClient");
        this.ttsProperties = Objects.requireNonNull(ttsProperties, "ttsProperties");
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        sessions.values().forEach(SessionContext::dispose);
        sessions.clear();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), new SessionContext());
        sendJson(session, event("ready"));
        log.debug("WebSocket session {} established", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SessionContext ctx = sessions.remove(session.getId());
        if (ctx != null) {
            ctx.dispose();
        }
        log.debug("WebSocket session {} closed (code={})", session.getId(), status.getCode());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = mapper.readTree(message.getPayload());
        String type = node.path("type").asText("");
        SessionContext ctx = sessions.get(session.getId());
        if (ctx == null) {
            log.warn("Message for unknown session {}", session.getId());
            return;
        }
        switch (type) {
            case "start" -> handleStart(session, ctx, node);
            case "audio" -> handleAudio(ctx, node);
            case "stop" -> handleStop(session, ctx);
            case "reset_history" -> ctx.history.clear();
            default -> log.warn("Unsupported message type: {}", type);
        }
    }

    private void handleStart(WebSocketSession session, SessionContext ctx, JsonNode node) throws IOException {
        if (ctx.turnActive.get()) {
            sendJson(session, error("TURN_IN_PROGRESS", "上一轮对话尚未完成"));
            return;
        }
        ctx.resetTurn();
        ctx.inputFormat = parseAudioFormat(node);
        node.path("history").forEach(item -> parseConversationMessage(item).ifPresent(ctx.history::add));
        ctx.turnActive.set(true);
        ctx.capturing.set(true);
        sendJson(session, event("listening"));
        startStreamingStt(session, ctx);
    }

    private void handleAudio(SessionContext ctx, JsonNode node) {
        if (!ctx.capturing.get()) {
            return;
        }
        String chunkBase64 = node.path("chunk").asText("");
        if (!StringUtils.hasText(chunkBase64)) {
            return;
        }
        byte[] bytes = Base64.getDecoder().decode(chunkBase64);
        PipedOutputStream output = ctx.audioOutput;
        if (output == null) {
            return;
        }
        try {
            output.write(bytes);
        } catch (IOException ex) {
            log.warn("Failed to write audio chunk", ex);
            ctx.capturing.set(false);
        }
    }

    private void handleStop(WebSocketSession session, SessionContext ctx) {
        if (!ctx.capturing.compareAndSet(true, false)) {
            return;
        }
        try {
            ctx.audioOutput.flush();
            ctx.audioOutput.close();
        } catch (IOException ignored) {
        }
        ctx.audioOutput = null;
        // STT callback will handle final transcript
        executor.execute(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {
            }
            finalizeTranscript(session, ctx);
        });
    }

    private void startStreamingStt(WebSocketSession session, SessionContext ctx) throws IOException {
        ctx.initAudioPipe();
        executor.execute(() -> {
            try {
                sttClient.streamRecognize(ctx.audioInput, result -> handleSttResult(session, ctx, result));
            } catch (Exception ex) {
                log.warn("Streaming STT failed", ex);
                sendSafely(session, error("STT_ERROR", ex.getMessage()));
                ctx.turnActive.set(false);
            }
        });
    }

    private void handleSttResult(WebSocketSession session, SessionContext ctx, SttResult result) {
        String text = result.getText() == null ? "" : result.getText().trim();
        if (StringUtils.hasText(text)) {
            ctx.updateTranscript(text, result.isFinished());
            ObjectNode msg = event("transcript");
            msg.put("text", text);
            msg.put("final", result.isFinished());
            sendSafely(session, msg);
        }
        if (result.isFinished()) {
            finalizeTranscript(session, ctx);
        }
    }

    private void finalizeTranscript(WebSocketSession session, SessionContext ctx) {
        if (!ctx.processing.compareAndSet(false, true)) {
            return;
        }
        ctx.capturing.set(false);
        ctx.closeAudioInput();
        String userText = ctx.consumeTranscript();
        if (!StringUtils.hasText(userText)) {
            sendSafely(session, event("no_speech"));
            ctx.turnActive.set(false);
            ctx.processing.set(false);
            return;
        }
        ctx.history.add(new ConversationMessage(ConversationRole.USER, userText));
        streamAssistant(session, ctx, userText);
    }

    private void streamAssistant(WebSocketSession session, SessionContext ctx, String userText) {
        executor.execute(() -> {
            StringBuilder accumulated = new StringBuilder();
            StringBuilder pendingSentence = new StringBuilder();
            try {
                llmClient.streamChat(new ArrayList<>(ctx.history), delta -> {
                    if (!StringUtils.hasText(delta)) {
                        return;
                    }
                    accumulated.append(delta);
                    pendingSentence.append(delta);
                    sendSafely(session, assistantDelta(delta));
                    emitSentences(session, ctx, pendingSentence);
                }, done -> {
                    String finalText = StringUtils.hasText(done) ? done : accumulated.toString();
                    if (StringUtils.hasText(finalText)) {
                        emitResidualSentence(session, ctx, pendingSentence);
                        ctx.history.add(new ConversationMessage(ConversationRole.ASSISTANT, finalText));
                    }
                    ctx.ttsChain.whenComplete((ignored, throwable) -> {
                        if (throwable != null) {
                            sendSafely(session, error("TTS_ERROR", throwable.getMessage()));
                        }
                        ObjectNode complete = event("tts_complete");
                        complete.set("history", toHistoryArray(ctx.history));
                        complete.put("sampleRate", ttsProperties.getSampleRate());
                        complete.put("channels", ttsProperties.getChannels());
                        sendSafely(session, complete);
                        ctx.turnActive.set(false);
                        ctx.processing.set(false);
                    });
                });
            } catch (Exception ex) {
                sendSafely(session, error("LLM_ERROR", ex.getMessage()));
                ctx.turnActive.set(false);
                ctx.processing.set(false);
            }
        });
    }

    private void emitSentences(WebSocketSession session, SessionContext ctx, StringBuilder buffer) {
        int idx;
        while ((idx = findSentenceBoundary(buffer)) != -1) {
            String sentence = buffer.substring(0, idx + 1).trim();
            buffer.delete(0, idx + 1);
            if (!sentence.isEmpty()) {
                enqueueTts(session, ctx, sentence);
            }
        }
    }

    private void emitResidualSentence(WebSocketSession session, SessionContext ctx, StringBuilder buffer) {
        String leftover = buffer.toString().trim();
        buffer.setLength(0);
        if (!leftover.isEmpty()) {
            enqueueTts(session, ctx, leftover);
        }
    }

    private void enqueueTts(WebSocketSession session, SessionContext ctx, String sentence) {
        ctx.ttsChain = ctx.ttsChain.thenRunAsync(() -> streamSentenceTts(session, sentence), executor);
    }

    private void streamSentenceTts(WebSocketSession session, String sentence) {
        AtomicBoolean delivered = new AtomicBoolean(false);
        CompletableFuture<Void> completed = new CompletableFuture<>();
        try {
            streamingTtsClient.streamSynthesize(sentence, null, chunk -> {
                if (chunk == null || chunk.length == 0) {
                    return;
                }
                delivered.set(true);
                sendAudioChunk(session, chunk, ttsProperties.getSampleRate(), ttsProperties.getChannels());
            }, () -> completed.complete(null));
            completed.join();
        } catch (Exception ex) {
            log.warn("Streaming TTS failed, fallback to blocking synth", ex);
            completed.completeExceptionally(ex);
        }

        if (!delivered.get()) {
            try {
                byte[] audio = blockingTtsClient.synthesize(sentence, null);
                if (audio != null && audio.length > 0) {
                    log.debug("Fallback TTS used for sentence: {}", sentence);
                    chunkAndSendAudio(session, audio);
                }
            } catch (Exception ex) {
                sendSafely(session, error("TTS_ERROR", ex.getMessage()));
            }
        }
    }

    private void sendAudioChunk(WebSocketSession session, byte[] chunk) {
        ObjectNode node = event("tts_chunk");
        node.put("audioBase64", Base64.getEncoder().encodeToString(chunk));
        node.put("sampleRate", ttsProperties.getSampleRate());
        node.put("channels", ttsProperties.getChannels());
        sendSafely(session, node);
    }

    private void chunkAndSendAudio(WebSocketSession session, byte[] audio) {
        AudioFormat format = blockingTtsClient.outputFormat();
        int bytesPerSample = Math.max(1, format.bitDepth() / 8);
        int bytesPerFrame = bytesPerSample * Math.max(1, format.channels());
        int chunkSize = Math.max(bytesPerFrame, format.sampleRate() * bytesPerFrame / 10); // ~100ms
        int offset = 0;
        while (offset < audio.length) {
            int len = Math.min(chunkSize, audio.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(audio, offset, chunk, 0, len);
            offset += len;
            sendAudioChunk(session, chunk, format.sampleRate(), format.channels());
        }
    }

    private void sendAudioChunk(WebSocketSession session, byte[] chunk, int sampleRate, int channels) {
        ObjectNode node = event("tts_chunk");
        node.put("audioBase64", Base64.getEncoder().encodeToString(chunk));
        node.put("sampleRate", sampleRate);
        node.put("channels", channels);
        sendSafely(session, node);
    }

    private void sendSafely(WebSocketSession session, ObjectNode node) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(node.toString()));
            }
        } catch (IOException ex) {
            log.warn("Failed to send WebSocket message", ex);
        }
    }

    private ObjectNode assistantDelta(String delta) {
        ObjectNode node = event("assistant_text");
        node.put("text", delta);
        return node;
    }

    private ObjectNode error(String code, String message) {
        ObjectNode node = event("error");
        node.put("code", code);
        if (StringUtils.hasText(message)) {
            node.put("message", message);
        }
        return node;
    }

    private int findSentenceBoundary(CharSequence buffer) {
        for (int i = 0; i < buffer.length(); i++) {
            if (SENTENCE_BOUNDARY.indexOf(buffer.charAt(i)) >= 0) {
                return i;
            }
        }
        return -1;
    }

    private AudioFormat parseAudioFormat(JsonNode node) {
        int sampleRate = node.path("sampleRate").asInt(AudioFormat.PCM16_MONO_16K.sampleRate());
        int channels = node.path("channels").asInt(AudioFormat.PCM16_MONO_16K.channels());
        int bitDepth = node.path("bitDepth").asInt(AudioFormat.PCM16_MONO_16K.bitDepth());
        return new AudioFormat(sampleRate, channels, bitDepth, AudioFormat.Endianness.LITTLE);
    }

    private ArrayNode toHistoryArray(List<ConversationMessage> history) {
        ArrayNode array = mapper.createArrayNode();
        history.forEach(message -> {
            ObjectNode item = array.addObject();
            item.put("role", message.role().name().toLowerCase());
            item.put("content", message.content());
        });
        return array;
    }

    private ObjectNode event(String type) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", type);
        return node;
    }

    private java.util.Optional<ConversationMessage> parseConversationMessage(JsonNode node) {
        String role = node.path("role").asText("");
        String content = node.path("content").asText("");
        if (!StringUtils.hasText(role) || !StringUtils.hasText(content)) {
            return java.util.Optional.empty();
        }
        try {
            ConversationRole conversationRole = ConversationRole.valueOf(role.trim().toUpperCase());
            return java.util.Optional.of(new ConversationMessage(conversationRole, content));
        } catch (IllegalArgumentException ex) {
            return java.util.Optional.empty();
        }
    }

    private void sendJson(WebSocketSession session, ObjectNode node) throws IOException {
        if (!session.isOpen()) {
            return;
        }
        synchronized (session) {
            session.sendMessage(new TextMessage(node.toString()));
        }
    }

    private static class SessionContext {
        AudioFormat inputFormat = AudioFormat.PCM16_MONO_16K;
        final List<ConversationMessage> history = new ArrayList<>();
        final AtomicBoolean capturing = new AtomicBoolean(false);
        final AtomicBoolean turnActive = new AtomicBoolean(false);
        final AtomicBoolean processing = new AtomicBoolean(false);
        PipedInputStream audioInput;
        PipedOutputStream audioOutput;
        final StringBuilder transcriptBuffer = new StringBuilder();
        CompletableFuture<Void> ttsChain = CompletableFuture.completedFuture(null);

        void resetTurn() {
            transcriptBuffer.setLength(0);
            ttsChain = CompletableFuture.completedFuture(null);
        }

        void initAudioPipe() throws IOException {
            audioInput = new PipedInputStream(64 * 1024);
            audioOutput = new PipedOutputStream(audioInput);
        }

        void closeAudioInput() {
            try {
                if (audioInput != null) {
                    audioInput.close();
                }
            } catch (IOException ignored) {
            }
            audioInput = null;
        }

        void dispose() {
            closeAudioInput();
            if (audioOutput != null) {
                try {
                    audioOutput.close();
                } catch (IOException ignored) {
                }
            }
        }

        void updateTranscript(String text, boolean isFinal) {
            if (isFinal) {
                transcriptBuffer.setLength(0);
                transcriptBuffer.append(text);
            } else {
                transcriptBuffer.setLength(0);
                transcriptBuffer.append(text);
            }
        }

        String consumeTranscript() {
            String value = transcriptBuffer.toString();
            transcriptBuffer.setLength(0);
            return value.trim();
        }
    }
}
