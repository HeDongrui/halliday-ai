package com.halliday.ai.orchestrator.persistence.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.halliday.ai.orchestrator.persistence.entity.AiTraceErrorEntity;
import com.halliday.ai.orchestrator.persistence.mapper.AiTraceErrorMapper;
import com.halliday.ai.orchestrator.persistence.service.AiTraceErrorService;
import org.springframework.stereotype.Service;
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
}
