package com.halliday.ai.orchestrator.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * TTS 阶段数据实体。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("ai_trace_tts")
public class AiTraceTtsEntity extends BaseTraceEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer segmentIndex;

    private String inputText;

    private String voiceName;

    private String engineName;

    private Integer sampleRate;

    private String format;

    private String outputAudioUrl;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long durationMs;

    private String requestParams;

    private String responseJson;
}
