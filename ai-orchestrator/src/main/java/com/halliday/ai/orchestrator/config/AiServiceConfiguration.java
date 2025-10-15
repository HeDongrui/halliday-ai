package com.halliday.ai.orchestrator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.halliday.ai.llm.config.OllamaLlmProperties;
import com.halliday.ai.llm.core.LanguageModelClient;
import com.halliday.ai.llm.ollama.OllamaChatClient;
import com.halliday.ai.stt.config.SherpaSttProperties;
import com.halliday.ai.stt.core.SpeechToTextClient;
import com.halliday.ai.stt.sherpa.SherpaSpeechToTextClient;
import com.halliday.ai.tts.config.KokoroTtsProperties;
import com.halliday.ai.tts.core.TextToSpeechClient;
import com.halliday.ai.tts.kokoro.KokoroTextToSpeechClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        SherpaSttProperties.class,
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
}
