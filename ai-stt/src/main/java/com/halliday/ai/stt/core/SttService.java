package com.halliday.ai.stt.core;

import com.halliday.ai.common.dto.SttResult;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * 语音识别服务接口，定义离线与流式两种调用形式。
 */
public interface SttService {

    /**
     * 从远程 WAV 地址下载音频并进行完整识别。
     *
     * @param wavUrl WAV 文件地址
     * @return 识别出的完整文本
     */
    String transcribeFromUrl(String wavUrl);

    /**
     * 对 PCM 流进行增量识别，结果通过回调异步返回。
     *
     * @param pcmStream PCM 输入流
     * @param onResult  每次识别结果的回调
     */
    void streamRecognize(InputStream pcmStream, Consumer<SttResult> onResult);

    /**
     * 查询模型配置信息。
     *
     * @return 模型信息
     */
    SttModelInfo getModelInfo();
}
