package com.halliday.ai.trace.persistence.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.halliday.ai.trace.persistence.entity.AiTraceLlmEntity;
import com.halliday.ai.trace.persistence.mapper.AiTraceLlmMapper;
import com.halliday.ai.trace.persistence.service.AiTraceLlmService;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * LLM 数据服务实现。
 */
@Service
public class AiTraceLlmServiceImpl extends ServiceImpl<AiTraceLlmMapper, AiTraceLlmEntity>
        implements AiTraceLlmService {

    @Override
    public Optional<AiTraceLlmEntity> findByTraceIdAndRound(String traceId, Integer roundIndex) {
        if (!StringUtils.hasText(traceId) || roundIndex == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(getOne(Wrappers.<AiTraceLlmEntity>lambdaQuery()
                .eq(AiTraceLlmEntity::getTraceId, traceId)
                .eq(AiTraceLlmEntity::getRoundIndex, roundIndex)
                .last("LIMIT 1")));
    }

    @Override
    public boolean saveOrUpdateByTraceIdAndRound(AiTraceLlmEntity entity) {
        Assert.notNull(entity, "LLM entity must not be null");
        Assert.isTrue(StringUtils.hasText(entity.getTraceId()), "traceId must not be blank");
        Assert.notNull(entity.getRoundIndex(), "roundIndex must not be null");
        return saveOrUpdate(entity, Wrappers.<AiTraceLlmEntity>lambdaQuery()
                .eq(AiTraceLlmEntity::getTraceId, entity.getTraceId())
                .eq(AiTraceLlmEntity::getRoundIndex, entity.getRoundIndex()));
    }
}
