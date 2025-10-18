package com.halliday.ai.orchestrator.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.halliday.ai.orchestrator.persistence.entity.AiTraceEventEntity;
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
}
