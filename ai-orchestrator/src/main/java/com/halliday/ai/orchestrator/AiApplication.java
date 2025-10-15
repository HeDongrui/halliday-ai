package com.halliday.ai.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用入口，负责启动编排服务。
 */
@SpringBootApplication(scanBasePackages = "com.halliday.ai")
public class AiApplication {

    /**
     * 主函数，启动 Spring Boot 应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
