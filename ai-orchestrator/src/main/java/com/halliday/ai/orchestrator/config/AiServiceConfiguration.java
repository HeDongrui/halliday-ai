package com.halliday.ai.orchestrator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.halliday.ai.llm.config.OllamaLlmProperties;
import com.halliday.ai.llm.core.LlmService;
import com.halliday.ai.llm.ollama.OllamaLlmService;
import com.halliday.ai.stt.config.SherpaSttProperties;
import com.halliday.ai.stt.core.SttService;
import com.halliday.ai.stt.sherpa.SherpaSttService;
import com.halliday.ai.tts.config.KokoroTtsProperties;
import com.halliday.ai.tts.core.TtsService;
import com.halliday.ai.tts.kokoro.KokoroTtsService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 服务 Bean 配置，集中定义 STT/LLM/TTS 的实例化逻辑。
 */
@Configuration
@EnableConfigurationProperties({
        SherpaSttProperties.class,
        OllamaLlmProperties.class,
        KokoroTtsProperties.class
})
public class AiServiceConfiguration {

    /**
     * 构建 Sherpa STT 服务实现。
     *
     * @param properties   Sherpa 配置
     * @param objectMapper JSON 工具
     * @param registry     指标注册器
     * @return STT 服务实例
     */
    @Bean
    public SttService sttService(SherpaSttProperties properties, ObjectMapper objectMapper, MeterRegistry registry) {
        return new SherpaSttService(properties, objectMapper, registry);
    }

    /**
     * 构建 Chat Completions 兼容的 LLM 服务实现。
     *
     * @param properties   LLM 配置
     * @param objectMapper JSON 工具
     * @param registry     指标注册器
     * @return LLM 服务实例
     */
    @Bean
    public LlmService llmService(OllamaLlmProperties properties, ObjectMapper objectMapper, MeterRegistry registry) {
        return new OllamaLlmService(properties, objectMapper, registry);
    }

    /**
     * 构建 Kokoro TTS 服务实现。
     *
     * @param properties   Kokoro 配置
     * @param objectMapper JSON 工具
     * @param registry     指标注册器
     * @return TTS 服务实例
     */
    @Bean
    public TtsService ttsService(KokoroTtsProperties properties, ObjectMapper objectMapper, MeterRegistry registry) {
        return new KokoroTtsService(properties, objectMapper, registry);
    }
}
