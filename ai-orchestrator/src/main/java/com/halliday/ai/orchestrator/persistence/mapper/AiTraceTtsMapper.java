package com.halliday.ai.orchestrator.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.halliday.ai.orchestrator.persistence.entity.AiTraceTtsEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * TTS 数据 Mapper。
 */
@Mapper
public interface AiTraceTtsMapper extends BaseMapper<AiTraceTtsEntity> {

    /**
     * 删除指定会话轮次的 TTS 片段。
     *
     * @param traceId traceId
     * @param roundIndex 轮次
     * @return 影响行数
     */
    int deleteByTraceIdAndRound(@Param("traceId") String traceId,
                                @Param("roundIndex") Integer roundIndex);

    /**
     * 查询指定会话轮次的 TTS 片段。
     *
     * @param traceId traceId
     * @param roundIndex 轮次
     * @return 片段列表
     */
    List<AiTraceTtsEntity> selectByTraceIdAndRound(@Param("traceId") String traceId,
                                                   @Param("roundIndex") Integer roundIndex);
}
