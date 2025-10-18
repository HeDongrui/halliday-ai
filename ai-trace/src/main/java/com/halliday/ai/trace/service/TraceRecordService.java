package com.halliday.ai.trace.service;

import com.halliday.ai.trace.model.TraceRoundDetail;
import com.halliday.ai.trace.persistence.entity.AiTraceErrorEntity;
import com.halliday.ai.trace.persistence.entity.AiTraceEventEntity;
import com.halliday.ai.trace.persistence.entity.AiTraceLlmEntity;
import com.halliday.ai.trace.persistence.entity.AiTraceSessionEntity;
import com.halliday.ai.trace.persistence.entity.AiTraceSttEntity;
import com.halliday.ai.trace.persistence.entity.AiTraceTtsEntity;

import java.util.List;
import java.util.Optional;

/**
 * 提供链路追踪数据的写入与查询能力，暴露给编排层等上层模块使用。
 */
public interface TraceRecordService {

    /**
     * 在单个事务内持久化一次对话轮次的数据。
     *
     * @param session     会话信息，包含 traceId、用户、轮次等上下文信息
     * @param events      事件列表，可为空
     * @param sttSegments STT 片段列表，可为空
     * @param llmRecord   LLM 记录，可为空
     * @param ttsSegments TTS 片段列表，可为空
     * @param errors      错误列表，可为空
     */
    void persistRound(AiTraceSessionEntity session,
                      List<AiTraceEventEntity> events,
                      List<AiTraceSttEntity> sttSegments,
                      AiTraceLlmEntity llmRecord,
                      List<AiTraceTtsEntity> ttsSegments,
                      List<AiTraceErrorEntity> errors);

    /**
     * 查询指定 traceId 的最新一轮数据快照。
     *
     * @param traceId 会话追踪 ID
     * @return 轮次数据详情
     */
    Optional<TraceRoundDetail> loadLatestRound(String traceId);

    /**
     * 查询指定 traceId 和轮次的数据快照。
     *
     * @param traceId    会话追踪 ID
     * @param roundIndex 轮次编号，从 0 开始；若为空则查最新轮次
     * @return 轮次详情
     */
    Optional<TraceRoundDetail> loadRound(String traceId, Integer roundIndex);

    /**
     * 查询会话元数据。
     *
     * @param traceId 会话追踪 ID
     * @return 会话信息
     */
    Optional<AiTraceSessionEntity> findSession(String traceId);
}
