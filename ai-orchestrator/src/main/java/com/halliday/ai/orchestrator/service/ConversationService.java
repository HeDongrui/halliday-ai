package com.halliday.ai.orchestrator.service;

import com.halliday.ai.common.conversation.ConversationInput;
import com.halliday.ai.common.conversation.ConversationMessage;
import com.halliday.ai.common.conversation.ConversationResult;
import com.halliday.ai.common.conversation.ConversationRole;
import com.halliday.ai.common.exception.AiServiceException;
import com.halliday.ai.llm.core.LanguageModelClient;
import com.halliday.ai.stt.core.SpeechToTextClient;
import com.halliday.ai.tts.core.TextToSpeechClient;
import com.halliday.ai.trace.model.TraceRoundDetail;
import com.halliday.ai.trace.service.TraceRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final SpeechToTextClient speechToTextClient;
    private final LanguageModelClient languageModelClient;
    private final TextToSpeechClient textToSpeechClient;
    private final TraceRecordService traceRecordService;

    public ConversationService(SpeechToTextClient speechToTextClient,
                               LanguageModelClient languageModelClient,
                               TextToSpeechClient textToSpeechClient,
                               TraceRecordService traceRecordService) {
        this.speechToTextClient = speechToTextClient;
        this.languageModelClient = languageModelClient;
        this.textToSpeechClient = textToSpeechClient;
        this.traceRecordService = traceRecordService;
        log.debug("【会话服务】ConversationService 初始化完成");
    }

    public Optional<ConversationResult> converse(ConversationInput input) {
        log.info("【会话服务】开始处理一次对话请求，历史消息数量：{}", input.history().size());
        Optional<String> maybeUserText = resolveUserText(input);
        if (maybeUserText.isEmpty()) {
            log.warn("【会话服务】无法识别用户输入内容，返回空结果");
            return Optional.empty();
        }
        String userText = maybeUserText.get();
        List<ConversationMessage> history = new ArrayList<>(input.history());
        history.add(new ConversationMessage(ConversationRole.USER, userText));
        log.debug("【会话服务】已追加用户消息，长度：{}", userText.length());

        String assistantReply = languageModelClient.chat(history);
        history.add(new ConversationMessage(ConversationRole.ASSISTANT, assistantReply));
        log.debug("【会话服务】模型回复生成完成，长度：{}", assistantReply.length());

        byte[] audio = textToSpeechClient.synthesize(assistantReply, null);
        log.debug("【会话服务】语音合成完成，音频字节数：{}", audio == null ? 0 : audio.length);

        ConversationResult result = ConversationResult.builder()
                .userText(userText)
                .assistantText(assistantReply)
                .assistantAudio(audio)
                .audioFormat(textToSpeechClient.outputFormat())
                .history(history)
                .build();
        log.info("【会话服务】对话处理完成");
        return Optional.of(result);
    }

    /**
     * 查询指定 traceId 的最新轮次追踪数据，用于调试或外部检索。
     *
     * @param traceId 链路追踪 ID
     * @return 最近一次轮次详情
     */
    public Optional<TraceRoundDetail> loadLatestTrace(String traceId) {
        log.debug("【会话服务】准备查询最新追踪数据，traceId={}", traceId);
        return traceRecordService.loadLatestRound(traceId);
    }

    private Optional<String> resolveUserText(ConversationInput input) {
        log.debug("【会话服务】开始解析用户输入内容");
        Optional<String> provided = input.textOverride()
                .map(String::trim)
                .filter(text -> !text.isEmpty());
        if (provided.isPresent()) {
            log.debug("【会话服务】直接使用文本覆盖内容，长度：{}", provided.get().length());
            return provided;
        }
        if (input.audio().isPresent()) {
            byte[] audio = input.audio().orElseThrow();
            log.debug("【会话服务】检测到音频输入，字节数：{}", audio.length);
            String transcription = speechToTextClient.transcribe(
                    audio,
                    input.format().orElseThrow(() ->
                            new AiServiceException("语音识别缺少音频格式信息")));
            String normalized = transcription == null ? "" : transcription.trim();
            log.debug("【会话服务】音频转写结果长度：{}", normalized.length());
            return normalized.isEmpty() ? Optional.empty() : Optional.of(normalized);
        }
        throw new AiServiceException("未提供可用的音频或文本输入");
    }
}
