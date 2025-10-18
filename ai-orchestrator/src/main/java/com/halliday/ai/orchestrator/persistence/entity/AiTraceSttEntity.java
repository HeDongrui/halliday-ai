package com.halliday.ai.orchestrator.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * STT 阶段数据实体。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("ai_trace_stt")
public class AiTraceSttEntity extends BaseTraceEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 段索引。
     */
    private Integer segmentIndex;

    /**
     * 识别引擎名称。
     */
    private String engineName;

    /**
     * 语种。
     */
    private String language;

    /**
     * 请求参数 JSON 字符串。
     */
    private String requestParams;

    /**
     * 响应 JSON 字符串。
     */
    private String responseJson;

    /**
     * 是否为最终结果。
     */
    @TableField("is_final")
    private Boolean isFinal;

    /**
     * 识别出的文本。
     */
    private String recognizedText;

    /**
     * 置信度。
     */
    private BigDecimal confidence;

    /**
     * 开始时间。
     */
    private LocalDateTime startTime;

    /**
     * 结束时间。
     */
    private LocalDateTime endTime;

    /**
     * 段耗时（毫秒）。
     */
    private Long durationMs;
}
