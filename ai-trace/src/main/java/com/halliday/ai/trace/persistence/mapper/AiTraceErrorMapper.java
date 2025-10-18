package com.halliday.ai.trace.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.halliday.ai.trace.persistence.entity.AiTraceErrorEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 错误记录 Mapper。
 */
@Mapper
public interface AiTraceErrorMapper extends BaseMapper<AiTraceErrorEntity> {

    /**
     * 根据 traceId 查询错误列表。
     *
     * @param traceId 会话追踪 ID
     * @return 错误列表
     */
    List<AiTraceErrorEntity> selectByTraceId(@Param("traceId") String traceId);

    /**
     * 根据 traceId 与轮次查询错误列表。
     *
     * @param traceId 会话追踪 ID
     * @param roundIndex 轮次编号
     * @return 错误列表
     */
    List<AiTraceErrorEntity> selectByTraceIdAndRound(@Param("traceId") String traceId,
                                                     @Param("roundIndex") Integer roundIndex);
}
