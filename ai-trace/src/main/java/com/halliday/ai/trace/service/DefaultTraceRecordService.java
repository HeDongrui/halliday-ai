package com.halliday.ai.trace.service;

import com.halliday.ai.trace.model.TraceRoundDetail;
import com.halliday.ai.trace.persistence.entity.AiTraceErrorEntity;
import com.halliday.ai.trace.persistence.entity.AiTraceEventEntity;
import com.halliday.ai.trace.persistence.entity.AiTraceLlmEntity;
import com.halliday.ai.trace.persistence.entity.AiTraceSessionEntity;
import com.halliday.ai.trace.persistence.entity.AiTraceSttEntity;
import com.halliday.ai.trace.persistence.entity.AiTraceTtsEntity;
import com.halliday.ai.trace.persistence.entity.BaseTraceEntity;
import com.halliday.ai.trace.persistence.service.AiTraceErrorService;
import com.halliday.ai.trace.persistence.service.AiTraceEventService;
import com.halliday.ai.trace.persistence.service.AiTraceLlmService;
import com.halliday.ai.trace.persistence.service.AiTraceSessionService;
import com.halliday.ai.trace.persistence.service.AiTraceSttService;
import com.halliday.ai.trace.persistence.service.AiTraceTtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * TraceRecordService 默认实现，封装链路追踪数据的写入和查询逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTraceRecordService implements TraceRecordService {

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
    @Override
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

    @Override
    public Optional<TraceRoundDetail> loadLatestRound(String traceId) {
        log.debug("[TRACE] 准备查询最新轮次数据，traceId={}", traceId);
        return loadRound(traceId, null);
    }

    @Override
    public Optional<TraceRoundDetail> loadRound(String traceId, Integer roundIndex) {
        if (!StringUtils.hasText(traceId)) {
            log.warn("[TRACE] loadRound 传入 traceId 为空");
            return Optional.empty();
        }

        Optional<AiTraceSessionEntity> sessionOptional = sessionService.findByTraceId(traceId);
        if (sessionOptional.isEmpty()) {
            log.info("[TRACE] 未找到会话记录，traceId={}", traceId);
            return Optional.empty();
        }

        AiTraceSessionEntity session = sessionOptional.get();
        int effectiveRound = roundIndex != null ? roundIndex :
                (session.getRoundIndex() == null ? 0 : session.getRoundIndex());
        if (effectiveRound < 0) {
            log.warn("[TRACE] roundIndex 小于 0，自动重置为 0，traceId={}", traceId);
            effectiveRound = 0;
        }

        List<AiTraceEventEntity> events = eventService.listByTraceIdAndRound(traceId, effectiveRound);

        List<AiTraceErrorEntity> errors = errorService.listByTraceIdAndRound(traceId, effectiveRound);

        List<AiTraceSttEntity> sttSegments = sttService.listByTraceIdAndRound(traceId, effectiveRound);
        List<AiTraceTtsEntity> ttsSegments = ttsService.listByTraceIdAndRound(traceId, effectiveRound);
        Optional<AiTraceLlmEntity> llmRecord = llmService.findByTraceIdAndRound(traceId, effectiveRound);

        TraceRoundDetail detail = new TraceRoundDetail(
                session,
                events,
                CollectionUtils.isEmpty(sttSegments) ? Collections.emptyList() : sttSegments,
                llmRecord,
                CollectionUtils.isEmpty(ttsSegments) ? Collections.emptyList() : ttsSegments,
                errors
        );
        log.debug("[TRACE] 查询轮次数据完成，traceId={}, roundIndex={}, events={}, stt={}, tts={}, errors={}",
                traceId,
                effectiveRound,
                detail.events().size(),
                detail.sttSegments().size(),
                detail.ttsSegments().size(),
                detail.errors().size());
        return Optional.of(detail);
    }

    @Override
    public Optional<AiTraceSessionEntity> findSession(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            return Optional.empty();
        }
        return sessionService.findByTraceId(traceId);
    }

    private void fillTraceContext(BaseTraceEntity entity, String traceId, Long userId, Integer roundIndex) {
        entity.setTraceId(traceId)
                .setUserId(userId)
                .setRoundIndex(roundIndex);
    }
}
