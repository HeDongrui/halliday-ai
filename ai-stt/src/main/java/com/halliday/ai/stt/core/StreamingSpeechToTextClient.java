package com.halliday.ai.stt.core;

import com.halliday.ai.common.stt.SttResult;

import java.io.InputStream;
import java.util.function.Consumer;

public interface StreamingSpeechToTextClient {

    void streamRecognize(InputStream pcmStream, Consumer<SttResult> onResult);
}
