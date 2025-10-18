package com.halliday.ai.tts.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public interface StreamingTextToSpeechClient {

    Logger log = LoggerFactory.getLogger(StreamingTextToSpeechClient.class);

    static void init() {
        log.debug("【语音合成接口】StreamingTextToSpeechClient 接口已加载");
    }

    void streamSynthesize(String text, String voice, Consumer<byte[]> onChunk, Runnable onComplete);
}
