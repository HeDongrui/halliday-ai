package com.halliday.ai.orchestrator.persistence.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.halliday.ai.orchestrator.persistence.entity.AiTraceSessionEntity;

import java.util.Optional;

/**
 * 会话主表服务。
 */
public interface AiTraceSessionService extends IService<AiTraceSessionEntity> {

    /**
     * 按 traceId 查询会话。
     *
     * @param traceId traceId
     * @return 会话
     */
    Optional<AiTraceSessionEntity> findByTraceId(String traceId);

    /**
     * 根据 traceId 进行保存或更新。
     *
     * @param entity 会话实体
     * @return 是否成功
     */
    boolean saveOrUpdateByTraceId(AiTraceSessionEntity entity);
}
