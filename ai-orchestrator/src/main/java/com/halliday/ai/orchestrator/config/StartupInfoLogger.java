package com.halliday.ai.orchestrator.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class StartupInfoLogger implements ApplicationListener<ApplicationReadyEvent> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StartupInfoLogger.class);

    private final ApplicationContext applicationContext;

    public StartupInfoLogger(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!(applicationContext instanceof WebServerApplicationContext webContext)) {
            return;
        }
        if (webContext.getWebServer() == null) {
            return;
        }
        int port = webContext.getWebServer().getPort();
        String baseUrl = "http://localhost:" + port;
        log.info("会话接口可用: {}{}", baseUrl, "/api/conversation");
    }
}
