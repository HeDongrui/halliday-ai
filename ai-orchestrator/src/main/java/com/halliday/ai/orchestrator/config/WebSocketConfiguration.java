package com.halliday.ai.orchestrator.config;

import com.halliday.ai.orchestrator.web.StreamingConversationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfiguration.class);

    private final StreamingConversationHandler conversationHandler;

    public WebSocketConfiguration(StreamingConversationHandler conversationHandler) {
        this.conversationHandler = conversationHandler;
        log.debug("【WebSocket 配置】初始化，处理器：{}", conversationHandler.getClass().getSimpleName());
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        log.info("【WebSocket 配置】注册会话 WebSocket 端点：/ws/conversation");
        registry.addHandler(conversationHandler, "/ws/conversation").setAllowedOrigins("*");
    }
}
