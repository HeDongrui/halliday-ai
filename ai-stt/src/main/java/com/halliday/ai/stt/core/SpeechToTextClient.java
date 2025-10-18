package com.halliday.ai.stt.core;

import com.halliday.ai.common.audio.AudioFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface SpeechToTextClient {

    Logger log = LoggerFactory.getLogger(SpeechToTextClient.class);

    static {
        log.debug("【语音识别接口】SpeechToTextClient 接口已加载，准备处理语音识别请求");
    }

    /**
     * Transcribe raw PCM audio into text.
     *
     * @param audio  PCM audio buffer
     * @param format format metadata describing {@code audio}
     * @return plain-text transcription
     */
    String transcribe(byte[] audio, AudioFormat format);
}
