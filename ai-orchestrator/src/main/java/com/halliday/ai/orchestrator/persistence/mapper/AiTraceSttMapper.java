package com.halliday.ai.orchestrator.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.halliday.ai.orchestrator.persistence.entity.AiTraceSttEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * STT 数据 Mapper。
 */
@Mapper
public interface AiTraceSttMapper extends BaseMapper<AiTraceSttEntity> {

    /**
     * 删除指定会话轮次的 STT 片段。
     *
     * @param traceId traceId
     * @param roundIndex 轮次
     * @return 受影响行数
     */
    int deleteByTraceIdAndRound(@Param("traceId") String traceId,
                                @Param("roundIndex") Integer roundIndex);

    /**
     * 查询指定会话轮次的 STT 片段。
     *
     * @param traceId traceId
     * @param roundIndex 轮次
     * @return 列表
     */
    List<AiTraceSttEntity> selectByTraceIdAndRound(@Param("traceId") String traceId,
                                                   @Param("roundIndex") Integer roundIndex);
}
