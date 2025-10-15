package com.halliday.ai.common.conversation;

import com.halliday.ai.common.audio.AudioFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConversationInputTest {

    @Test
    void requiresAudioOrText() {
        ConversationInput input = ConversationInput.builder()
                .audio(new byte[]{1, 2, 3})
                .format(AudioFormat.PCM16_MONO_16K)
                .build();
        assertTrue(input.audio().isPresent());
        assertTrue(input.format().isPresent());
    }

    @Test
    void supportsTextFallback() {
        ConversationInput input = ConversationInput.builder()
                .textOverride("hello")
                .build();
        assertEquals("hello", input.textOverride().orElseThrow());
        assertTrue(input.audio().isEmpty());
    }

    @Test
    void rejectsMissingPayload() {
        assertThrows(IllegalStateException.class, () -> ConversationInput.builder().build());
    }

    @Test
    void rejectsAudioWithoutFormat() {
        assertThrows(IllegalStateException.class, () -> ConversationInput.builder()
                .audio(new byte[]{1})
                .build());
    }
}
