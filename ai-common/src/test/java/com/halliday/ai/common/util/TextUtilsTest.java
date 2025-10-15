package com.halliday.ai.common.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * TextUtils 工具类单元测试，覆盖句末判断逻辑。
 */
class TextUtilsTest {

    @Test
    void shouldDetectSentenceBoundary() {
        Assertions.assertTrue(TextUtils.isSentenceBoundary("你好。"));
        Assertions.assertTrue(TextUtils.isSentenceBoundary("Hello!"));
        Assertions.assertTrue(TextUtils.isSentenceBoundary("OK?"));
    }

    @Test
    void shouldReturnFalseWhenNoBoundary() {
        Assertions.assertFalse(TextUtils.isSentenceBoundary("未结束"));
        Assertions.assertFalse(TextUtils.isSentenceBoundary(""));
        Assertions.assertFalse(TextUtils.isSentenceBoundary(null));
    }
}
