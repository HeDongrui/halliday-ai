package com.halliday.ai.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ollama LLM 相关配置，通过 Spring Boot 配置文件注入。
 */
@Data
@ConfigurationProperties(prefix = "ai.llm")
public class OllamaLlmProperties {

    /**
     * Ollama 服务基础地址，例如 http://127.0.0.1:11434。
     */
    private String baseUrl = "http://127.0.0.1:11434";

    /**
     * 指定模型名称，例如 qwen3:latest。
     */
    private String model = "qwen3:latest";

    /**
     * HTTP 连接超时时间，毫秒。
     */
    private long connectTimeoutMs = 5_000;

    /**
     * HTTP 读取超时时间，毫秒。
     */
    private long readTimeoutMs = 300_000;
}
