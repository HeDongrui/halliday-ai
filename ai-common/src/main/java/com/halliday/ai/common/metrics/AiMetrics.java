package com.halliday.ai.common.metrics;

/**
 * 统一管理系统内部使用的度量指标名称，确保模块间命名一致。
 */
public final class AiMetrics {

    /**
     * STT 段落识别次数计数器。
     */
    public static final String METRIC_STT_SEGMENTS_TOTAL = "stt_segments_total";

    /**
     * LLM 输出 token 数量计数器。
     */
    public static final String METRIC_LLM_TOKENS_TOTAL = "llm_tokens_total";

    /**
     * TTS 下行音频字节数计数器。
     */
    public static final String METRIC_TTS_BYTES_STREAMED_TOTAL = "tts_bytes_streamed_total";

    private AiMetrics() {
        // 工具类不允许实例化。
    }
}
