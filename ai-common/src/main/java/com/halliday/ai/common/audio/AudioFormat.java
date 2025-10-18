package com.halliday.ai.common.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Basic PCM audio format descriptor understood by the pipeline.
 */
public record AudioFormat(int sampleRate, int channels, int bitDepth, Endianness endianness) {

    private static final Logger log = LoggerFactory.getLogger(AudioFormat.class);

    public static final AudioFormat PCM16_MONO_16K = new AudioFormat(16_000, 1, 16, Endianness.LITTLE);

    public AudioFormat {
        log.debug("【音频格式】开始校验音频格式参数：采样率={}，声道数={}，位深={}，字节序={}",
                sampleRate, channels, bitDepth, endianness);
        if (sampleRate <= 0) {
            log.error("【音频格式】校验失败：采样率必须为正数，实际值={}", sampleRate);
            throw new IllegalArgumentException("sampleRate must be positive");
        }
        if (channels <= 0) {
            log.error("【音频格式】校验失败：声道数必须为正数，实际值={}", channels);
            throw new IllegalArgumentException("channels must be positive");
        }
        if (bitDepth <= 0 || bitDepth % 8 != 0) {
            log.error("【音频格式】校验失败：位深必须为 8 的正整数倍，实际值={}", bitDepth);
            throw new IllegalArgumentException("bitDepth must be a positive multiple of 8");
        }
        endianness = Objects.requireNonNull(endianness, "endianness");
        log.debug("【音频格式】校验完成，音频格式创建成功");
    }

    public enum Endianness {
        LITTLE,
        BIG;

        private static final Logger log = LoggerFactory.getLogger(Endianness.class);

        static {
            log.debug("【音频格式】初始化字节序枚举：LITTLE, BIG");
        }
    }

    /**
     * Byte size of a single PCM frame (all channels).
     */
    public int frameSizeBytes() {
        int size = (bitDepth / 8) * channels;
        log.debug("【音频格式】计算帧字节数：{}", size);
        return size;
    }
}
