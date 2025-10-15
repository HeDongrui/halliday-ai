package com.halliday.ai.tts.core;

import com.halliday.ai.common.audio.AudioFormat;

public interface TextToSpeechClient {

    /**
     * Convert assistant reply text into audio using the configured backend.
     *
     * @param text  reply text
     * @param voice optional voice identifier
     * @return audio bytes encoded according to {@link #outputFormat()}
     */
    byte[] synthesize(String text, String voice);

    /**
     * Audio format metadata of the generated speech.
     */
    AudioFormat outputFormat();
}
