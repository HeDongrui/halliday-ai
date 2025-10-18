package com.halliday.ai.stt.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Azure Speech-to-Text streaming configuration.
 */
@ConfigurationProperties(prefix = "ai.stt.azure")
public class AzureSttProperties {

    private static final Logger log = LoggerFactory.getLogger(AzureSttProperties.class);

    /**
     * Whether Azure streaming STT should be used.
     */
    private boolean enabled;

    /**
     * Azure Speech resource subscription key.
     */
    private String subscriptionKey;

    /**
     * Azure region (e.g. eastus). Required when endpoint is not provided.
     */
    private String region;

    /**
     * Optional custom endpoint URI. When provided, region is ignored.
     */
    private String endpoint;

    /**
     * Recognition language (e.g. en-US, zh-CN).
     */
    private String language = "en-US";

    /**
     * PCM sample rate of the incoming audio stream.
     */
    private int sampleRate = 16_000;

    /**
     * Number of PCM channels.
     */
    private int channels = 1;

    /**
     * PCM bit depth.
     */
    private int bitDepth = 16;

    /**
     * Size of the buffer used to read from the incoming stream.
     */
    private int readBufferSize = 3200;

    /**
     * Timeout (ms) to await recognizer start/stop operations.
     */
    private long operationTimeoutMs = 10_000;

    public boolean isEnabled() {
        log.debug("【Azure 配置】读取 enabled：{}", enabled);
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        log.debug("【Azure 配置】设置 enabled：{}", enabled);
        this.enabled = enabled;
    }

    public String getSubscriptionKey() {
        log.debug("【Azure 配置】读取 subscriptionKey 是否为空：{}", subscriptionKey == null || subscriptionKey.isBlank());
        return subscriptionKey;
    }

    public void setSubscriptionKey(String subscriptionKey) {
        log.debug("【Azure 配置】设置 subscriptionKey 是否为空：{}", subscriptionKey == null || subscriptionKey.isBlank());
        this.subscriptionKey = subscriptionKey;
    }

    public String getRegion() {
        log.debug("【Azure 配置】读取 region：{}", region);
        return region;
    }

    public void setRegion(String region) {
        log.debug("【Azure 配置】设置 region：{}", region);
        this.region = region;
    }

    public String getEndpoint() {
        log.debug("【Azure 配置】读取 endpoint：{}", endpoint);
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        log.debug("【Azure 配置】设置 endpoint：{}", endpoint);
        this.endpoint = endpoint;
    }

    public String getLanguage() {
        log.debug("【Azure 配置】读取 language：{}", language);
        return language;
    }

    public void setLanguage(String language) {
        log.debug("【Azure 配置】设置 language：{}", language);
        this.language = language;
    }

    public int getSampleRate() {
        log.debug("【Azure 配置】读取 sampleRate：{}", sampleRate);
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        log.debug("【Azure 配置】设置 sampleRate：{}", sampleRate);
        this.sampleRate = sampleRate;
    }

    public int getChannels() {
        log.debug("【Azure 配置】读取 channels：{}", channels);
        return channels;
    }

    public void setChannels(int channels) {
        log.debug("【Azure 配置】设置 channels：{}", channels);
        this.channels = channels;
    }

    public int getBitDepth() {
        log.debug("【Azure 配置】读取 bitDepth：{}", bitDepth);
        return bitDepth;
    }

    public void setBitDepth(int bitDepth) {
        log.debug("【Azure 配置】设置 bitDepth：{}", bitDepth);
        this.bitDepth = bitDepth;
    }

    public int getReadBufferSize() {
        log.debug("【Azure 配置】读取 readBufferSize：{}", readBufferSize);
        return readBufferSize;
    }

    public void setReadBufferSize(int readBufferSize) {
        log.debug("【Azure 配置】设置 readBufferSize：{}", readBufferSize);
        this.readBufferSize = readBufferSize;
    }

    public long getOperationTimeoutMs() {
        log.debug("【Azure 配置】读取 operationTimeoutMs：{}", operationTimeoutMs);
        return operationTimeoutMs;
    }

    public void setOperationTimeoutMs(long operationTimeoutMs) {
        log.debug("【Azure 配置】设置 operationTimeoutMs：{}", operationTimeoutMs);
        this.operationTimeoutMs = operationTimeoutMs;
    }

    /**
     * @return whether Azure STT has the minimum credentials to start.
     */
    public boolean hasCredentials() {
        boolean available = subscriptionKey != null && !subscriptionKey.isBlank()
                && ((endpoint != null && !endpoint.isBlank()) || (region != null && !region.isBlank()));
        log.debug("【Azure 配置】检测凭据是否完整：{}", available);
        return available;
    }
}

