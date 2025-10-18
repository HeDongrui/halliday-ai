package com.halliday.ai.orchestrator.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 基础配置。
 */
@Configuration
@MapperScan("com.halliday.ai.orchestrator.persistence.mapper")
public class MyBatisPlusConfiguration {
}
