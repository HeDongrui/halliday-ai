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
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class KokoroTextToSpeechClient implements TextToSpeechClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final KokoroTtsProperties properties;
    private final ObjectMapper mapper;
    private final OkHttpClient client;
    private final AudioFormat outputFormat;

    public KokoroTextToSpeechClient(KokoroTtsProperties properties, ObjectMapper mapper) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.client = buildClient(properties);
        this.outputFormat = new AudioFormat(
                properties.getSampleRate(),
                properties.getChannels(),
                properties.getBitDepth(),
                AudioFormat.Endianness.LITTLE
        );
    }

    private OkHttpClient buildClient(KokoroTtsProperties properties) {
        return new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public byte[] synthesize(String text, String voice) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("text must not be blank");
        }
        try {
            byte[] payload = mapper.writeValueAsBytes(buildPayload(text, voice));
            Request request = new Request.Builder()
                    .url(properties.getUrl())
                    .post(RequestBody.create(payload, JSON))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new AiServiceException("TTS request failed with status " + response.code());
                }
                return Objects.requireNonNull(response.body(), "empty TTS body").bytes();
            }
        } catch (IOException ex) {
            throw new AiServiceException("Failed to call TTS backend", ex);
        }
    }

    private Map<String, Object> buildPayload(String text, String voice) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", "kokoro");
        payload.put("voice", StringUtils.hasText(voice) ? voice : properties.getVoice());
        payload.put("input", text);
        payload.put("response_format", properties.getFormat());
        payload.put("stream", false);
        return payload;
    }

    @Override
    public AudioFormat outputFormat() {
        return outputFormat;
    }
}
