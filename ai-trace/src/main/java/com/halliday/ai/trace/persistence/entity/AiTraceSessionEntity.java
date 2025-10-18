package com.halliday.ai.trace.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 会话主表实体。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("ai_trace_session")
public class AiTraceSessionEntity extends BaseTraceEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会话开始时间。
     */
    private LocalDateTime startTime;

    /**
     * 会话结束时间。
     */
    private LocalDateTime endTime;

    /**
     * 会话耗时（毫秒）。
     */
    private Long durationMs;

    /**
     * 使用的大模型名称。
     */
    private String llmModel;

    /**
     * 大模型输入 token 数量。
     */
    private Integer llmInputTokens;

    /**
     * 大模型输出 token 数量。
     */
    private Integer llmOutputTokens;

    /**
     * 大模型总 token 数量。
     */
    private Integer llmTotalTokens;

    /**
     * 会话状态。
     */
    private String status;

    /**
     * 错误摘要信息。
     */
    private String errorMessage;
}
