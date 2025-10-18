package com.halliday.ai.tts.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kokoro TTS 服务配置项。
 */
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
        log.debug("【Kokoro 配置】读取 url：{}", url);
        return url;
    }

    public void setUrl(String url) {
        log.debug("【Kokoro 配置】设置 url：{}", url);
        this.url = url;
    }

    public String getVoice() {
        log.debug("【Kokoro 配置】读取 voice：{}", voice);
        return voice;
    }

    public void setVoice(String voice) {
        log.debug("【Kokoro 配置】设置 voice：{}", voice);
        this.voice = voice;
    }

    public String getFormat() {
        log.debug("【Kokoro 配置】读取 format：{}", format);
        return format;
    }

    public void setFormat(String format) {
        log.debug("【Kokoro 配置】设置 format：{}", format);
        this.format = format;
    }

    public int getSampleRate() {
        log.debug("【Kokoro 配置】读取 sampleRate：{}", sampleRate);
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        log.debug("【Kokoro 配置】设置 sampleRate：{}", sampleRate);
        this.sampleRate = sampleRate;
    }

    public int getChannels() {
        log.debug("【Kokoro 配置】读取 channels：{}", channels);
        return channels;
    }

    public void setChannels(int channels) {
        log.debug("【Kokoro 配置】设置 channels：{}", channels);
        this.channels = channels;
    }

    public int getBitDepth() {
        log.debug("【Kokoro 配置】读取 bitDepth：{}", bitDepth);
        return bitDepth;
    }

    public void setBitDepth(int bitDepth) {
        log.debug("【Kokoro 配置】设置 bitDepth：{}", bitDepth);
        this.bitDepth = bitDepth;
    }

    public long getConnectTimeoutMs() {
        log.debug("【Kokoro 配置】读取 connectTimeoutMs：{}", connectTimeoutMs);
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(long connectTimeoutMs) {
        log.debug("【Kokoro 配置】设置 connectTimeoutMs：{}", connectTimeoutMs);
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public long getReadTimeoutMs() {
        log.debug("【Kokoro 配置】读取 readTimeoutMs：{}", readTimeoutMs);
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(long readTimeoutMs) {
        log.debug("【Kokoro 配置】设置 readTimeoutMs：{}", readTimeoutMs);
        this.readTimeoutMs = readTimeoutMs;
    }

    public String getWsUrl() {
        log.debug("【Kokoro 配置】读取 wsUrl：{}", wsUrl);
        return wsUrl;
    }

    public void setWsUrl(String wsUrl) {
        log.debug("【Kokoro 配置】设置 wsUrl：{}", wsUrl);
        this.wsUrl = wsUrl;
    }
}
