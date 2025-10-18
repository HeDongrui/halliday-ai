package com.halliday.ai.trace.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 基础 MyBatis-Plus 配置，负责扫描 Trace 模块的 Mapper 并启用事务管理。
 */
@Configuration
@EnableTransactionManagement
@MapperScan("com.halliday.ai.trace.persistence.mapper")
public class TracePersistenceConfiguration {
}
