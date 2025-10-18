package com.halliday.ai.trace.model;

import com.halliday.ai.trace.persistence.entity.AiTraceErrorEntity;
import com.halliday.ai.trace.persistence.entity.AiTraceEventEntity;
import com.halliday.ai.trace.persistence.entity.AiTraceLlmEntity;
import com.halliday.ai.trace.persistence.entity.AiTraceSessionEntity;
import com.halliday.ai.trace.persistence.entity.AiTraceSttEntity;
import com.halliday.ai.trace.persistence.entity.AiTraceTtsEntity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 用于封装某次 trace 轮次的全量数据快照，方便上层一次性获取所有记录。
 *
 * @param session     会话主表数据
 * @param events      事件记录
 * @param sttSegments STT 片段
 * @param llmRecord   LLM 调用记录
 * @param ttsSegments TTS 片段
 * @param errors      错误列表
 */
public record TraceRoundDetail(AiTraceSessionEntity session,
                               List<AiTraceEventEntity> events,
                               List<AiTraceSttEntity> sttSegments,
                               Optional<AiTraceLlmEntity> llmRecord,
                               List<AiTraceTtsEntity> ttsSegments,
                               List<AiTraceErrorEntity> errors) {

    public TraceRoundDetail {
        session = Objects.requireNonNull(session, "session must not be null");
        events = events == null ? List.of() : List.copyOf(events);
        sttSegments = sttSegments == null ? List.of() : List.copyOf(sttSegments);
        llmRecord = llmRecord == null ? Optional.empty() : llmRecord;
        ttsSegments = ttsSegments == null ? List.of() : List.copyOf(ttsSegments);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
