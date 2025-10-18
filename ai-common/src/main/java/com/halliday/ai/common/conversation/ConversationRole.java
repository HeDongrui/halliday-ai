package com.halliday.ai.common.conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public enum ConversationRole {
    SYSTEM,
    USER,
    ASSISTANT;

    private static final Logger log = LoggerFactory.getLogger(ConversationRole.class);

    static void init() {
        log.debug("【会话角色】初始化完成，可用角色：{}", Arrays.toString(values()));
    }
}
