package com.halliday.ai.stt.core;

import com.halliday.ai.common.audio.AudioFormat;

public interface SpeechToTextClient {

    /**
     * Transcribe raw PCM audio into text.
     *
     * @param audio  PCM audio buffer
     * @param format format metadata describing {@code audio}
     * @return plain-text transcription
     */
    String transcribe(byte[] audio, AudioFormat format);
}
