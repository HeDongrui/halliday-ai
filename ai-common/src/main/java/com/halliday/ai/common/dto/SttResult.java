package com.halliday.ai.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 语音转写结果 DTO，包含文本内容、是否结束标志以及分段索引。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SttResult {

    /**
     * 识别出的文本内容。
     */
    private String text;

    /**
     * 是否为最终段落，true 表示该段落识别完成。
     */
    private boolean finished;

    /**
     * 段落索引，从 0 开始递增，便于客户端按顺序处理。
     */
    private int idx;
}
