package com.halliday.ai.orchestrator.config;

import com.halliday.ai.orchestrator.web.StreamingConversationHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {

    private final StreamingConversationHandler conversationHandler;

    public WebSocketConfiguration(StreamingConversationHandler conversationHandler) {
        this.conversationHandler = conversationHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(conversationHandler, "/ws/conversation").setAllowedOrigins("*");
    }
}
