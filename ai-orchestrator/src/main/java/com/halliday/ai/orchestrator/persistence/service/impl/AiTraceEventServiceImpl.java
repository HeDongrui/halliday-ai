package com.halliday.ai.orchestrator.persistence.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.halliday.ai.orchestrator.persistence.entity.AiTraceEventEntity;
import com.halliday.ai.orchestrator.persistence.mapper.AiTraceEventMapper;
import com.halliday.ai.orchestrator.persistence.service.AiTraceEventService;
import org.springframework.stereotype.Service;
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
}
