package com.halliday.ai.tts.kokoro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.halliday.ai.common.audio.AudioFormat;
import com.halliday.ai.common.exception.AiServiceException;
import com.halliday.ai.tts.config.KokoroTtsProperties;
import com.halliday.ai.tts.core.TextToSpeechClient;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class KokoroTextToSpeechClient implements TextToSpeechClient {

    private static final Logger log = LoggerFactory.getLogger(KokoroTextToSpeechClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final KokoroTtsProperties properties;
    private final ObjectMapper mapper;
    private final OkHttpClient client;
    private final AudioFormat outputFormat;

    public KokoroTextToSpeechClient(KokoroTtsProperties properties, ObjectMapper mapper) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        log.debug("【Kokoro 语音合成】初始化客户端，服务地址：{}", properties.getUrl());
        this.client = buildClient(properties);
        this.outputFormat = new AudioFormat(
                properties.getSampleRate(),
                properties.getChannels(),
                properties.getBitDepth(),
                AudioFormat.Endianness.LITTLE
        );
        log.debug("【Kokoro 语音合成】输出格式：采样率={}，声道={}，位深={}",
                outputFormat.sampleRate(), outputFormat.channels(), outputFormat.bitDepth());
    }

    private OkHttpClient buildClient(KokoroTtsProperties properties) {
        log.debug("【Kokoro 语音合成】构建 OkHttpClient，连接超时：{}ms，读取超时：{}ms",
                properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
        return new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public byte[] synthesize(String text, String voice) {
        if (!StringUtils.hasText(text)) {
            log.error("【Kokoro 语音合成】输入文本为空，拒绝合成");
            throw new IllegalArgumentException("text must not be blank");
        }
        log.info("【Kokoro 语音合成】开始合成语音，文本长度：{}", text.length());
        try {
            byte[] payload = mapper.writeValueAsBytes(buildPayload(text, voice));
            Request request = new Request.Builder()
                    .url(properties.getUrl())
                    .post(RequestBody.create(payload, JSON))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                log.debug("【Kokoro 语音合成】收到响应，HTTP 状态码：{}", response.code());
                if (!response.isSuccessful()) {
                    log.error("【Kokoro 语音合成】调用失败，状态码：{}", response.code());
                    throw new AiServiceException("TTS request failed with status " + response.code());
                }
                byte[] audio = Objects.requireNonNull(response.body(), "empty TTS body").bytes();
                log.info("【Kokoro 语音合成】合成完成，音频字节数：{}", audio.length);
                return audio;
            }
        } catch (IOException ex) {
            log.error("【Kokoro 语音合成】调用服务异常", ex);
            throw new AiServiceException("Failed to call TTS backend", ex);
        }
    }

    private Map<String, Object> buildPayload(String text, String voice) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", "kokoro");
        payload.put("voice", StringUtils.hasText(voice) ? voice : properties.getVoice());
        payload.put("input", text);
        payload.put("response_format", properties.getFormat());
        // Ask backend to render with our desired sample rate when supported
        payload.put("sample_rate", properties.getSampleRate());
        payload.put("stream", false);
        log.debug("【Kokoro 语音合成】构建请求载荷，目标音色：{}", payload.get("voice"));
        return payload;
    }

    @Override
    public AudioFormat outputFormat() {
        log.debug("【Kokoro 语音合成】获取输出格式信息");
        return outputFormat;
    }
}
