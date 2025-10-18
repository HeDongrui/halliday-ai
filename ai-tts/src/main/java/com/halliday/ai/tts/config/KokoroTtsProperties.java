package com.halliday.ai.tts.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kokoro TTS 服务配置项。
 */
@Getter
@Setter
@Accessors(fluent = true)
@ConfigurationProperties(prefix = "ai.tts")
public class KokoroTtsProperties {

    private static final Logger log = LoggerFactory.getLogger(KokoroTtsProperties.class);

    /**
     * HTTP 音频合成地址，例如 http://127.0.0.1:8880/v1/audio/speech。
     */
    private String url = "http://127.0.0.1:8880/v1/audio/speech";

    /**
     * 默认语音名称。
     */
    private String voice = "af_heart";

    /**
     * 默认输出格式。
     */
    private String format = "pcm";

    /**
     * 目标采样率。
     */
    private int sampleRate = 16_000;

    /**
     * 声道数。
     */
    private int channels = 1;

    /**
     * 位深。
     */
    private int bitDepth = 16;

    /**
     * 连接超时时间，毫秒。
     */
    private long connectTimeoutMs = 5_000;

    /**
     * 读取超时时间，毫秒。
     */
    private long readTimeoutMs = 300_000;

    /**
     * WebSocket 流式合成地址，例如 ws://127.0.0.1:8880/v1/ws/tts/stream。
     */
    private String wsUrl;

    public String getUrl() {
        String value = url();
        log.debug("【Kokoro 配置】读取 url：{}", value);
        return value;
    }

    public void setUrl(String url) {
        log.debug("【Kokoro 配置】设置 url：{}", url);
        url(url);
    }

    public String getVoice() {
        String value = voice();
        log.debug("【Kokoro 配置】读取 voice：{}", value);
        return value;
    }

    public void setVoice(String voice) {
        log.debug("【Kokoro 配置】设置 voice：{}", voice);
        voice(voice);
    }

    public String getFormat() {
        String value = format();
        log.debug("【Kokoro 配置】读取 format：{}", value);
        return value;
    }

    public void setFormat(String format) {
        log.debug("【Kokoro 配置】设置 format：{}", format);
        format(format);
    }

    public int getSampleRate() {
        int value = sampleRate();
        log.debug("【Kokoro 配置】读取 sampleRate：{}", value);
        return value;
    }

    public void setSampleRate(int sampleRate) {
        log.debug("【Kokoro 配置】设置 sampleRate：{}", sampleRate);
        sampleRate(sampleRate);
    }

    public int getChannels() {
        int value = channels();
        log.debug("【Kokoro 配置】读取 channels：{}", value);
        return value;
    }

    public void setChannels(int channels) {
        log.debug("【Kokoro 配置】设置 channels：{}", channels);
        channels(channels);
    }

    public int getBitDepth() {
        int value = bitDepth();
        log.debug("【Kokoro 配置】读取 bitDepth：{}", value);
        return value;
    }

    public void setBitDepth(int bitDepth) {
        log.debug("【Kokoro 配置】设置 bitDepth：{}", bitDepth);
        bitDepth(bitDepth);
    }

    public long getConnectTimeoutMs() {
        long value = connectTimeoutMs();
        log.debug("【Kokoro 配置】读取 connectTimeoutMs：{}", value);
        return value;
    }

    public void setConnectTimeoutMs(long connectTimeoutMs) {
        log.debug("【Kokoro 配置】设置 connectTimeoutMs：{}", connectTimeoutMs);
        connectTimeoutMs(connectTimeoutMs);
    }

    public long getReadTimeoutMs() {
        long value = readTimeoutMs();
        log.debug("【Kokoro 配置】读取 readTimeoutMs：{}", value);
        return value;
    }

    public void setReadTimeoutMs(long readTimeoutMs) {
        log.debug("【Kokoro 配置】设置 readTimeoutMs：{}", readTimeoutMs);
        readTimeoutMs(readTimeoutMs);
    }

    public String getWsUrl() {
        String value = wsUrl();
        log.debug("【Kokoro 配置】读取 wsUrl：{}", value);
        return value;
    }

    public void setWsUrl(String wsUrl) {
        log.debug("【Kokoro 配置】设置 wsUrl：{}", wsUrl);
        wsUrl(wsUrl);
    }
}
