package com.halliday.ai.trace.persistence.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.halliday.ai.trace.persistence.entity.AiTraceTtsEntity;
import com.halliday.ai.trace.persistence.mapper.AiTraceTtsMapper;
import com.halliday.ai.trace.persistence.service.AiTraceTtsService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * TTS 数据服务实现。
 */
@Service
public class AiTraceTtsServiceImpl extends ServiceImpl<AiTraceTtsMapper, AiTraceTtsEntity>
        implements AiTraceTtsService {

    @Override
    public boolean removeByTraceIdAndRound(String traceId, Integer roundIndex) {
        if (!StringUtils.hasText(traceId) || roundIndex == null) {
            return false;
        }
        return getBaseMapper().deleteByTraceIdAndRound(traceId, roundIndex) > 0;
    }

    @Override
    public List<AiTraceTtsEntity> listByTraceIdAndRound(String traceId, Integer roundIndex) {
        if (!StringUtils.hasText(traceId) || roundIndex == null) {
            return Collections.emptyList();
        }
        List<AiTraceTtsEntity> results = getBaseMapper().selectByTraceIdAndRound(traceId, roundIndex);
        return CollectionUtils.isEmpty(results) ? Collections.emptyList() : results;
    }
}
