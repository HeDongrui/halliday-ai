package com.halliday.ai.common.stt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public final class SttResult {

    private static final Logger log = LoggerFactory.getLogger(SttResult.class);

    private final String text;
    private final boolean finished;
    private final int idx;

    private SttResult(Builder builder) {
        log.debug("【语音识别结果】开始构建，文本内容长度：{}，是否结束：{}，索引：{}",
                builder.text.length(), builder.finished, builder.idx);
        this.text = builder.text;
        this.finished = builder.finished;
        this.idx = builder.idx;
        log.debug("【语音识别结果】构建完成");
    }

    public String getText() {
        log.debug("【语音识别结果】获取识别文本，长度：{}", text.length());
        return text;
    }

    public boolean isFinished() {
        log.debug("【语音识别结果】判断是否结束：{}", finished);
        return finished;
    }

    public int getIdx() {
        log.debug("【语音识别结果】获取识别片段索引：{}", idx);
        return idx;
    }

    public static Builder builder() {
        log.debug("【语音识别结果】创建 SttResult.Builder 实例");
        return new Builder();
    }

    public static final class Builder {
        private static final Logger log = LoggerFactory.getLogger(Builder.class);

        private String text = "";
        private boolean finished;
        private int idx;

        public Builder text(String text) {
            log.debug("【语音识别结果构建器】设置文本内容，是否为空：{}", text == null || text.isEmpty());
            this.text = Objects.requireNonNullElse(text, "");
            return this;
        }

        public Builder finished(boolean finished) {
            log.debug("【语音识别结果构建器】设置结束标记：{}", finished);
            this.finished = finished;
            return this;
        }

        public Builder idx(int idx) {
            log.debug("【语音识别结果构建器】设置识别片段索引：{}", idx);
            this.idx = idx;
            return this;
        }

        public SttResult build() {
            log.debug("【语音识别结果构建器】开始创建 SttResult 实例");
            return new SttResult(this);
        }
    }
}
