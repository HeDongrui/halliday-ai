package com.halliday.ai.stt.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Azure Speech-to-Text streaming configuration.
 */
@Getter
@Setter
@Accessors(fluent = true)
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
        boolean value = enabled();
        log.debug("【Azure 配置】读取 enabled：{}", value);
        return value;
    }

    public void setEnabled(boolean enabled) {
        log.debug("【Azure 配置】设置 enabled：{}", enabled);
        enabled(enabled);
    }

    public String getSubscriptionKey() {
        String value = subscriptionKey();
        log.debug("【Azure 配置】读取 subscriptionKey 是否为空：{}", value == null || value.isBlank());
        return value;
    }

    public void setSubscriptionKey(String subscriptionKey) {
        log.debug("【Azure 配置】设置 subscriptionKey 是否为空：{}", subscriptionKey == null || subscriptionKey.isBlank());
        subscriptionKey(subscriptionKey);
    }

    public String getRegion() {
        String value = region();
        log.debug("【Azure 配置】读取 region：{}", value);
        return value;
    }

    public void setRegion(String region) {
        log.debug("【Azure 配置】设置 region：{}", region);
        region(region);
    }

    public String getEndpoint() {
        String value = endpoint();
        log.debug("【Azure 配置】读取 endpoint：{}", value);
        return value;
    }

    public void setEndpoint(String endpoint) {
        log.debug("【Azure 配置】设置 endpoint：{}", endpoint);
        endpoint(endpoint);
    }

    public String getLanguage() {
        String value = language();
        log.debug("【Azure 配置】读取 language：{}", value);
        return value;
    }

    public void setLanguage(String language) {
        log.debug("【Azure 配置】设置 language：{}", language);
        language(language);
    }

    public int getSampleRate() {
        int value = sampleRate();
        log.debug("【Azure 配置】读取 sampleRate：{}", value);
        return value;
    }

    public void setSampleRate(int sampleRate) {
        log.debug("【Azure 配置】设置 sampleRate：{}", sampleRate);
        sampleRate(sampleRate);
    }

    public int getChannels() {
        int value = channels();
        log.debug("【Azure 配置】读取 channels：{}", value);
        return value;
    }

    public void setChannels(int channels) {
        log.debug("【Azure 配置】设置 channels：{}", channels);
        channels(channels);
    }

    public int getBitDepth() {
        int value = bitDepth();
        log.debug("【Azure 配置】读取 bitDepth：{}", value);
        return value;
    }

    public void setBitDepth(int bitDepth) {
        log.debug("【Azure 配置】设置 bitDepth：{}", bitDepth);
        bitDepth(bitDepth);
    }

    public int getReadBufferSize() {
        int value = readBufferSize();
        log.debug("【Azure 配置】读取 readBufferSize：{}", value);
        return value;
    }

    public void setReadBufferSize(int readBufferSize) {
        log.debug("【Azure 配置】设置 readBufferSize：{}", readBufferSize);
        readBufferSize(readBufferSize);
    }

    public long getOperationTimeoutMs() {
        long value = operationTimeoutMs();
        log.debug("【Azure 配置】读取 operationTimeoutMs：{}", value);
        return value;
    }

    public void setOperationTimeoutMs(long operationTimeoutMs) {
        log.debug("【Azure 配置】设置 operationTimeoutMs：{}", operationTimeoutMs);
        operationTimeoutMs(operationTimeoutMs);
    }

    /**
     * @return whether Azure STT has the minimum credentials to start.
     */
    public boolean hasCredentials() {
        String key = subscriptionKey();
        String endpointValue = endpoint();
        String regionValue = region();
        boolean available = key != null && !key.isBlank()
                && ((endpointValue != null && !endpointValue.isBlank())
                || (regionValue != null && !regionValue.isBlank()));
        log.debug("【Azure 配置】检测凭据是否完整：{}", available);
        return available;
    }
}

