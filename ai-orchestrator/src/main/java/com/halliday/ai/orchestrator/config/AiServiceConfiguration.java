package com.halliday.ai.orchestrator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.halliday.ai.llm.config.OllamaLlmProperties;
import com.halliday.ai.llm.core.LanguageModelClient;
import com.halliday.ai.llm.ollama.OllamaChatClient;
import com.halliday.ai.llm.core.StreamingLanguageModelClient;
import com.halliday.ai.llm.ollama.OllamaStreamingChatClient;
import com.halliday.ai.stt.azure.AzureStreamingSpeechToTextClient;
import com.halliday.ai.stt.config.AzureSttProperties;
import com.halliday.ai.stt.config.SherpaSttProperties;
import com.halliday.ai.stt.core.SpeechToTextClient;
import com.halliday.ai.stt.core.StreamingSpeechToTextClient;
import com.halliday.ai.stt.sherpa.SherpaSpeechToTextClient;
import com.halliday.ai.stt.sherpa.SherpaStreamingSpeechToTextClient;
import com.halliday.ai.tts.config.KokoroTtsProperties;
import com.halliday.ai.tts.core.StreamingTextToSpeechClient;
import com.halliday.ai.tts.core.TextToSpeechClient;
import com.halliday.ai.tts.kokoro.KokoroStreamingTextToSpeechClient;
import com.halliday.ai.tts.kokoro.KokoroTextToSpeechClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        SherpaSttProperties.class,
        AzureSttProperties.class,
        OllamaLlmProperties.class,
        KokoroTtsProperties.class
})
public class AiServiceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiServiceConfiguration.class);

    @Bean
    public SpeechToTextClient speechToTextClient(SherpaSttProperties properties, ObjectMapper objectMapper) {
        log.info("【服务配置】初始化 SherpaSpeechToTextClient");
        return new SherpaSpeechToTextClient(properties, objectMapper);
    }

    @Bean
    public LanguageModelClient languageModelClient(OllamaLlmProperties properties, ObjectMapper objectMapper) {
        log.info("【服务配置】初始化 OllamaChatClient");
        return new OllamaChatClient(properties, objectMapper);
    }

    @Bean
    public TextToSpeechClient textToSpeechClient(KokoroTtsProperties properties, ObjectMapper objectMapper) {
        log.info("【服务配置】初始化 KokoroTextToSpeechClient");
        return new KokoroTextToSpeechClient(properties, objectMapper);
    }

    @Bean
    public StreamingLanguageModelClient streamingLanguageModelClient(OllamaLlmProperties properties, ObjectMapper mapper) {
        log.info("【服务配置】初始化 OllamaStreamingChatClient");
        return new OllamaStreamingChatClient(properties, mapper);
    }

    @Bean
    public StreamingTextToSpeechClient streamingTextToSpeechClient(KokoroTtsProperties properties, ObjectMapper mapper) {
        log.info("【服务配置】初始化 KokoroStreamingTextToSpeechClient");
        return new KokoroStreamingTextToSpeechClient(properties, mapper);
    }

    @Bean(name = "sherpa")
    public StreamingSpeechToTextClient sherpaStreamingSpeechToTextClient(SherpaSttProperties sherpaProperties,
                                                                         ObjectMapper mapper) {
        log.info("【服务配置】初始化 SherpaStreamingSpeechToTextClient");
        return new SherpaStreamingSpeechToTextClient(sherpaProperties, mapper);
    }

    @Bean(name = "azure")
    @ConditionalOnProperty(prefix = "ai.stt.azure", name = "enabled", havingValue = "true")
    public StreamingSpeechToTextClient azureStreamingSpeechToTextClient(AzureSttProperties azureProperties) {
        log.info("【服务配置】检测 Azure 流式识别配置是否有效");
        if (!azureProperties.hasCredentials()) {
            log.error("【服务配置】Azure STT 已启用但缺少凭据或区域信息");
            throw new IllegalStateException("Azure STT is enabled but credentials or region/endpoint are missing");
        }
        log.info("【服务配置】初始化 AzureStreamingSpeechToTextClient");
        return new AzureStreamingSpeechToTextClient(azureProperties);
    }
}
