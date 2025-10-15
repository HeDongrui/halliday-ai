package com.halliday.ai.orchestrator.web;

import com.halliday.ai.common.dto.LlmMessage;
import com.halliday.ai.common.dto.PipelineResponse;
import com.halliday.ai.llm.core.LlmService;
import com.halliday.ai.stt.core.SttService;
import com.halliday.ai.tts.config.KokoroTtsProperties;
import com.halliday.ai.tts.core.TtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 提供 STT → LLM → TTS 全链路的 REST 编排接口。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PipelineController {

    private final SttService sttService;
    private final LlmService llmService;
    private final TtsService ttsService;
    private final KokoroTtsProperties ttsProperties;

    /**
     * 通过 WAV URL 发起全链路调用，返回识别文本、模型回复与合成音频。
     *
     * @param wavUrl WAV 音频文件地址
     * @return 全链路结果
     */
    @GetMapping(value = "/orchestrator/pipeline", produces = MediaType.APPLICATION_JSON_VALUE)
    public PipelineResponse invokePipeline(@RequestParam("wavUrl") String wavUrl) {
        log.info("全链路请求: {}", wavUrl);
        String transcription = safeText(sttService.transcribeFromUrl(wavUrl));
        if (transcription.isEmpty()) {
            log.warn("STT 未返回任何文本，跳过后续链路");
            return PipelineResponse.builder()
                    .transcription("")
                    .reply("")
                    .build();
        }

        String reply = invokeLlm(transcription);
        if (reply.isEmpty()) {
            log.warn("LLM 未生成回复，跳过 TTS");
            return PipelineResponse.builder()
                    .transcription(transcription)
                    .reply("")
                    .build();
        }

        String audioFormat = ttsProperties.getFormat();
        String audioMimeType = resolveMimeType(audioFormat);
        String audioBase64 = synthesizeAudio(reply, audioFormat);
        return PipelineResponse.builder()
                .transcription(transcription)
                .reply(reply)
                .audioFormat(audioBase64 != null ? audioFormat : null)
                .audioMimeType(audioBase64 != null ? audioMimeType : null)
                .audioBase64(audioBase64)
                .build();
    }

    private String invokeLlm(String transcription) {
        List<LlmMessage> history = new ArrayList<>();
        history.add(LlmMessage.builder().role("user").content(transcription).build());
        StringBuilder deltaBuffer = new StringBuilder();
        final String[] finalHolder = new String[1];
        llmService.streamChat(history, deltaBuffer::append, done -> {
            if (done != null && !done.isEmpty()) {
                finalHolder[0] = done;
            } else {
                finalHolder[0] = deltaBuffer.toString();
            }
        });
        return safeText(finalHolder[0] != null ? finalHolder[0] : deltaBuffer.toString());
    }

    private String synthesizeAudio(String text, String format) {
        Path audioFile = null;
        try {
            audioFile = ttsService.synthesizeToFile(text, ttsProperties.getVoice(), format);
            byte[] data = Files.readAllBytes(audioFile);
            return Base64.getEncoder().encodeToString(data);
        } catch (IOException ex) {
            log.error("读取 TTS 音频失败", ex);
            return null;
        } catch (Exception ex) {
            log.error("调用 TTS 失败", ex);
            return null;
        } finally {
            if (audioFile != null) {
                try {
                    Files.deleteIfExists(audioFile);
                } catch (IOException ex) {
                    log.warn("删除临时音频文件失败: {}", audioFile, ex);
                }
            }
        }
    }

    private String resolveMimeType(String format) {
        if (format == null) {
            return "application/octet-stream";
        }
        return switch (format.toLowerCase()) {
            case "wav", "wave" -> "audio/wav";
            case "mp3" -> "audio/mpeg";
            case "ogg" -> "audio/ogg";
            case "pcm" -> "audio/L16";
            default -> "application/octet-stream";
        };
    }

    private String safeText(String text) {
        return text != null ? text.trim() : "";
    }
}
