package com.halliday.ai.orchestrator.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.halliday.ai.common.audio.AudioFormat;
import com.halliday.ai.common.conversation.ConversationMessage;
import com.halliday.ai.common.conversation.ConversationRole;
import com.halliday.ai.common.spi.NamedService;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class StreamingConversationHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(StreamingConversationHandler.class);
    private static final String SENTENCE_BOUNDARY = "。！？.!?";

    private final ObjectMapper mapper;
    private final Map<String, StreamingSpeechToTextClient> sttClients;
    private final Map<String, String> sttDisplayNames;
    private final List<String> availableSttProviders;
    private final String defaultSttProvider;
    private final StreamingLanguageModelClient llmClient;
    private final StreamingTextToSpeechClient streamingTtsClient;
    private final TextToSpeechClient blockingTtsClient;
    private final KokoroTtsProperties ttsProperties;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, SessionContext> sessions = new ConcurrentHashMap<>();

    public StreamingConversationHandler(ObjectMapper mapper,
                                        Map<String, StreamingSpeechToTextClient> sttClients,
                                        StreamingLanguageModelClient llmClient,
                                        StreamingTextToSpeechClient streamingTtsClient,
                                        TextToSpeechClient blockingTtsClient,
                                        KokoroTtsProperties ttsProperties) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(sttClients, "sttClients");
        Map<String, StreamingSpeechToTextClient> clientMap = new LinkedHashMap<>();
        Map<String, String> displayNames = new LinkedHashMap<>();
        sttClients.forEach((beanName, client) -> {
            String id = extractProviderId(beanName, client);
            if (!StringUtils.hasText(id)) {
                log.warn("【流式会话】忽略名称为 {} 的 STT Bean，原因：提供者 ID 为空", beanName);
                return;
            }
            if (clientMap.containsKey(id)) {
                log.warn("【流式会话】检测到重复的 STT 提供者 ID {}，保留第一个实例", id);
                return;
            }
            clientMap.put(id, client);
            displayNames.put(id, extractDisplayName(id, client));
        });
        if (clientMap.isEmpty()) {
            throw new IllegalStateException("No streaming STT clients configured");
        }
        this.sttClients = Collections.unmodifiableMap(clientMap);
        this.sttDisplayNames = Collections.unmodifiableMap(displayNames);
        this.availableSttProviders = List.copyOf(this.sttClients.keySet());
        this.defaultSttProvider = determineDefaultProvider(this.sttClients);
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.streamingTtsClient = Objects.requireNonNull(streamingTtsClient, "streamingTtsClient");
        this.blockingTtsClient = Objects.requireNonNull(blockingTtsClient, "blockingTtsClient");
        this.ttsProperties = Objects.requireNonNull(ttsProperties, "ttsProperties");
        log.debug("【流式会话】初始化完成，STT 服务数量：{}，默认 STT：{}", this.sttClients.size(), this.defaultSttProvider);
    }

    @PreDestroy
    public void shutdown() {
        log.info("【流式会话】开始释放资源，准备关闭线程池");
        executor.shutdownNow();
        sessions.values().forEach(SessionContext::dispose);
        sessions.clear();
        log.info("【流式会话】资源释放完成");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        SessionContext context = new SessionContext(defaultSttProvider);
        sessions.put(session.getId(), context);
        ObjectNode ready = event("ready");
        ArrayNode providers = ready.putArray("sttProviders");
        availableSttProviders.forEach(id -> {
            ObjectNode item = providers.addObject();
            item.put("id", id);
            item.put("name", sttDisplayNames.getOrDefault(id, id));
        });
        if (StringUtils.hasText(defaultSttProvider)) {
            ready.put("defaultSttProvider", defaultSttProvider);
        }
        sendJson(session, ready);
        log.debug("【流式会话】WebSocket 会话建立成功，ID={}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SessionContext ctx = sessions.remove(session.getId());
        if (ctx != null) {
            ctx.dispose();
        }
        log.debug("【流式会话】WebSocket 会话关闭，ID={}，状态码={}", session.getId(), status.getCode());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.trace("【流式会话】收到文本消息，长度：{}", message.getPayloadLength());
        JsonNode node = mapper.readTree(message.getPayload());
        String type = node.path("type").asText("");
        SessionContext ctx = sessions.get(session.getId());
        if (ctx == null) {
            log.warn("【流式会话】收到未知会话的消息，ID={}", session.getId());
            return;
        }
        switch (type) {
            case "start" -> handleStart(session, ctx, node);
            case "audio" -> handleAudio(ctx, node);
            case "stop" -> handleStop(session, ctx);
            case "reset_history" -> ctx.history.clear();
            default -> log.warn("【流式会话】收到不支持的消息类型：{}", type);
        }
    }

    private void handleStart(WebSocketSession session, SessionContext ctx, JsonNode node) throws IOException {
        log.debug("【流式会话】开始新的会话轮次，session={}", session.getId());
        if (ctx.turnActive.get()) {
            log.warn("【流式会话】上一轮对话尚未结束，拒绝新的 start 指令");
            sendJson(session, error("TURN_IN_PROGRESS", "上一轮对话尚未完成"));
            return;
        }
        ctx.resetTurn();
        ctx.inputFormat = parseAudioFormat(node);
        node.path("history").forEach(item -> parseConversationMessage(item).ifPresent(ctx.history::add));
        String requestedProvider = node.path("sttProvider").asText("");
        String provider = resolveSttProvider(requestedProvider);
        if (provider == null) {
            ObjectNode extra = mapper.createObjectNode();
            extra.put("requestedProvider", requestedProvider);
            ArrayNode available = extra.putArray("availableProviders");
            availableSttProviders.forEach(available::add);
            long now = System.currentTimeMillis();
            sendDebug(session, "asr", "error", "Unsupported STT provider requested", now, now, extra);
            sendJson(session, error("STT_PROVIDER_UNAVAILABLE", "不支持的语音识别服务: " + requestedProvider));
            ctx.turnActive.set(false);
            ctx.capturing.set(false);
            return;
        }
        ctx.sttProvider = provider;
        ctx.turnActive.set(true);
        ctx.capturing.set(true);
        log.info("【流式会话】已选择 STT 服务：{}", ctx.sttProvider);
        ObjectNode listening = event("listening");
        listening.put("sttProvider", ctx.sttProvider);
        listening.put("sttProviderName", sttDisplayNames.getOrDefault(ctx.sttProvider, ctx.sttProvider));
        sendJson(session, listening);
        startStreamingStt(session, ctx);
    }

    private void handleAudio(SessionContext ctx, JsonNode node) {
        if (!ctx.capturing.get()) {
            log.trace("【流式会话】忽略音频片段：当前未处于采集状态");
            return;
        }
        String chunkBase64 = node.path("chunk").asText("");
        if (!StringUtils.hasText(chunkBase64)) {
            log.trace("【流式会话】收到空的音频片段，忽略");
            return;
        }
        byte[] bytes = Base64.getDecoder().decode(chunkBase64);
        PipedOutputStream output = ctx.audioOutput;
        if (output == null) {
            log.warn("【流式会话】音频输出管道尚未就绪，丢弃当前片段");
            return;
        }
        try {
            output.write(bytes);
        } catch (IOException ex) {
            log.warn("【流式会话】写入音频片段失败", ex);
            ctx.capturing.set(false);
        }
    }

    private void handleStop(WebSocketSession session, SessionContext ctx) {
        if (!ctx.capturing.compareAndSet(true, false)) {
            log.trace("【流式会话】收到 stop 指令但当前未采集音频");
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
        StreamingSpeechToTextClient sttClient = sttClients.get(ctx.sttProvider);
        if (sttClient == null) {
            long now = System.currentTimeMillis();
            ObjectNode extra = mapper.createObjectNode();
            extra.put("provider", ctx.sttProvider == null ? "" : ctx.sttProvider);
            extra.put("providerName", sttDisplayNames.getOrDefault(ctx.sttProvider, ctx.sttProvider == null ? "" : ctx.sttProvider));
            ArrayNode available = extra.putArray("availableProviders");
            availableSttProviders.forEach(available::add);
            sendDebug(session, "asr", "error", "Requested STT provider is not configured", now, now, extra);
            sendSafely(session, error("STT_PROVIDER_UNAVAILABLE", "语音识别服务不可用: " + ctx.sttProvider));
            ctx.capturing.set(false);
            ctx.turnActive.set(false);
            ctx.processing.set(false);
            return;
        }
        ctx.initAudioPipe();
        ctx.asrStartMs = System.currentTimeMillis();
        log.info("【流式会话】启动语音识别，提供者：{}", ctx.sttProvider);
        ObjectNode extra = mapper.createObjectNode();
        extra.put("sampleRate", ctx.inputFormat.sampleRate());
        extra.put("channels", ctx.inputFormat.channels());
        extra.put("bitDepth", ctx.inputFormat.bitDepth());
        extra.put("provider", ctx.sttProvider);
        extra.put("providerName", sttDisplayNames.getOrDefault(ctx.sttProvider, ctx.sttProvider));
        sendDebug(session, "asr", "start", "ASR streaming started", ctx.asrStartMs, null, extra);
        executor.execute(() -> {
            try {
                sttClient.streamRecognize(ctx.audioInput, result -> handleSttResult(session, ctx, result));
            } catch (Exception ex) {
                log.warn("【流式会话】语音识别流程出现异常", ex);
                long end = System.currentTimeMillis();
                ObjectNode errorExtra = mapper.createObjectNode();
                errorExtra.put("message", ex.getMessage());
                errorExtra.put("provider", ctx.sttProvider);
                errorExtra.put("providerName", sttDisplayNames.getOrDefault(ctx.sttProvider, ctx.sttProvider));
                sendDebug(session, "asr", "error", "Streaming STT failed", ctx.asrStartMs, end, errorExtra);
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
            long end = System.currentTimeMillis();
            ObjectNode extra = mapper.createObjectNode();
            extra.put("final", true);
            extra.put("length", text.length());
            extra.put("provider", ctx.sttProvider);
            if (StringUtils.hasText(text)) {
                extra.put("text", text);
            }
            sendDebug(session, "asr", "complete", "ASR streaming finished", ctx.asrStartMs, end, extra);
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
            ctx.llmStartMs = System.currentTimeMillis();
            ObjectNode llmStartExtra = mapper.createObjectNode();
            llmStartExtra.put("historySize", ctx.history.size());
            llmStartExtra.put("userTextLength", userText.length());
            llmStartExtra.put("userTextPreview", userText.length() > 160 ? userText.substring(0, 160) : userText);
            llmStartExtra.set("requestHistory", toHistoryArray(ctx.history));
            sendDebug(session, "llm", "start", "LLM streaming started", ctx.llmStartMs, null, llmStartExtra);
            try {
                llmClient.streamChat(new ArrayList<>(ctx.history), delta -> {
                    if (!StringUtils.hasText(delta)) {
                        return;
                    }
                    accumulated.append(delta);
                    pendingSentence.append(delta);
                    sendSafely(session, assistantDelta(delta));
                    emitSentences(session, ctx, pendingSentence);
                }, completion -> {
                    String done = completion.text();
                    String finalText = StringUtils.hasText(done) ? done : accumulated.toString();
                    if (StringUtils.hasText(finalText)) {
                        emitResidualSentence(session, ctx, pendingSentence);
                        ctx.history.add(new ConversationMessage(ConversationRole.ASSISTANT, finalText));
                    }
                    long llmEnd = System.currentTimeMillis();
                    ObjectNode llmExtra = mapper.createObjectNode();
                    llmExtra.put("finalTextLength", finalText.length());
                    llmExtra.put("responseText", finalText);
                    if (completion.metadata() != null && !completion.metadata().isEmpty()) {
                        llmExtra.set("metadata", mapper.valueToTree(completion.metadata()));
                    }
                    sendDebug(session, "llm", "complete", "LLM streaming finished", ctx.llmStartMs, llmEnd, llmExtra);
                    ctx.ttsChain.whenComplete((ignored, throwable) -> {
                        if (throwable != null) {
                            ObjectNode ttsErrorExtra = mapper.createObjectNode();
                            ttsErrorExtra.put("message", throwable.getMessage());
                            sendDebug(session, "tts", "error", "TTS chain failed", ctx.ttsStartMs, System.currentTimeMillis(), ttsErrorExtra);
                            sendSafely(session, error("TTS_ERROR", throwable.getMessage()));
                        }
                        long ttsEnd = System.currentTimeMillis();
                        ObjectNode complete = event("tts_complete");
                        complete.set("history", toHistoryArray(ctx.history));
                        complete.put("sampleRate", ttsProperties.getSampleRate());
                        complete.put("channels", ttsProperties.getChannels());
                        sendSafely(session, complete);
                        if (ctx.ttsStartMs > 0) {
                            ObjectNode ttsExtra = mapper.createObjectNode();
                            ttsExtra.put("sentences", ctx.ttsIndex.get());
                            sendDebug(session, "tts", "complete", "TTS playback finished", ctx.ttsStartMs, ttsEnd, ttsExtra);
                        }
                        ctx.turnActive.set(false);
                        ctx.processing.set(false);
                    });
                });
            } catch (Exception ex) {
                ObjectNode errExtra = mapper.createObjectNode();
                errExtra.put("message", ex.getMessage());
                sendDebug(session, "llm", "error", "LLM streaming failed", ctx.llmStartMs, System.currentTimeMillis(), errExtra);
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
        ctx.ttsChain = ctx.ttsChain.thenRunAsync(() -> streamSentenceTts(session, ctx, sentence), executor);
    }

    private void streamSentenceTts(WebSocketSession session, SessionContext ctx, String sentence) {
        AtomicBoolean delivered = new AtomicBoolean(false);
        AtomicInteger chunkCount = new AtomicInteger();
        CompletableFuture<Void> completed = new CompletableFuture<>();
        int sentenceIndex = ctx.ttsIndex.incrementAndGet();
        long start = System.currentTimeMillis();
        if (ctx.ttsStartMs == 0) {
            ctx.ttsStartMs = start;
        }
        ObjectNode startExtra = mapper.createObjectNode();
        startExtra.put("sentenceIndex", sentenceIndex);
        startExtra.put("textLength", sentence.length());
        startExtra.put("text", sentence);
        startExtra.put("textPreview", sentence.length() > 160 ? sentence.substring(0, 160) : sentence);
        sendDebug(session, "tts", "start", "Streaming TTS sentence", start, null, startExtra);
        try {
            streamingTtsClient.streamSynthesize(sentence, null, chunk -> {
                if (chunk == null || chunk.length == 0) {
                    return;
                }
                delivered.set(true);
                chunkCount.incrementAndGet();
                sendAudioChunk(session, chunk, ttsProperties.getSampleRate(), ttsProperties.getChannels());
            }, () -> completed.complete(null));
            completed.join();
        } catch (Exception ex) {
            log.warn("【流式会话】流式语音合成失败，准备回退到阻塞模式", ex);
            long errorTime = System.currentTimeMillis();
            ObjectNode errorExtra = mapper.createObjectNode();
            errorExtra.put("sentenceIndex", sentenceIndex);
            errorExtra.put("message", ex.getMessage());
            sendDebug(session, "tts", "error", "Streaming TTS failed", start, errorTime, errorExtra);
            completed.completeExceptionally(ex);
        }

        if (!delivered.get()) {
            long fallbackStart = System.currentTimeMillis();
            ObjectNode fallbackExtra = mapper.createObjectNode();
            fallbackExtra.put("sentenceIndex", sentenceIndex);
            fallbackExtra.put("textLength", sentence.length());
            fallbackExtra.put("textPreview", sentence.length() > 160 ? sentence.substring(0, 160) : sentence);
            sendDebug(session, "tts", "fallback-start", "Fallback TTS synthesize", fallbackStart, null, fallbackExtra);
            try {
                byte[] audio = blockingTtsClient.synthesize(sentence, null);
                if (audio != null && audio.length > 0) {
                    log.debug("【流式会话】已使用阻塞式 TTS 回退，文本：{}", sentence);
                    chunkAndSendAudio(session, audio);
                    long fallbackEnd = System.currentTimeMillis();
                    fallbackExtra.put("bytes", audio.length);
                    sendDebug(session, "tts", "fallback-complete", "Fallback TTS finished", fallbackStart, fallbackEnd, fallbackExtra);
                }
            } catch (Exception ex) {
                ObjectNode fallbackError = mapper.createObjectNode();
                fallbackError.put("sentenceIndex", sentenceIndex);
                fallbackError.put("textLength", sentence.length());
                fallbackError.put("message", ex.getMessage());
                sendDebug(session, "tts", "fallback-error", "Fallback TTS failed", fallbackStart, System.currentTimeMillis(), fallbackError);
                sendSafely(session, error("TTS_ERROR", ex.getMessage()));
            }
        } else {
            long end = System.currentTimeMillis();
            ObjectNode completeExtra = mapper.createObjectNode();
            completeExtra.put("sentenceIndex", sentenceIndex);
            completeExtra.put("chunks", chunkCount.get());
            completeExtra.put("textLength", sentence.length());
            sendDebug(session, "tts", "sentence-complete", "Streaming TTS sentence finished", start, end, completeExtra);
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

    private void sendDebug(WebSocketSession session,
                           String stage,
                           String status,
                           String message,
                           Long startMillis,
                           Long endMillis,
                           ObjectNode extra) {
        ObjectNode node = event("debug");
        node.put("timestamp", Instant.now().toString());
        node.put("stage", stage);
        node.put("status", status);
        if (StringUtils.hasText(message)) {
            node.put("message", message);
        }
        if (startMillis != null && startMillis > 0) {
            node.put("startTime", Instant.ofEpochMilli(startMillis).toString());
        }
        if (endMillis != null && endMillis > 0) {
            node.put("endTime", Instant.ofEpochMilli(endMillis).toString());
        }
        if (startMillis != null && endMillis != null && startMillis > 0 && endMillis >= startMillis) {
            node.put("durationMs", endMillis - startMillis);
        }
        if (extra != null && extra.size() > 0) {
            node.set("extra", extra);
        }
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
            log.warn("【流式会话】发送 WebSocket 消息失败", ex);
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

    private String extractProviderId(String beanName, StreamingSpeechToTextClient client) {
        if (client instanceof NamedService named) {
            String id = sanitizeProviderId(named.id());
            if (StringUtils.hasText(id)) {
                return id;
            }
        }
        return sanitizeProviderId(beanName);
    }

    private String extractDisplayName(String providerId, StreamingSpeechToTextClient client) {
        if (client instanceof NamedService named) {
            String label = named.displayName();
            if (StringUtils.hasText(label)) {
                return label;
            }
        }
        return providerId;
    }

    private String sanitizeProviderId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        normalized.chars()
                .filter(ch -> ch == '_' || ch == '-' || Character.isLetterOrDigit(ch))
                .forEach(ch -> builder.append((char) ch));
        return builder.toString();
    }

    private String resolveSttProvider(String requestedProvider) {
        String normalized = normalizeProviderKey(requestedProvider);
        if (StringUtils.hasText(normalized)) {
            return sttClients.containsKey(normalized) ? normalized : null;
        }
        return defaultSttProvider;
    }

    private String determineDefaultProvider(Map<String, StreamingSpeechToTextClient> clients) {
        if (clients.containsKey("sherpa")) {
            return "sherpa";
        }
        return clients.keySet().iterator().next();
    }

    private String normalizeProviderKey(String name) {
        return sanitizeProviderId(name);
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
        String sttProvider;
        PipedInputStream audioInput;
        PipedOutputStream audioOutput;
        final StringBuilder transcriptBuffer = new StringBuilder();
        CompletableFuture<Void> ttsChain = CompletableFuture.completedFuture(null);
        long asrStartMs;
        long llmStartMs;
        long ttsStartMs;
        final AtomicInteger ttsIndex = new AtomicInteger();

        SessionContext(String defaultProvider) {
            this.sttProvider = defaultProvider;
        }

        void resetTurn() {
            transcriptBuffer.setLength(0);
            ttsChain = CompletableFuture.completedFuture(null);
            asrStartMs = 0L;
            llmStartMs = 0L;
            ttsStartMs = 0L;
            ttsIndex.set(0);
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
