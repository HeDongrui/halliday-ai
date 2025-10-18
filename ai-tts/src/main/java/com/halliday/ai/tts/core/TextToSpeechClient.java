package com.halliday.ai.tts.core;

import com.halliday.ai.common.audio.AudioFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface TextToSpeechClient {

    Logger log = LoggerFactory.getLogger(TextToSpeechClient.class);

    static void init() {
        log.debug("【语音合成接口】TextToSpeechClient 接口已加载");
    }

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
