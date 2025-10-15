package com.halliday.ai.common.conversation;

import com.halliday.ai.common.audio.AudioFormat;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ConversationInput {

    private final List<ConversationMessage> history;
    private final byte[] audio;
    private final AudioFormat format;
    private final String textOverride;

    private ConversationInput(Builder builder) {
        this.history = Collections.unmodifiableList(new ArrayList<>(builder.history));
        this.audio = builder.audio;
        this.format = builder.format;
        this.textOverride = builder.textOverride;
    }

    public List<ConversationMessage> history() {
        return history;
    }

    public Optional<byte[]> audio() {
        return Optional.ofNullable(audio);
    }

    public Optional<AudioFormat> format() {
        return Optional.ofNullable(format);
    }

    public Optional<String> textOverride() {
        return Optional.ofNullable(textOverride).map(String::trim).filter(s -> !s.isEmpty());
    }

    public String audioAsBase64() {
        return audio == null ? "" : Base64.getEncoder().encodeToString(audio);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<ConversationMessage> history = new ArrayList<>();
        private byte[] audio;
        private AudioFormat format;
        private String textOverride;

        public Builder addHistory(ConversationMessage message) {
            history.add(Objects.requireNonNull(message, "message"));
            return this;
        }

        public Builder history(List<ConversationMessage> history) {
            this.history.clear();
            if (history != null) {
                history.stream()
                        .filter(Objects::nonNull)
                        .forEach(this.history::add);
            }
            return this;
        }

        public Builder audio(byte[] audio) {
            this.audio = audio;
            return this;
        }

        public Builder format(AudioFormat format) {
            this.format = format;
            return this;
        }

        public Builder textOverride(String textOverride) {
            this.textOverride = textOverride;
            return this;
        }

        public ConversationInput build() {
            if (audio == null && (textOverride == null || textOverride.isBlank())) {
                throw new IllegalStateException("audio or textOverride must be provided");
            }
            if (audio != null && format == null) {
                throw new IllegalStateException("audio format must be provided when audio is present");
            }
            return new ConversationInput(this);
        }
    }
}
