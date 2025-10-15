package com.halliday.ai.tts.core;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * 文本转语音服务接口，定义离线文件与流式两种模式。
 */
public interface TtsService {

    /**
     * 将文本合成到本地文件。
     *
     * @param text   待合成文本
     * @param voice  语音名称
     * @param format 输出格式
     * @return 合成后文件路径
     */
    Path synthesizeToFile(String text, String voice, String format);

    /**
     * 以流式方式合成语音，实时输出音频块。
     *
     * @param text    待合成文本
     * @param voice   语音名称
     * @param format  输出格式
     * @param onChunk 音频块回调
     * @param onEnd   结束回调
     */
    void streamSynthesize(String text, String voice, String format, Consumer<byte[]> onChunk, Runnable onEnd);
}
