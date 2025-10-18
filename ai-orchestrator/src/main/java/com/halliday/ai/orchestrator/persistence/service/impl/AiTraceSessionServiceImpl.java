package com.halliday.ai.orchestrator.persistence.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.halliday.ai.orchestrator.persistence.entity.AiTraceSessionEntity;
import com.halliday.ai.orchestrator.persistence.mapper.AiTraceSessionMapper;
import com.halliday.ai.orchestrator.persistence.service.AiTraceSessionService;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 会话主表服务实现。
 */
@Service
public class AiTraceSessionServiceImpl extends ServiceImpl<AiTraceSessionMapper, AiTraceSessionEntity>
        implements AiTraceSessionService {

    @Override
    public Optional<AiTraceSessionEntity> findByTraceId(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(getOne(Wrappers.<AiTraceSessionEntity>lambdaQuery()
                .eq(AiTraceSessionEntity::getTraceId, traceId)
                .last("LIMIT 1")));
    }

    @Override
    public boolean saveOrUpdateByTraceId(AiTraceSessionEntity entity) {
        Assert.notNull(entity, "session entity must not be null");
        Assert.isTrue(StringUtils.hasText(entity.getTraceId()), "traceId must not be blank");
        return saveOrUpdate(entity, Wrappers.<AiTraceSessionEntity>lambdaQuery()
                .eq(AiTraceSessionEntity::getTraceId, entity.getTraceId()));
    }
}
