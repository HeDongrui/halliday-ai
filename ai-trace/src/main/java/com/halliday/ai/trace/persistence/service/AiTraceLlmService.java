package com.halliday.ai.trace.persistence.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.halliday.ai.trace.persistence.entity.AiTraceLlmEntity;

import java.util.Optional;

/**
 * LLM 数据服务。
 */
public interface AiTraceLlmService extends IService<AiTraceLlmEntity> {

    /**
     * 查询指定 traceId 和轮次的记录。
     *
     * @param traceId traceId
     * @param roundIndex 轮次
     * @return LLM 记录
     */
    Optional<AiTraceLlmEntity> findByTraceIdAndRound(String traceId, Integer roundIndex);

    /**
     * 按 traceId + roundIndex 保存或更新。
     *
     * @param entity 实体
     * @return 是否成功
     */
    boolean saveOrUpdateByTraceIdAndRound(AiTraceLlmEntity entity);
}
