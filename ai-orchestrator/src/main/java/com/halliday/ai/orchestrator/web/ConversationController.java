package com.halliday.ai.orchestrator.web;

import com.halliday.ai.common.audio.AudioFormat;
import com.halliday.ai.common.conversation.ConversationInput;
import com.halliday.ai.common.conversation.ConversationMessage;
import com.halliday.ai.common.conversation.ConversationResult;
import com.halliday.ai.common.conversation.ConversationRole;
import com.halliday.ai.orchestrator.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping(path = "/api/conversation", produces = MediaType.APPLICATION_JSON_VALUE)
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);
    private static final AudioFormat DEFAULT_INPUT_FORMAT = AudioFormat.PCM16_MONO_16K;

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
        log.debug("【会话接口】ConversationController 已创建");
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConversationResponse> converse(@RequestBody ConversationRequest request) {
        log.info("【会话接口】收到对话请求，历史消息数量：{}", request.history() == null ? 0 : request.history().size());
        ConversationInput.Builder builder = ConversationInput.builder();
        if (request.history() != null) {
            builder.history(request.history().stream().map(MessagePayload::toDomain).toList());
        }
        request.audioBytes().ifPresent(bytes -> {
            log.debug("【会话接口】请求包含音频数据，字节数：{}", bytes.length);
            builder.audio(bytes).format(request.inputFormat());
        });
        request.textValue().ifPresent(text -> {
            log.debug("【会话接口】请求包含文本覆盖，长度：{}", text.length());
            builder.textOverride(text);
        });

        return conversationService.converse(builder.build())
                .map(this::toResponseEntity)
                .orElseGet(() -> {
                    log.info("【会话接口】对话服务未返回结果，响应 204");
                    return ResponseEntity.noContent().build();
                });
    }

    private ResponseEntity<ConversationResponse> toResponseEntity(ConversationResult result) {
        log.debug("【会话接口】开始转换服务结果，历史消息数量：{}", result.history().size());
        AudioFormat outputFormat = result.audioFormat().orElse(DEFAULT_INPUT_FORMAT);
        List<MessagePayload> updatedHistory = result.history().stream()
                .map(MessagePayload::fromDomain)
                .toList();
        ConversationResponse body = new ConversationResponse(
                result.userText(),
                result.assistantText(),
                result.assistantAudioBase64(),
                outputFormat.sampleRate(),
                outputFormat.channels(),
                outputFormat.bitDepth(),
                updatedHistory
        );
        log.debug("【会话接口】转换完成，历史消息数量：{}", updatedHistory.size());
        return ResponseEntity.ok(body);
    }

    public record ConversationRequest(String audioBase64,
                                      Integer sampleRate,
                                      Integer channels,
                                      Integer bitDepth,
                                      String text,
                                      List<MessagePayload> history) {

        public Optional<String> textValue() {
            Optional<String> value = Optional.ofNullable(text).map(String::trim).filter(s -> !s.isEmpty());
            log.debug("【会话接口】解析文本内容，是否存在：{}", value.isPresent());
            return value;
        }

        public Optional<byte[]> audioBytes() {
            if (audioBase64 == null || audioBase64.isBlank()) {
                log.debug("【会话接口】请求未包含音频 Base64");
                return Optional.empty();
            }
            try {
                byte[] bytes = Base64.getDecoder().decode(audioBase64);
                log.debug("【会话接口】解码音频 Base64 成功，字节数：{}", bytes.length);
                return Optional.of(bytes);
            } catch (IllegalArgumentException ex) {
                log.error("【会话接口】音频 Base64 解码失败", ex);
                throw new ResponseStatusException(BAD_REQUEST, "Invalid base64 audio payload", ex);
            }
        }

        public AudioFormat inputFormat() {
            int sr = Optional.ofNullable(sampleRate).orElse(DEFAULT_INPUT_FORMAT.sampleRate());
            int ch = Optional.ofNullable(channels).orElse(DEFAULT_INPUT_FORMAT.channels());
            int bd = Optional.ofNullable(bitDepth).orElse(DEFAULT_INPUT_FORMAT.bitDepth());
            AudioFormat format = new AudioFormat(sr, ch, bd, AudioFormat.Endianness.LITTLE);
            log.debug("【会话接口】解析输入音频格式：采样率={}，声道={}，位深={}", sr, ch, bd);
            return format;
        }
    }

    public record MessagePayload(String role, String content) {

        public ConversationMessage toDomain() {
            if (role == null || role.isBlank()) {
                log.error("【会话接口】历史消息缺少角色字段");
                throw new ResponseStatusException(BAD_REQUEST, "Message role must be provided");
            }
            ConversationRole conversationRole;
            try {
                conversationRole = ConversationRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                log.error("【会话接口】收到不支持的角色：{}", role, ex);
                throw new ResponseStatusException(BAD_REQUEST, "Unsupported role: " + role, ex);
            }
            if (content == null || content.isBlank()) {
                log.error("【会话接口】历史消息缺少内容");
                throw new ResponseStatusException(BAD_REQUEST, "Message content must be provided");
            }
            log.debug("【会话接口】转换历史消息，角色：{}，内容长度：{}", conversationRole, content.length());
            return new ConversationMessage(conversationRole, content);
        }

        static MessagePayload fromDomain(ConversationMessage message) {
            String roleValue = message.role().name().toLowerCase(Locale.ROOT);
            log.trace("【会话接口】序列化历史消息，角色：{}", roleValue);
            return new MessagePayload(roleValue, message.content());
        }
    }

    public record ConversationResponse(String userText,
                                       String assistantText,
                                       String assistantAudioBase64,
                                       int sampleRate,
                                       int channels,
                                       int bitDepth,
                                       List<MessagePayload> history) {
    }
}
