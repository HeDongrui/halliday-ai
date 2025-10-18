package com.halliday.ai.tts.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kokoro TTS 服务配置项。
 */
@ConfigurationProperties(prefix = "ai.tts")
public class KokoroTtsProperties {

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
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getVoice() {
        return voice;
    }

    public void setVoice(String voice) {
        this.voice = voice;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public int getChannels() {
        return channels;
    }

    public void setChannels(int channels) {
        this.channels = channels;
    }

    public int getBitDepth() {
        return bitDepth;
    }

    public void setBitDepth(int bitDepth) {
        this.bitDepth = bitDepth;
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

    public String getWsUrl() {
        return wsUrl;
    }

    public void setWsUrl(String wsUrl) {
        this.wsUrl = wsUrl;
    }
}
