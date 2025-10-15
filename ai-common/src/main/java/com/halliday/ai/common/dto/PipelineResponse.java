package com.halliday.ai.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * STT → LLM → TTS 全链路调用结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineResponse {

    /**
     * 语音识别得到的完整文本。
     */
    private String transcription;

    /**
     * 大模型基于识别文本生成的回复内容。
     */
    private String reply;

    /**
     * 音频格式，例如 wav、mp3、pcm。
     */
    private String audioFormat;

    /**
     * 音频内容的 MIME 类型，便于前端选择合适的播放方式。
     */
    private String audioMimeType;

    /**
     * TTS 合成音频的 Base64 编码数据。
     */
    private String audioBase64;
}
