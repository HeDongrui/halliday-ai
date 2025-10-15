package com.halliday.ai.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 大语言模型聊天消息，描述角色与消息内容。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmMessage {

    /**
     * 角色，例如 system、user、assistant。
     */
    private String role;

    /**
     * 消息内容，使用 UTF-8 文本。
     */
    private String content;
}
