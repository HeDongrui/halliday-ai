package com.halliday.ai.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用入口，负责启动编排服务。
 */
@SpringBootApplication(scanBasePackages = "com.halliday.ai")
public class AiApplication {

    private static final Logger log = LoggerFactory.getLogger(AiApplication.class);

    /**
     * 主函数，启动 Spring Boot 应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        log.info("【编排服务】启动应用，传入参数数量：{}", args == null ? 0 : args.length);
        SpringApplication.run(AiApplication.class, args);
        log.info("【编排服务】应用启动完成");
    }
}
