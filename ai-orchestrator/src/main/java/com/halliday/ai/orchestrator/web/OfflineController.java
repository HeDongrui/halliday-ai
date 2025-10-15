package com.halliday.ai.orchestrator.web;

import com.halliday.ai.stt.core.SttService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 离线语音转写 REST 接口，代理 STT 服务对外提供能力。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class OfflineController {

    private final SttService sttService;

    /**
     * 通过 WAV URL 调用 STT 服务获取完整转写文本。
     *
     * @param wavUrl WAV 文件地址
     * @return 识别文本
     */
    @GetMapping(value = "/orchestrator/stt/offline", produces = MediaType.TEXT_PLAIN_VALUE)
    public String offlineTranscribe(@RequestParam("wavUrl") String wavUrl) {
        log.info("离线识别请求: {}", wavUrl);
        return sttService.transcribeFromUrl(wavUrl);
    }
}
