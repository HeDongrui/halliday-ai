package com.halliday.ai.tts.kokoro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.halliday.ai.common.audio.AudioFormat;
import com.halliday.ai.common.exception.AiServiceException;
import com.halliday.ai.tts.config.KokoroTtsProperties;
import com.halliday.ai.tts.core.TextToSpeechClient;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.Response;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Base64;
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
                return readAudioPayload(response);
            }
        } catch (IOException ex) {
            throw new AiServiceException("Failed to call TTS backend", ex);
        }
    }

    private byte[] readAudioPayload(Response response) throws IOException {
        ResponseBody body = Objects.requireNonNull(response.body(), "empty TTS body");
        MediaType contentType = body.contentType();
        byte[] data = body.bytes();

        if (!isJsonPayload(contentType, data)) {
            return data;
        }

        JsonNode root = mapper.readTree(data);
        String base64 = extractAudioBase64(root);
        if (!StringUtils.hasText(base64)) {
            throw new AiServiceException("TTS JSON response missing audio content");
        }
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new AiServiceException("Invalid base64 audio payload from TTS backend", ex);
        }
    }

    private boolean isJsonPayload(MediaType contentType, byte[] data) {
        if (contentType != null &&
                "application".equalsIgnoreCase(contentType.type()) &&
                "json".equalsIgnoreCase(contentType.subtype())) {
            return true;
        }
        if (data.length == 0) {
            return false;
        }
        byte first = data[0];
        return first == '{' || first == '[';
    }

    private String extractAudioBase64(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.hasNonNull("audio")) {
            return node.get("audio").asText();
        }
        if (node.hasNonNull("audio_content")) {
            return node.get("audio_content").asText();
        }
        if (node.hasNonNull("b64_json")) {
            return node.get("b64_json").asText();
        }
        if (node.has("data")) {
            for (JsonNode child : node.get("data")) {
                String nested = extractAudioBase64(child);
                if (StringUtils.hasText(nested)) {
                    return nested;
                }
            }
        }
        return null;
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
