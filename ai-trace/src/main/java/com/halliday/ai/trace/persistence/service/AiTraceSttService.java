package com.halliday.ai.trace.persistence.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.halliday.ai.trace.persistence.entity.AiTraceSttEntity;

import java.util.List;

/**
 * STT 数据服务。
 */
public interface AiTraceSttService extends IService<AiTraceSttEntity> {

    /**
     * 删除指定 traceId 和轮次的 STT 片段。
     *
     * @param traceId traceId
     * @param roundIndex 轮次
     * @return 是否删除成功
     */
    boolean removeByTraceIdAndRound(String traceId, Integer roundIndex);

    /**
     * 查询指定 traceId 和轮次的 STT 片段。
     *
     * @param traceId traceId
     * @param roundIndex 轮次
     * @return 片段列表
     */
    List<AiTraceSttEntity> listByTraceIdAndRound(String traceId, Integer roundIndex);
}
