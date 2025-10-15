package com.halliday.ai.tts.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kokoro TTS 服务配置项。
 */
@Data
@ConfigurationProperties(prefix = "ai.tts")
public class KokoroTtsProperties {

    /**
     * 流式 WebSocket 地址，例如 ws://127.0.0.1:8880/v1/ws/tts/stream。
     */
    private String wsUrl = "ws://127.0.0.1:8880/v1/ws/tts/stream";

    /**
     * HTTP 音频合成地址，例如 http://127.0.0.1:8880/v1/audio/speech。
     */
    private String httpUrl = "http://127.0.0.1:8880/v1/audio/speech";

    /**
     * 默认语音名称。
     */
    private String voice = "af_heart";

    /**
     * 默认输出格式。
     */
    private String format = "pcm";

    /**
     * 连接超时时间，毫秒。
     */
    private long connectTimeoutMs = 5_000;

    /**
     * 读取超时时间，毫秒。
     */
    private long readTimeoutMs = 300_000;
}
