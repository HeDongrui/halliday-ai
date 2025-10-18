package com.halliday.ai.trace.persistence.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.halliday.ai.trace.persistence.entity.AiTraceEventEntity;
import com.halliday.ai.trace.persistence.mapper.AiTraceEventMapper;
import com.halliday.ai.trace.persistence.service.AiTraceEventService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * 事件追踪服务实现。
 */
@Service
public class AiTraceEventServiceImpl extends ServiceImpl<AiTraceEventMapper, AiTraceEventEntity>
        implements AiTraceEventService {

    @Override
    public List<AiTraceEventEntity> listByTraceId(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            return Collections.emptyList();
        }
        return getBaseMapper().selectByTraceId(traceId);
    }

    @Override
    public List<AiTraceEventEntity> listByTraceIdAndRound(String traceId, Integer roundIndex) {
        if (!StringUtils.hasText(traceId) || roundIndex == null) {
            return Collections.emptyList();
        }
        List<AiTraceEventEntity> results = getBaseMapper().selectByTraceIdAndRound(traceId, roundIndex);
        return CollectionUtils.isEmpty(results) ? Collections.emptyList() : results;
    }
}
