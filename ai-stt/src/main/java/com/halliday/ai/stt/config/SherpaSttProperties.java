package com.halliday.ai.stt.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sherpa WebSocket ASR 服务的配置项。
 */
@Getter
@Setter
@Accessors(fluent = true)
@ConfigurationProperties(prefix = "ai.stt")
public class SherpaSttProperties {

    private static final Logger log = LoggerFactory.getLogger(SherpaSttProperties.class);

    /**
     * WebSocket 地址，例如 ws://127.0.0.1:8000/asr?samplerate=16000。
     */
    private String wsUrl;

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
        String value = wsUrl();
        log.debug("【Sherpa 配置】读取 wsUrl：{}", value);
        return value;
    }

    public void setWsUrl(String wsUrl) {
        log.debug("【Sherpa 配置】设置 wsUrl：{}", wsUrl);
        wsUrl(wsUrl);
    }

    public int getFrameBytes() {
        int value = frameBytes();
        log.debug("【Sherpa 配置】读取 frameBytes：{}", value);
        return value;
    }

    public void setFrameBytes(int frameBytes) {
        log.debug("【Sherpa 配置】设置 frameBytes：{}", frameBytes);
        frameBytes(frameBytes);
    }

    public long getConnectTimeoutMs() {
        long value = connectTimeoutMs();
        log.debug("【Sherpa 配置】读取 connectTimeoutMs：{}", value);
        return value;
    }

    public void setConnectTimeoutMs(long connectTimeoutMs) {
        log.debug("【Sherpa 配置】设置 connectTimeoutMs：{}", connectTimeoutMs);
        connectTimeoutMs(connectTimeoutMs);
    }

    public long getReadTimeoutMs() {
        long value = readTimeoutMs();
        log.debug("【Sherpa 配置】读取 readTimeoutMs：{}", value);
        return value;
    }

    public void setReadTimeoutMs(long readTimeoutMs) {
        log.debug("【Sherpa 配置】设置 readTimeoutMs：{}", readTimeoutMs);
        readTimeoutMs(readTimeoutMs);
    }

    public long getResultTimeoutMs() {
        long value = resultTimeoutMs();
        log.debug("【Sherpa 配置】读取 resultTimeoutMs：{}", value);
        return value;
    }

    public void setResultTimeoutMs(long resultTimeoutMs) {
        log.debug("【Sherpa 配置】设置 resultTimeoutMs：{}", resultTimeoutMs);
        resultTimeoutMs(resultTimeoutMs);
    }
}
