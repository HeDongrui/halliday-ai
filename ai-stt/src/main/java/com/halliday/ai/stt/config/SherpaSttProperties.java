package com.halliday.ai.stt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sherpa WebSocket ASR 服务的配置项。
 */
@Data
@ConfigurationProperties(prefix = "ai.stt")
public class SherpaSttProperties {

    /**
     * WebSocket 地址，例如 ws://127.0.0.1:8000/asr?samplerate=16000。
     */
    private String wsUrl = "ws://127.0.0.1:8000/asr?samplerate=16000";

    /**
     * PCM 帧大小，默认为 20ms 对应的字节数（16kHz * 2 字节 * 0.02s = 640）。
     */
    private int frameBytes = 640;

    /**
     * STT 连接建立超时时间，毫秒。
     */
    private long connectTimeoutMs = 10_000;

    /**
     * STT 读取超时时间，毫秒。
     */
    private long readTimeoutMs = 600_000;

    /**
     * PCM 写入缓冲区大小，默认 64KB。
     */
    private int bufferSize = 64 * 1024;
}
