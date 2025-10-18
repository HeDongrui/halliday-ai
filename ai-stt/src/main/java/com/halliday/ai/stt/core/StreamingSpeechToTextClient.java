package com.halliday.ai.stt.core;

import com.halliday.ai.common.stt.SttResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.function.Consumer;

public interface StreamingSpeechToTextClient {

    Logger log = LoggerFactory.getLogger(StreamingSpeechToTextClient.class);

    static void init() {
        log.debug("【语音识别接口】StreamingSpeechToTextClient 接口已加载，准备处理流式识别");
    }

    void streamRecognize(InputStream pcmStream, Consumer<SttResult> onResult);
}
