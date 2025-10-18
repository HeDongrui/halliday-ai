package com.halliday.ai.trace.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.halliday.ai.trace.persistence.entity.AiTraceEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 事件追踪 Mapper。
 */
@Mapper
public interface AiTraceEventMapper extends BaseMapper<AiTraceEventEntity> {

    /**
     * 查询指定 traceId 的所有事件。
     *
     * @param traceId 会话追踪 ID
     * @return 事件列表
     */
    List<AiTraceEventEntity> selectByTraceId(@Param("traceId") String traceId);

    /**
     * 查询指定 traceId 与轮次的事件。
     *
     * @param traceId 会话追踪 ID
     * @param roundIndex 轮次编号
     * @return 事件列表
     */
    List<AiTraceEventEntity> selectByTraceIdAndRound(@Param("traceId") String traceId,
                                                     @Param("roundIndex") Integer roundIndex);
}
