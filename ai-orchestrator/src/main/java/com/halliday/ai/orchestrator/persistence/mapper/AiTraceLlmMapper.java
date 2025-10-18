package com.halliday.ai.orchestrator.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.halliday.ai.orchestrator.persistence.entity.AiTraceLlmEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * LLM 数据 Mapper。
 */
@Mapper
public interface AiTraceLlmMapper extends BaseMapper<AiTraceLlmEntity> {

    /**
     * 根据 traceId 和轮次查询 LLM 数据。
     *
     * @param traceId traceId
     * @param roundIndex 轮次
     * @return LLM 实体
     */
    AiTraceLlmEntity selectByTraceIdAndRound(@Param("traceId") String traceId,
                                             @Param("roundIndex") Integer roundIndex);
}
