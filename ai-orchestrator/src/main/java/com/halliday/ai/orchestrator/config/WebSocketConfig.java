package com.halliday.ai.orchestrator.config;

import com.halliday.ai.orchestrator.ws.AiStreamHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置，注册流式编排端点。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AiStreamHandler aiStreamHandler;

    public WebSocketConfig(AiStreamHandler aiStreamHandler) {
        this.aiStreamHandler = aiStreamHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(aiStreamHandler, "/ai/stream").setAllowedOriginPatterns("*");
    }
}
