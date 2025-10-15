package com.halliday.ai.orchestrator.web;

import com.halliday.ai.common.audio.AudioFormat;
import com.halliday.ai.common.conversation.ConversationInput;
import com.halliday.ai.common.conversation.ConversationMessage;
import com.halliday.ai.common.conversation.ConversationResult;
import com.halliday.ai.common.conversation.ConversationRole;
import com.halliday.ai.orchestrator.service.ConversationService;
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

    private static final AudioFormat DEFAULT_INPUT_FORMAT = AudioFormat.PCM16_MONO_16K;

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConversationResponse> converse(@RequestBody ConversationRequest request) {
        ConversationInput.Builder builder = ConversationInput.builder();
        if (request.history() != null) {
            builder.history(request.history().stream().map(MessagePayload::toDomain).toList());
        }
        request.audioBytes().ifPresent(bytes -> builder.audio(bytes).format(request.inputFormat()));
        request.textValue().ifPresent(builder::textOverride);

        return conversationService.converse(builder.build())
                .map(this::toResponseEntity)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private ResponseEntity<ConversationResponse> toResponseEntity(ConversationResult result) {
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
        return ResponseEntity.ok(body);
    }

    public record ConversationRequest(String audioBase64,
                                      Integer sampleRate,
                                      Integer channels,
                                      Integer bitDepth,
                                      String text,
                                      List<MessagePayload> history) {

        public Optional<String> textValue() {
            return Optional.ofNullable(text).map(String::trim).filter(s -> !s.isEmpty());
        }

        public Optional<byte[]> audioBytes() {
            if (audioBase64 == null || audioBase64.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(Base64.getDecoder().decode(audioBase64));
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(BAD_REQUEST, "Invalid base64 audio payload", ex);
            }
        }

        public AudioFormat inputFormat() {
            int sr = Optional.ofNullable(sampleRate).orElse(DEFAULT_INPUT_FORMAT.sampleRate());
            int ch = Optional.ofNullable(channels).orElse(DEFAULT_INPUT_FORMAT.channels());
            int bd = Optional.ofNullable(bitDepth).orElse(DEFAULT_INPUT_FORMAT.bitDepth());
            return new AudioFormat(sr, ch, bd, AudioFormat.Endianness.LITTLE);
        }
    }

    public record MessagePayload(String role, String content) {

        public ConversationMessage toDomain() {
            if (role == null || role.isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "Message role must be provided");
            }
            ConversationRole conversationRole;
            try {
                conversationRole = ConversationRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(BAD_REQUEST, "Unsupported role: " + role, ex);
            }
            if (content == null || content.isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "Message content must be provided");
            }
            return new ConversationMessage(conversationRole, content);
        }

        static MessagePayload fromDomain(ConversationMessage message) {
            return new MessagePayload(message.role().name().toLowerCase(Locale.ROOT), message.content());
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
