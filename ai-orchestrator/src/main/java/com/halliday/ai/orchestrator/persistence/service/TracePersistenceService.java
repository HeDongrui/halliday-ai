package com.halliday.ai.orchestrator.persistence.service;

import com.halliday.ai.orchestrator.persistence.entity.AiTraceErrorEntity;
import com.halliday.ai.orchestrator.persistence.entity.AiTraceEventEntity;
import com.halliday.ai.orchestrator.persistence.entity.AiTraceLlmEntity;
import com.halliday.ai.orchestrator.persistence.entity.AiTraceSessionEntity;
import com.halliday.ai.orchestrator.persistence.entity.AiTraceSttEntity;
import com.halliday.ai.orchestrator.persistence.entity.AiTraceTtsEntity;
import com.halliday.ai.orchestrator.persistence.entity.BaseTraceEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;

/**
 * 统一的链路追踪持久化服务，负责在一个事务内写入会话、事件及流式阶段数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TracePersistenceService {

    private final AiTraceSessionService sessionService;
    private final AiTraceEventService eventService;
    private final AiTraceErrorService errorService;
    private final AiTraceSttService sttService;
    private final AiTraceLlmService llmService;
    private final AiTraceTtsService ttsService;

    /**
     * 保存一次完整轮次的数据，确保在单个事务内落库。
     *
     * @param session 会话信息
     * @param events 事件列表
     * @param sttSegments STT 片段
     * @param llmRecord LLM 响应
     * @param ttsSegments TTS 片段
     * @param errors 错误记录
     */
    @Transactional(rollbackFor = Exception.class)
    public void persistRound(AiTraceSessionEntity session,
                              List<AiTraceEventEntity> events,
                              List<AiTraceSttEntity> sttSegments,
                              AiTraceLlmEntity llmRecord,
                              List<AiTraceTtsEntity> ttsSegments,
                              List<AiTraceErrorEntity> errors) {
        Objects.requireNonNull(session, "session must not be null");
        String traceId = Objects.requireNonNull(session.getTraceId(), "traceId must not be null");
        Integer roundIndex = session.getRoundIndex() == null ? 0 : session.getRoundIndex();
        session.setRoundIndex(roundIndex);
        log.info("[TRACE] 开始持久化会话数据，traceId={}, roundIndex={}", traceId, roundIndex);

        sessionService.saveOrUpdateByTraceId(session);
        log.debug("[TRACE] 会话信息保存完成，traceId={}", traceId);

        if (!CollectionUtils.isEmpty(events)) {
            events.forEach(event -> fillTraceContext(event, traceId, session.getUserId(), roundIndex));
            eventService.saveBatch(events);
            log.debug("[TRACE] 事件数量：{}", events.size());
        }

        if (!CollectionUtils.isEmpty(errors)) {
            errors.forEach(error -> fillTraceContext(error, traceId, session.getUserId(), roundIndex));
            errorService.saveBatch(errors);
            log.debug("[TRACE] 错误数量：{}", errors.size());
        }

        if (!CollectionUtils.isEmpty(sttSegments)) {
            sttService.removeByTraceIdAndRound(traceId, roundIndex);
            sttSegments.forEach(segment -> fillTraceContext(segment, traceId, session.getUserId(), roundIndex));
            sttService.saveBatch(sttSegments);
            log.debug("[TRACE] STT 片段数量：{}", sttSegments.size());
        }

        if (llmRecord != null) {
            fillTraceContext(llmRecord, traceId, session.getUserId(), roundIndex);
            llmService.saveOrUpdateByTraceIdAndRound(llmRecord);
            log.debug("[TRACE] LLM 记录写入完成");
        }

        if (!CollectionUtils.isEmpty(ttsSegments)) {
            ttsService.removeByTraceIdAndRound(traceId, roundIndex);
            ttsSegments.forEach(segment -> fillTraceContext(segment, traceId, session.getUserId(), roundIndex));
            ttsService.saveBatch(ttsSegments);
            log.debug("[TRACE] TTS 片段数量：{}", ttsSegments.size());
        }

        log.info("[TRACE] 链路持久化完成，traceId={}, roundIndex={}", traceId, roundIndex);
    }

    private void fillTraceContext(BaseTraceEntity entity, String traceId, Long userId, Integer roundIndex) {
        entity.setTraceId(traceId)
                .setUserId(userId)
                .setRoundIndex(roundIndex);
    }
}
