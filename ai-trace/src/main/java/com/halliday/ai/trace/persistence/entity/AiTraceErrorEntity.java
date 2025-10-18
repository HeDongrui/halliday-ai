package com.halliday.ai.trace.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 错误记录表实体。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("ai_trace_error")
public class AiTraceErrorEntity extends BaseTraceEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 出错阶段。
     */
    private String phase;

    /**
     * 错误代码。
     */
    private String errorCode;

    /**
     * 错误描述。
     */
    private String errorMessage;

    /**
     * 错误堆栈。
     */
    private String stackTrace;

    /**
     * 错误发生时间。
     */
    private LocalDateTime occurTime;
}
