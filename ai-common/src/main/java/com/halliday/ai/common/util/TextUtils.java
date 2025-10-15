package com.halliday.ai.common.util;

import java.util.regex.Pattern;

/**
 * 文本处理工具类，提供句子级别拆分等能力。
 */
public final class TextUtils {

    /**
     * 句子边界符号匹配模式。
     */
    private static final Pattern SENTENCE_PATTERN = Pattern.compile(".*[。！？.!?]$");

    private TextUtils() {
        // 工具类不允许实例化。
    }

    /**
     * 判断给定文本是否以句末符号结尾。
     *
     * @param text 待检测文本
     * @return true 表示文本末尾存在句号或问号等终结符
     */
    public static boolean isSentenceBoundary(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return SENTENCE_PATTERN.matcher(text.trim()).matches();
    }
}
