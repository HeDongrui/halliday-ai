package com.halliday.ai.orchestrator.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * LLM 阶段数据实体。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("ai_trace_llm")
public class AiTraceLlmEntity extends BaseTraceEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private String modelName;

    private String promptText;

    private String responseText;

    private String toolCalls;

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer totalTokens;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long latencyMs;

    private String finishReason;

    private String requestParams;

    private String responseJson;
}
