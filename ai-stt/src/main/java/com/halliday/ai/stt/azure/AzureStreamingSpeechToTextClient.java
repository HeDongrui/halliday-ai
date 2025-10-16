package com.halliday.ai.stt.azure;

import com.halliday.ai.common.stt.SttResult;
import com.halliday.ai.stt.config.AzureSttProperties;
import com.halliday.ai.stt.core.StreamingSpeechToTextClient;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionCanceledEventArgs;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionEventArgs;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.audio.AudioInputStream;
import com.microsoft.cognitiveservices.speech.audio.AudioStreamFormat;
import com.microsoft.cognitiveservices.speech.audio.PushAudioInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class AzureStreamingSpeechToTextClient implements StreamingSpeechToTextClient {

    private static final Logger log = LoggerFactory.getLogger(AzureStreamingSpeechToTextClient.class);
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    private final AzureSttProperties properties;
    private final ExecutorService executor;

    public AzureStreamingSpeechToTextClient(AzureSttProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.executor = Executors.newCachedThreadPool(new AzureThreadFactory());
    }

    @Override
    public void streamRecognize(InputStream pcmStream, Consumer<SttResult> onResult) {
        Objects.requireNonNull(pcmStream, "pcmStream");
        Objects.requireNonNull(onResult, "onResult");
        if (!properties.hasCredentials()) {
            throw new IllegalStateException("Azure STT credentials are not configured");
        }
        executor.execute(() -> runRecognition(pcmStream, onResult));
    }

    private void runRecognition(InputStream pcmStream, Consumer<SttResult> onResult) {
        long timeoutMs = Math.max(1_000L, properties.getOperationTimeoutMs());
        PushAudioInputStream pushStream = createPushStream();
        CompletableFuture<Void> sessionCompleted = new CompletableFuture<>();
        try (InputStream input = pcmStream;
             SpeechConfig speechConfig = createSpeechConfig();
             AudioConfig audioConfig = AudioConfig.fromStreamInput(pushStream);
             SpeechRecognizer recognizer = createRecognizer(speechConfig, audioConfig)) {

            recognizer.recognizing.addEventListener((s, e) -> onRecognizing(e, onResult));
            recognizer.recognized.addEventListener((s, e) -> onRecognized(e, onResult));
            recognizer.canceled.addEventListener((s, e) -> onCanceled(e, onResult, sessionCompleted));
            recognizer.sessionStopped.addEventListener((s, e) -> sessionCompleted.complete(null));

            recognizer.startContinuousRecognitionAsync().get(timeoutMs, TimeUnit.MILLISECONDS);
            pumpAudio(input, pushStream);
            recognizer.stopContinuousRecognitionAsync().get(timeoutMs, TimeUnit.MILLISECONDS);
            sessionCompleted.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            log.warn("Azure streaming STT failed: {}", ex.getMessage(), ex);
            safeAccept(onResult, SttResult.builder().text("").finished(true).idx(0).build());
        } finally {
            try {
                pushStream.close();
            } catch (Exception ignored) {
            }
        }
    }

    private PushAudioInputStream createPushStream() {
        AudioStreamFormat format = AudioStreamFormat.getWaveFormatPCM(
                properties.getSampleRate(),
                (short) properties.getBitDepth(),
                (short) properties.getChannels());
        return AudioInputStream.createPushStream(format);
    }

    private SpeechConfig createSpeechConfig() throws URISyntaxException {
        SpeechConfig config;
        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            config = SpeechConfig.fromEndpoint(new URI(properties.getEndpoint()), properties.getSubscriptionKey());
        } else {
            config = SpeechConfig.fromSubscription(properties.getSubscriptionKey(), properties.getRegion());
        }
        if (properties.getLanguage() != null && !properties.getLanguage().isBlank()) {
            config.setSpeechRecognitionLanguage(properties.getLanguage());
        }
        return config;
    }

    private SpeechRecognizer createRecognizer(SpeechConfig config, AudioConfig audioConfig) {
        if (properties.getLanguage() != null && !properties.getLanguage().isBlank()) {
            return new SpeechRecognizer(config, properties.getLanguage(), audioConfig);
        }
        return new SpeechRecognizer(config, audioConfig);
    }

    private void pumpAudio(InputStream input, PushAudioInputStream pushStream) throws IOException {
        byte[] buffer = new byte[Math.max(1, properties.getReadBufferSize())];
        int read;
        while ((read = input.read(buffer)) != -1) {
            byte[] chunk = java.util.Arrays.copyOf(buffer, read);
            pushStream.write(chunk);
        }
    }

    private void onRecognizing(SpeechRecognitionEventArgs event, Consumer<SttResult> consumer) {
        if (event == null || event.getResult() == null) {
            return;
        }
        if (event.getResult().getReason() == ResultReason.RecognizingSpeech) {
            String text = event.getResult().getText();
            if (text != null && !text.isBlank()) {
                safeAccept(consumer, SttResult.builder()
                        .text(text)
                        .finished(false)
                        .idx(0)
                        .build());
            }
        }
    }

    private void onRecognized(SpeechRecognitionEventArgs event, Consumer<SttResult> consumer) {
        if (event == null || event.getResult() == null) {
            return;
        }
        switch (event.getResult().getReason()) {
            case RecognizedSpeech -> safeAccept(consumer, SttResult.builder()
                    .text(event.getResult().getText())
                    .finished(true)
                    .idx(0)
                    .build());
            case NoMatch -> safeAccept(consumer, SttResult.builder()
                    .text("")
                    .finished(true)
                    .idx(0)
                    .build());
            default -> {
                // ignore
            }
        }
    }

    private void onCanceled(SpeechRecognitionCanceledEventArgs event,
                            Consumer<SttResult> consumer,
                            CompletableFuture<Void> sessionCompleted) {
        if (event != null && event.getReason() == com.microsoft.cognitiveservices.speech.CancellationReason.Error) {
            log.warn("Azure STT canceled: code={}, details={}",
                    event.getErrorCode(), event.getErrorDetails());
        }
        safeAccept(consumer, SttResult.builder().text("").finished(true).idx(0).build());
        sessionCompleted.complete(null);
    }

    private void safeAccept(Consumer<SttResult> consumer, SttResult result) {
        try {
            consumer.accept(result);
        } catch (Exception ex) {
            log.warn("STT consumer threw exception", ex);
        }
    }

    private static class AzureThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("azure-stt-" + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
