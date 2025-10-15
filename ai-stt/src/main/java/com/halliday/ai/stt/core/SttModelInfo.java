package com.halliday.ai.stt.core;

/**
 * 语音识别模型信息，用于对外暴露服务能力。
 */
public record SttModelInfo(String provider, String model, int sampleRate) {
}
