package com.halliday.ai.orchestrator.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 应用启动后输出关键访问地址，方便本地快速体验。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupInfoLogger implements ApplicationListener<ApplicationReadyEvent> {

    private final ApplicationContext applicationContext;

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
        String pageUrl = baseUrl + "/index.html";
        String apiUrl = baseUrl + "/orchestrator/stt/offline?wavUrl={wavUrl}";
        log.info("离线转写体验页地址: {}", pageUrl);
        log.info("离线转写接口地址: {}", apiUrl);
    }
}
