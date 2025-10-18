package com.halliday.ai.trace.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 事件追踪表实体。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("ai_trace_event")
public class AiTraceEventEntity extends BaseTraceEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所处阶段。
     */
    private String phase;

    /**
     * 事件名称。
     */
    private String eventName;

    /**
     * 事件说明。
     */
    private String message;

    /**
     * 附加元数据（JSON 字符串）。
     */
    private String metadata;

    /**
     * 事件发生时间。
     */
    private LocalDateTime timestamp;

    /**
     * 事件耗时（毫秒）。
     */
    private Long durationMs;
}
