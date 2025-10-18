package com.halliday.ai.stt.azure;

import com.halliday.ai.common.spi.NamedService;
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

public class AzureStreamingSpeechToTextClient implements StreamingSpeechToTextClient, NamedService {

    private static final Logger log = LoggerFactory.getLogger(AzureStreamingSpeechToTextClient.class);
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    private final AzureSttProperties properties;
    private final ExecutorService executor;

    public AzureStreamingSpeechToTextClient(AzureSttProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        log.debug("【Azure 流式识别】初始化客户端，区域={}，语言={}", properties.getRegion(), properties.getLanguage());
        this.executor = Executors.newCachedThreadPool(new AzureThreadFactory());
    }

    @Override
    public String id() {
        log.debug("【Azure 流式识别】返回服务标识：azure");
        return "azure";
    }

    @Override
    public void streamRecognize(InputStream pcmStream, Consumer<SttResult> onResult) {
        Objects.requireNonNull(pcmStream, "pcmStream");
        Objects.requireNonNull(onResult, "onResult");
        if (!properties.hasCredentials()) {
            throw new IllegalStateException("Azure STT credentials are not configured");
        }
        log.info("【Azure 流式识别】提交新的识别任务");
        executor.execute(() -> runRecognition(pcmStream, onResult));
    }

    private void runRecognition(InputStream pcmStream, Consumer<SttResult> onResult) {
        long timeoutMs = Math.max(1_000L, properties.getOperationTimeoutMs());
        log.debug("【Azure 流式识别】启动识别任务，超时时长：{}ms", timeoutMs);
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
            log.warn("【Azure 流式识别】识别任务失败，原因：{}", ex.getMessage(), ex);
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
        log.debug("【Azure 流式识别】创建音频推流，采样率={}，位深={}，声道数={}",
                properties.getSampleRate(), properties.getBitDepth(), properties.getChannels());
        return AudioInputStream.createPushStream(format);
    }

    private SpeechConfig createSpeechConfig() throws URISyntaxException {
        SpeechConfig config;
        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            log.debug("【Azure 流式识别】使用自定义 Endpoint：{}", properties.getEndpoint());
            config = SpeechConfig.fromEndpoint(new URI(properties.getEndpoint()), properties.getSubscriptionKey());
        } else {
            log.debug("【Azure 流式识别】使用订阅区域：{}", properties.getRegion());
            config = SpeechConfig.fromSubscription(properties.getSubscriptionKey(), properties.getRegion());
        }
        if (properties.getLanguage() != null && !properties.getLanguage().isBlank()) {
            config.setSpeechRecognitionLanguage(properties.getLanguage());
            log.debug("【Azure 流式识别】设置识别语言：{}", properties.getLanguage());
        }
        return config;
    }

    private SpeechRecognizer createRecognizer(SpeechConfig config, AudioConfig audioConfig) {
        if (properties.getLanguage() != null && !properties.getLanguage().isBlank()) {
            log.debug("【Azure 流式识别】根据指定语言创建识别器");
            return new SpeechRecognizer(config, properties.getLanguage(), audioConfig);
        }
        log.debug("【Azure 流式识别】使用默认语言创建识别器");
        return new SpeechRecognizer(config, audioConfig);
    }

    private void pumpAudio(InputStream input, PushAudioInputStream pushStream) throws IOException {
        byte[] buffer = new byte[Math.max(1, properties.getReadBufferSize())];
        log.debug("【Azure 流式识别】开始推送音频数据，缓冲区大小：{}", buffer.length);
        int read;
        while ((read = input.read(buffer)) != -1) {
            byte[] chunk = java.util.Arrays.copyOf(buffer, read);
            pushStream.write(chunk);
        }
        log.debug("【Azure 流式识别】音频推送完成");
    }

    private void onRecognizing(SpeechRecognitionEventArgs event, Consumer<SttResult> consumer) {
        if (event == null || event.getResult() == null) {
            return;
        }
        if (event.getResult().getReason() == ResultReason.RecognizingSpeech) {
            String text = event.getResult().getText();
            if (text != null && !text.isBlank()) {
                log.debug("【Azure 流式识别】识别中间结果：{}", text);
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
            case RecognizedSpeech -> {
                String text = event.getResult().getText();
                log.info("【Azure 流式识别】识别完成，文本：{}", text);
                safeAccept(consumer, SttResult.builder()
                        .text(text)
                        .finished(true)
                        .idx(0)
                        .build());
            }
            case NoMatch -> {
                log.info("【Azure 流式识别】未匹配到有效文本");
                safeAccept(consumer, SttResult.builder()
                        .text("")
                        .finished(true)
                        .idx(0)
                        .build());
            }
            default -> {
                // ignore
            }
        }
    }

    private void onCanceled(SpeechRecognitionCanceledEventArgs event,
                            Consumer<SttResult> consumer,
                            CompletableFuture<Void> sessionCompleted) {
        if (event != null && event.getReason() == com.microsoft.cognitiveservices.speech.CancellationReason.Error) {
            log.warn("【Azure 流式识别】识别被取消：code={}，details={}",
                    event.getErrorCode(), event.getErrorDetails());
        }
        safeAccept(consumer, SttResult.builder().text("").finished(true).idx(0).build());
        sessionCompleted.complete(null);
    }

    private void safeAccept(Consumer<SttResult> consumer, SttResult result) {
        try {
            log.trace("【Azure 流式识别】回调识别结果，文本长度：{}，是否结束：{}",
                    result.getText() == null ? 0 : result.getText().length(), result.isFinished());
            consumer.accept(result);
        } catch (Exception ex) {
            log.warn("【Azure 流式识别】回调处理器抛出异常", ex);
        }
    }

    private static class AzureThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("azure-stt-" + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            log.debug("【Azure 流式识别】创建工作线程：{}", thread.getName());
            return thread;
        }
    }
}
