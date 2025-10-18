package com.halliday.ai.trace.persistence.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.halliday.ai.trace.persistence.entity.AiTraceErrorEntity;
import com.halliday.ai.trace.persistence.mapper.AiTraceErrorMapper;
import com.halliday.ai.trace.persistence.service.AiTraceErrorService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * 错误记录服务实现。
 */
@Service
public class AiTraceErrorServiceImpl extends ServiceImpl<AiTraceErrorMapper, AiTraceErrorEntity>
        implements AiTraceErrorService {

    @Override
    public List<AiTraceErrorEntity> listByTraceId(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            return Collections.emptyList();
        }
        return getBaseMapper().selectByTraceId(traceId);
    }

    @Override
    public List<AiTraceErrorEntity> listByTraceIdAndRound(String traceId, Integer roundIndex) {
        if (!StringUtils.hasText(traceId) || roundIndex == null) {
            return Collections.emptyList();
        }
        List<AiTraceErrorEntity> results = getBaseMapper().selectByTraceIdAndRound(traceId, roundIndex);
        return CollectionUtils.isEmpty(results) ? Collections.emptyList() : results;
    }
}
