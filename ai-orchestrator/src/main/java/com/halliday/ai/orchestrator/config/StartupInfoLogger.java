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
        log.debug("【启动日志】收到 ApplicationReadyEvent，准备输出服务信息");
        if (!(applicationContext instanceof WebServerApplicationContext webContext)) {
            log.warn("【启动日志】当前应用上下文不是 WebServerApplicationContext，跳过端口输出");
            return;
        }
        if (webContext.getWebServer() == null) {
            log.warn("【启动日志】WebServer 尚未初始化，无法输出访问地址");
            return;
        }
        int port = webContext.getWebServer().getPort();
        String baseUrl = "http://localhost:" + port;
        log.info("【启动日志】会话接口可用: {}{}", baseUrl, "/api/conversation");
    }
}
