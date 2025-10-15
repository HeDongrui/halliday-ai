package com.halliday.ai.tts.core;

import java.util.function.Consumer;

public interface StreamingTextToSpeechClient {

    void streamSynthesize(String text, String voice, Consumer<byte[]> onChunk, Runnable onComplete);
}
