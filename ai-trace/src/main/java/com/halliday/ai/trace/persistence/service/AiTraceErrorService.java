package com.halliday.ai.trace.persistence.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.halliday.ai.trace.persistence.entity.AiTraceErrorEntity;

import java.util.List;

/**
 * 错误记录服务。
 */
public interface AiTraceErrorService extends IService<AiTraceErrorEntity> {

    /**
     * 查询指定 traceId 的错误记录。
     *
     * @param traceId traceId
     * @return 错误列表
     */
    List<AiTraceErrorEntity> listByTraceId(String traceId);

    /**
     * 查询指定 traceId 与轮次的错误记录。
     *
     * @param traceId traceId
     * @param roundIndex 轮次编号
     * @return 错误列表
     */
    List<AiTraceErrorEntity> listByTraceIdAndRound(String traceId, Integer roundIndex);
}
