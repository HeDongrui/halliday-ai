package com.halliday.ai.stt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sherpa WebSocket ASR 服务的配置项。
 */
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
     * 等待 Sherpa 返回最终文本的时间，毫秒。
     */
    private long resultTimeoutMs = 5_000;

    public String getWsUrl() {
        return wsUrl;
    }

    public void setWsUrl(String wsUrl) {
        this.wsUrl = wsUrl;
    }

    public int getFrameBytes() {
        return frameBytes;
    }

    public void setFrameBytes(int frameBytes) {
        this.frameBytes = frameBytes;
    }

    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public long getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(long readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public long getResultTimeoutMs() {
        return resultTimeoutMs;
    }

    public void setResultTimeoutMs(long resultTimeoutMs) {
        this.resultTimeoutMs = resultTimeoutMs;
    }
}
