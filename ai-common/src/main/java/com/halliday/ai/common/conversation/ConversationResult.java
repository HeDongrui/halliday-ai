package com.halliday.ai.common.conversation;

import com.halliday.ai.common.audio.AudioFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ConversationResult {

    private static final Logger log = LoggerFactory.getLogger(ConversationResult.class);

    private final String userText;
    private final String assistantText;
    private final byte[] assistantAudio;
    private final AudioFormat audioFormat;
    private final List<ConversationMessage> history;

    private ConversationResult(Builder builder) {
        log.debug("【会话结果】开始构建，会话历史数量：{}", builder.history.size());
        this.userText = builder.userText;
        this.assistantText = builder.assistantText;
        this.assistantAudio = builder.assistantAudio;
        this.audioFormat = builder.audioFormat;
        this.history = List.copyOf(builder.history);
        log.debug("【会话结果】构建完成，用户文本长度：{}，助手文本长度：{}",
                userText.length(), assistantText.length());
    }

    public String userText() {
        log.debug("【会话结果】获取用户文本内容，长度：{}", userText.length());
        return userText;
    }

    public String assistantText() {
        log.debug("【会话结果】获取助手文本内容，长度：{}", assistantText.length());
        return assistantText;
    }

    public Optional<byte[]> assistantAudio() {
        log.debug("【会话结果】判断是否包含助手音频：{}", assistantAudio != null);
        return Optional.ofNullable(assistantAudio);
    }

    public Optional<AudioFormat> audioFormat() {
        log.debug("【会话结果】获取音频格式：{}", audioFormat);
        return Optional.ofNullable(audioFormat);
    }

    public List<ConversationMessage> history() {
        log.debug("【会话结果】获取历史消息列表，数量：{}", history.size());
        return history;
    }

    public String assistantAudioBase64() {
        String result = assistantAudio == null ? "" : Base64.getEncoder().encodeToString(assistantAudio);
        log.debug("【会话结果】转换助手音频为 Base64，是否生成成功：{}", !result.isEmpty());
        return result;
    }

    public static Builder builder() {
        log.debug("【会话结果】创建 ConversationResult.Builder 实例");
        return new Builder();
    }

    public static final class Builder {
        private static final Logger log = LoggerFactory.getLogger(Builder.class);

        private String userText;
        private String assistantText;
        private byte[] assistantAudio;
        private AudioFormat audioFormat;
        private List<ConversationMessage> history = List.of();

        public Builder userText(String userText) {
            log.debug("【会话结果构建器】设置用户文本，是否为空：{}", userText == null || userText.trim().isEmpty());
            this.userText = Objects.requireNonNull(userText, "userText").trim();
            return this;
        }

        public Builder assistantText(String assistantText) {
            log.debug("【会话结果构建器】设置助手文本，是否为空：{}", assistantText == null || assistantText.trim().isEmpty());
            this.assistantText = Objects.requireNonNull(assistantText, "assistantText").trim();
            return this;
        }

        public Builder assistantAudio(byte[] assistantAudio) {
            log.debug("【会话结果构建器】设置助手音频数据，是否存在：{}", assistantAudio != null);
            this.assistantAudio = assistantAudio;
            return this;
        }

        public Builder audioFormat(AudioFormat audioFormat) {
            log.debug("【会话结果构建器】设置音频格式：{}", audioFormat);
            this.audioFormat = audioFormat;
            return this;
        }

        public Builder history(List<ConversationMessage> history) {
            log.debug("【会话结果构建器】设置历史消息列表，输入数量：{}", history == null ? 0 : history.size());
            this.history = history == null ? List.of() : List.copyOf(history);
            return this;
        }

        public ConversationResult build() {
            log.debug("【会话结果构建器】开始校验用户文本和助手文本是否有效");
            if (userText == null || userText.isBlank()) {
                log.error("【会话结果构建器】校验失败：缺少用户文本");
                throw new IllegalStateException("userText must be provided");
            }
            if (assistantText == null || assistantText.isBlank()) {
                log.error("【会话结果构建器】校验失败：缺少助手文本");
                throw new IllegalStateException("assistantText must be provided");
            }
            if (history == null) {
                log.warn("【会话结果构建器】历史消息为 null，将使用空列表");
                history = List.of();
            }
            log.debug("【会话结果构建器】校验通过，开始创建 ConversationResult 实例");
            return new ConversationResult(this);
        }
    }
}
