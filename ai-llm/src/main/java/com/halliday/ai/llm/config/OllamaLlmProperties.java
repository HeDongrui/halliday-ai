package com.halliday.ai.llm.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chat Completions 兼容 LLM 相关配置，通过 Spring Boot 配置文件注入。
 */
@ConfigurationProperties(prefix = "ai.llm")
public class OllamaLlmProperties {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmProperties.class);

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
        log.debug("【Ollama 配置】读取 baseUrl：{}", baseUrl);
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        log.debug("【Ollama 配置】设置 baseUrl：{}", baseUrl);
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        log.debug("【Ollama 配置】读取 model：{}", model);
        return model;
    }

    public void setModel(String model) {
        log.debug("【Ollama 配置】设置 model：{}", model);
        this.model = model;
    }

    public String getApiKey() {
        log.debug("【Ollama 配置】读取 apiKey 是否为空：{}", apiKey == null || apiKey.isEmpty());
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        log.debug("【Ollama 配置】设置 apiKey 是否为空：{}", apiKey == null || apiKey.isEmpty());
        this.apiKey = apiKey;
    }

    public double getTemperature() {
        log.debug("【Ollama 配置】读取 temperature：{}", temperature);
        return temperature;
    }

    public void setTemperature(double temperature) {
        log.debug("【Ollama 配置】设置 temperature：{}", temperature);
        this.temperature = temperature;
    }

    public double getTopP() {
        log.debug("【Ollama 配置】读取 topP：{}", topP);
        return topP;
    }

    public void setTopP(double topP) {
        log.debug("【Ollama 配置】设置 topP：{}", topP);
        this.topP = topP;
    }

    public String getSystemPrompt() {
        log.debug("【Ollama 配置】读取 systemPrompt，长度：{}", systemPrompt == null ? 0 : systemPrompt.length());
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        log.debug("【Ollama 配置】设置 systemPrompt，长度：{}", systemPrompt == null ? 0 : systemPrompt.length());
        this.systemPrompt = systemPrompt;
    }

    public long getConnectTimeoutMs() {
        log.debug("【Ollama 配置】读取 connectTimeoutMs：{}", connectTimeoutMs);
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(long connectTimeoutMs) {
        log.debug("【Ollama 配置】设置 connectTimeoutMs：{}", connectTimeoutMs);
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public long getReadTimeoutMs() {
        log.debug("【Ollama 配置】读取 readTimeoutMs：{}", readTimeoutMs);
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(long readTimeoutMs) {
        log.debug("【Ollama 配置】设置 readTimeoutMs：{}", readTimeoutMs);
        this.readTimeoutMs = readTimeoutMs;
    }
}
