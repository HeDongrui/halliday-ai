package com.halliday.ai.orchestrator.persistence.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.halliday.ai.orchestrator.persistence.entity.AiTraceTtsEntity;

import java.util.List;

/**
 * TTS 数据服务。
 */
public interface AiTraceTtsService extends IService<AiTraceTtsEntity> {

    /**
     * 删除指定 traceId 和轮次的记录。
     *
     * @param traceId traceId
     * @param roundIndex 轮次
     * @return 是否删除成功
     */
    boolean removeByTraceIdAndRound(String traceId, Integer roundIndex);

    /**
     * 查询指定 traceId 和轮次的记录。
     *
     * @param traceId traceId
     * @param roundIndex 轮次
     * @return 记录列表
     */
    List<AiTraceTtsEntity> listByTraceIdAndRound(String traceId, Integer roundIndex);
}
