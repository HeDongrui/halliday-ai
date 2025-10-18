package com.halliday.ai.common.conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public record ConversationMessage(ConversationRole role, String content) {

    private static final Logger log = LoggerFactory.getLogger(ConversationMessage.class);

    public ConversationMessage {
        log.debug("【会话消息】开始校验消息内容，角色：{}", role);
        role = Objects.requireNonNull(role, "role");
        content = Objects.requireNonNull(content, "content").trim();
        log.debug("【会话消息】创建完成，消息内容长度：{}", content.length());
    }

    public static ConversationMessage user(String content) {
        log.debug("【会话消息】构建用户消息，内容长度：{}", content == null ? 0 : content.length());
        return new ConversationMessage(ConversationRole.USER, content);
    }

    public static ConversationMessage assistant(String content) {
        log.debug("【会话消息】构建助手消息，内容长度：{}", content == null ? 0 : content.length());
        return new ConversationMessage(ConversationRole.ASSISTANT, content);
    }

    public static ConversationMessage system(String content) {
        log.debug("【会话消息】构建系统消息，内容长度：{}", content == null ? 0 : content.length());
        return new ConversationMessage(ConversationRole.SYSTEM, content);
    }
}
