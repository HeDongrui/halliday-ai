package com.halliday.ai.orchestrator.persistence.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.halliday.ai.orchestrator.persistence.entity.AiTraceErrorEntity;

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
}
