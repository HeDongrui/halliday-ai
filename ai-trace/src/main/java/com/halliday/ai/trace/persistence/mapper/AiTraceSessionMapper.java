package com.halliday.ai.trace.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.halliday.ai.trace.persistence.entity.AiTraceSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 会话主表 Mapper。
 */
@Mapper
public interface AiTraceSessionMapper extends BaseMapper<AiTraceSessionEntity> {

    /**
     * 根据 traceId 查询会话。
     *
     * @param traceId traceId
     * @return 会话实体
     */
    AiTraceSessionEntity selectByTraceId(@Param("traceId") String traceId);
}
