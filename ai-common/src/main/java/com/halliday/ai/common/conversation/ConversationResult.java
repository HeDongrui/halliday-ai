package com.halliday.ai.common.conversation;

import com.halliday.ai.common.audio.AudioFormat;

import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ConversationResult {

    private final String userText;
    private final String assistantText;
    private final byte[] assistantAudio;
    private final AudioFormat audioFormat;
    private final List<ConversationMessage> history;

    private ConversationResult(Builder builder) {
        this.userText = builder.userText;
        this.assistantText = builder.assistantText;
        this.assistantAudio = builder.assistantAudio;
        this.audioFormat = builder.audioFormat;
        this.history = List.copyOf(builder.history);
    }

    public String userText() {
        return userText;
    }

    public String assistantText() {
        return assistantText;
    }

    public Optional<byte[]> assistantAudio() {
        return Optional.ofNullable(assistantAudio);
    }

    public Optional<AudioFormat> audioFormat() {
        return Optional.ofNullable(audioFormat);
    }

    public List<ConversationMessage> history() {
        return history;
    }

    public String assistantAudioBase64() {
        return assistantAudio == null ? "" : Base64.getEncoder().encodeToString(assistantAudio);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String userText;
        private String assistantText;
        private byte[] assistantAudio;
        private AudioFormat audioFormat;
        private List<ConversationMessage> history = List.of();

        public Builder userText(String userText) {
            this.userText = Objects.requireNonNull(userText, "userText").trim();
            return this;
        }

        public Builder assistantText(String assistantText) {
            this.assistantText = Objects.requireNonNull(assistantText, "assistantText").trim();
            return this;
        }

        public Builder assistantAudio(byte[] assistantAudio) {
            this.assistantAudio = assistantAudio;
            return this;
        }

        public Builder audioFormat(AudioFormat audioFormat) {
            this.audioFormat = audioFormat;
            return this;
        }

        public Builder history(List<ConversationMessage> history) {
            this.history = history == null ? List.of() : List.copyOf(history);
            return this;
        }

        public ConversationResult build() {
            if (userText == null || userText.isBlank()) {
                throw new IllegalStateException("userText must be provided");
            }
            if (assistantText == null || assistantText.isBlank()) {
                throw new IllegalStateException("assistantText must be provided");
            }
            if (history == null) {
                history = List.of();
            }
            return new ConversationResult(this);
        }
    }
}
