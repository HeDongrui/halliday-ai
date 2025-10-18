package com.halliday.ai.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AiServiceException extends RuntimeException {

    private static final Logger log = LoggerFactory.getLogger(AiServiceException.class);

    public AiServiceException(String message) {
        super(message);
        log.error("【AI 服务异常】创建异常实例，错误信息：{}", message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
        log.error("【AI 服务异常】创建异常实例，错误信息：{}，根因：{}", message, cause == null ? "无" : cause.getMessage(), cause);
    }
}
