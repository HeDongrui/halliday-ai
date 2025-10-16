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
import com.halliday.ai.tts.kokoro.KokoroTextToSpeechClient;
import com.halliday.ai.tts.kokoro.KokoroStreamingTextToSpeechClient;
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

    @Bean
    public SpeechToTextClient speechToTextClient(SherpaSttProperties properties, ObjectMapper objectMapper) {
        return new SherpaSpeechToTextClient(properties, objectMapper);
    }

    @Bean
    public LanguageModelClient languageModelClient(OllamaLlmProperties properties, ObjectMapper objectMapper) {
        return new OllamaChatClient(properties, objectMapper);
    }

    @Bean
    public TextToSpeechClient textToSpeechClient(KokoroTtsProperties properties, ObjectMapper objectMapper) {
        return new KokoroTextToSpeechClient(properties, objectMapper);
    }

    @Bean
    public StreamingLanguageModelClient streamingLanguageModelClient(OllamaLlmProperties properties, ObjectMapper mapper) {
        return new OllamaStreamingChatClient(properties, mapper);
    }

    @Bean
    public StreamingTextToSpeechClient streamingTextToSpeechClient(KokoroTtsProperties properties, ObjectMapper mapper) {
        return new KokoroStreamingTextToSpeechClient(properties, mapper);
    }

    @Bean(name = "sherpa")
    public StreamingSpeechToTextClient sherpaStreamingSpeechToTextClient(SherpaSttProperties sherpaProperties,
                                                                         ObjectMapper mapper) {
        return new SherpaStreamingSpeechToTextClient(sherpaProperties, mapper);
    }

    @Bean(name = "azure")
    @ConditionalOnProperty(prefix = "ai.stt.azure", name = "enabled", havingValue = "true")
    public StreamingSpeechToTextClient azureStreamingSpeechToTextClient(AzureSttProperties azureProperties) {
        if (!azureProperties.hasCredentials()) {
            throw new IllegalStateException("Azure STT is enabled but credentials or region/endpoint are missing");
        }
        return new AzureStreamingSpeechToTextClient(azureProperties);
    }
}
