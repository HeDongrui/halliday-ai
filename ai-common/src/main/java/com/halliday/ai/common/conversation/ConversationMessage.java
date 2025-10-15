package com.halliday.ai.common.conversation;

import java.util.Objects;

public record ConversationMessage(ConversationRole role, String content) {

    public ConversationMessage {
        role = Objects.requireNonNull(role, "role");
        content = Objects.requireNonNull(content, "content").trim();
    }

    public static ConversationMessage user(String content) {
        return new ConversationMessage(ConversationRole.USER, content);
    }

    public static ConversationMessage assistant(String content) {
        return new ConversationMessage(ConversationRole.ASSISTANT, content);
    }

    public static ConversationMessage system(String content) {
        return new ConversationMessage(ConversationRole.SYSTEM, content);
    }
}
