package com.halliday.ai.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chat Completions 兼容 LLM 相关配置，通过 Spring Boot 配置文件注入。
 */
@ConfigurationProperties(prefix = "ai.llm")
public class OllamaLlmProperties {

    /**
     * Chat Completions 兼容接口的完整地址，例如 http://127.0.0.1:3000/v1/chat/completions。
     */
    private String baseUrl = "http://127.0.0.1:3000/v1/chat/completions";

    /**
     * 指定模型名称，例如 llama3.1。
     */
    private String model = "llama3.1";

    /**
     * 接口访问所需的 API Key，可留空。
     */
    private String apiKey = "";

    /**
     * 采样温度，取值范围通常为 0~1。
     */
    private double temperature = 0.7;

    /**
     * Top-P 采样阈值。
     */
    private double topP = 1.0;

    /**
     * 默认系统提示词，用于引导模型风格。
     */
    private String systemPrompt = "You are an English AI assistant. Always respond in English with concise answers.";

    /**
     * HTTP 连接超时时间，毫秒。
     */
    private long connectTimeoutMs = 5_000;

    /**
     * HTTP 读取超时时间，毫秒。
     */
    private long readTimeoutMs = 300_000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getTopP() {
        return topP;
    }

    public void setTopP(double topP) {
        this.topP = topP;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
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
}
