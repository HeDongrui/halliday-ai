package com.halliday.ai.common.conversation;

import com.halliday.ai.common.audio.AudioFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ConversationInput {

    private static final Logger log = LoggerFactory.getLogger(ConversationInput.class);

    private final List<ConversationMessage> history;
    private final byte[] audio;
    private final AudioFormat format;
    private final String textOverride;

    private ConversationInput(Builder builder) {
        log.debug("【会话输入】开始构建 ConversationInput 实例，历史消息数量：{}", builder.history.size());
        this.history = Collections.unmodifiableList(new ArrayList<>(builder.history));
        this.audio = builder.audio;
        this.format = builder.format;
        this.textOverride = builder.textOverride;
        log.debug("【会话输入】完成构建 ConversationInput 实例，是否包含音频：{}，是否包含文本覆盖：{}",
                audio != null, textOverride != null && !textOverride.trim().isEmpty());
    }

    public List<ConversationMessage> history() {
        log.debug("【会话输入】获取历史消息列表，当前数量：{}", history.size());
        return history;
    }

    public Optional<byte[]> audio() {
        log.debug("【会话输入】检查是否存在音频数据：{}", audio != null);
        return Optional.ofNullable(audio);
    }

    public Optional<AudioFormat> format() {
        log.debug("【会话输入】获取音频格式信息：{}", format);
        return Optional.ofNullable(format);
    }

    public Optional<String> textOverride() {
        log.debug("【会话输入】获取文本覆盖内容是否存在：{}", textOverride != null && !textOverride.trim().isEmpty());
        return Optional.ofNullable(textOverride).map(String::trim).filter(s -> !s.isEmpty());
    }

    public String audioAsBase64() {
        String result = audio == null ? "" : Base64.getEncoder().encodeToString(audio);
        log.debug("【会话输入】转换音频为 Base64 字符串，是否生成成功：{}", !result.isEmpty());
        return result;
    }

    public static Builder builder() {
        log.debug("【会话输入】创建 ConversationInput.Builder 实例");
        return new Builder();
    }

    public static final class Builder {
        private static final Logger log = LoggerFactory.getLogger(Builder.class);

        private final List<ConversationMessage> history = new ArrayList<>();
        private byte[] audio;
        private AudioFormat format;
        private String textOverride;

        public Builder addHistory(ConversationMessage message) {
            Objects.requireNonNull(message, "message");
            log.debug("【会话输入构建器】新增历史消息，当前总数：{}", history.size() + 1);
            history.add(message);
            return this;
        }

        public Builder history(List<ConversationMessage> history) {
            log.debug("【会话输入构建器】设置历史消息列表，输入数量：{}", history == null ? 0 : history.size());
            this.history.clear();
            if (history != null) {
                history.stream()
                        .filter(Objects::nonNull)
                        .forEach(item -> {
                            log.trace("【会话输入构建器】添加有效历史消息：{}", item);
                            this.history.add(item);
                        });
            }
            log.debug("【会话输入构建器】历史消息设置完成，当前总数：{}", this.history.size());
            return this;
        }

        public Builder audio(byte[] audio) {
            log.debug("【会话输入构建器】设置音频数据，是否为 null：{}", audio == null);
            this.audio = audio;
            return this;
        }

        public Builder format(AudioFormat format) {
            log.debug("【会话输入构建器】设置音频格式：{}", format);
            this.format = format;
            return this;
        }

        public Builder textOverride(String textOverride) {
            log.debug("【会话输入构建器】设置文本覆盖内容，是否为空：{}", textOverride == null || textOverride.trim().isEmpty());
            this.textOverride = textOverride;
            return this;
        }

        public ConversationInput build() {
            log.debug("【会话输入构建器】开始校验构建条件，是否包含音频：{}，是否包含文本覆盖：{}",
                    audio != null, textOverride != null && !textOverride.trim().isEmpty());
            if (audio == null && (textOverride == null || textOverride.isBlank())) {
                log.error("【会话输入构建器】校验失败：缺少音频和文本覆盖");
                throw new IllegalStateException("audio or textOverride must be provided");
            }
            if (audio != null && format == null) {
                log.error("【会话输入构建器】校验失败：提供音频但未指定格式");
                throw new IllegalStateException("audio format must be provided when audio is present");
            }
            log.debug("【会话输入构建器】校验通过，开始创建 ConversationInput 实例");
            return new ConversationInput(this);
        }
    }
}
