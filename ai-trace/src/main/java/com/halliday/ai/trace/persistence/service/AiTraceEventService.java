package com.halliday.ai.trace.persistence.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.halliday.ai.trace.persistence.entity.AiTraceEventEntity;

import java.util.List;

/**
 * 事件追踪服务。
 */
public interface AiTraceEventService extends IService<AiTraceEventEntity> {

    /**
     * 查询指定 traceId 的事件。
     *
     * @param traceId traceId
     * @return 事件列表
     */
    List<AiTraceEventEntity> listByTraceId(String traceId);

    /**
     * 查询指定 traceId 与轮次的事件。
     *
     * @param traceId traceId
     * @param roundIndex 轮次编号
     * @return 事件列表
     */
    List<AiTraceEventEntity> listByTraceIdAndRound(String traceId, Integer roundIndex);
}
