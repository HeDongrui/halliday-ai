package com.halliday.ai.orchestrator.persistence.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.halliday.ai.orchestrator.persistence.entity.AiTraceSttEntity;
import com.halliday.ai.orchestrator.persistence.mapper.AiTraceSttMapper;
import com.halliday.ai.orchestrator.persistence.service.AiTraceSttService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * STT 数据服务实现。
 */
@Service
public class AiTraceSttServiceImpl extends ServiceImpl<AiTraceSttMapper, AiTraceSttEntity>
        implements AiTraceSttService {

    @Override
    public boolean removeByTraceIdAndRound(String traceId, Integer roundIndex) {
        if (!StringUtils.hasText(traceId) || roundIndex == null) {
            return false;
        }
        return getBaseMapper().deleteByTraceIdAndRound(traceId, roundIndex) > 0;
    }

    @Override
    public List<AiTraceSttEntity> listByTraceIdAndRound(String traceId, Integer roundIndex) {
        if (!StringUtils.hasText(traceId) || roundIndex == null) {
            return Collections.emptyList();
        }
        List<AiTraceSttEntity> results = getBaseMapper().selectByTraceIdAndRound(traceId, roundIndex);
        return CollectionUtils.isEmpty(results) ? Collections.emptyList() : results;
    }
}
