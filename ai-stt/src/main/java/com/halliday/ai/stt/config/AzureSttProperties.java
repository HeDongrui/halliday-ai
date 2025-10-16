package com.halliday.ai.stt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Azure Speech-to-Text streaming configuration.
 */
@ConfigurationProperties(prefix = "ai.stt.azure")
public class AzureSttProperties {

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
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSubscriptionKey() {
        return subscriptionKey;
    }

    public void setSubscriptionKey(String subscriptionKey) {
        this.subscriptionKey = subscriptionKey;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
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

    public int getReadBufferSize() {
        return readBufferSize;
    }

    public void setReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
    }

    public long getOperationTimeoutMs() {
        return operationTimeoutMs;
    }

    public void setOperationTimeoutMs(long operationTimeoutMs) {
        this.operationTimeoutMs = operationTimeoutMs;
    }

    /**
     * @return whether Azure STT has the minimum credentials to start.
     */
    public boolean hasCredentials() {
        return subscriptionKey != null && !subscriptionKey.isBlank()
                && ((endpoint != null && !endpoint.isBlank()) || (region != null && !region.isBlank()));
    }
}

