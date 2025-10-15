package com.halliday.ai.common.audio;

import java.util.Objects;

/**
 * Basic PCM audio format descriptor understood by the pipeline.
 */
public record AudioFormat(int sampleRate, int channels, int bitDepth, Endianness endianness) {

    public static final AudioFormat PCM16_MONO_16K = new AudioFormat(16_000, 1, 16, Endianness.LITTLE);

    public AudioFormat {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be positive");
        }
        if (channels <= 0) {
            throw new IllegalArgumentException("channels must be positive");
        }
        if (bitDepth <= 0 || bitDepth % 8 != 0) {
            throw new IllegalArgumentException("bitDepth must be a positive multiple of 8");
        }
        endianness = Objects.requireNonNull(endianness, "endianness");
    }

    public enum Endianness {
        LITTLE, BIG
    }

    /**
     * Byte size of a single PCM frame (all channels).
     */
    public int frameSizeBytes() {
        return (bitDepth / 8) * channels;
    }
}
