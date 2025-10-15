package com.halliday.ai.orchestrator.service;

import com.halliday.ai.common.audio.AudioFormat;
import com.halliday.ai.common.conversation.ConversationInput;
import com.halliday.ai.common.conversation.ConversationMessage;
import com.halliday.ai.common.conversation.ConversationRole;
import com.halliday.ai.common.conversation.ConversationResult;
import com.halliday.ai.llm.core.LanguageModelClient;
import com.halliday.ai.stt.core.SpeechToTextClient;
import com.halliday.ai.tts.core.TextToSpeechClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConversationServiceTest {

    @Test
    void composesServicesForTextInput() {
        StubStt stt = new StubStt("transcribed");
        StubLlm llm = new StubLlm("你好，我是助手。");
        StubTts tts = new StubTts();
        ConversationService service = new ConversationService(stt, llm, tts);

        ConversationInput input = ConversationInput.builder()
                .textOverride("你好")
                .history(List.of(new ConversationMessage(ConversationRole.SYSTEM, "系统")))
                .build();

        Optional<ConversationResult> result = service.converse(input);

        assertTrue(result.isPresent());
        assertEquals("你好", result.get().userText());
        assertEquals("你好，我是助手。", result.get().assistantText());
        assertEquals("你好，我是助手。", new String(result.get().assistantAudio().orElseThrow()));
        assertEquals(3, result.get().history().size());
        assertEquals(ConversationRole.USER, result.get().history().get(1).role());
        assertFalse(stt.called);
    }

    @Test
    void transcribesAudioWhenTextMissing() {
        StubStt stt = new StubStt("transcribed");
        StubLlm llm = new StubLlm("回复");
        StubTts tts = new StubTts();
        ConversationService service = new ConversationService(stt, llm, tts);

        ConversationInput input = ConversationInput.builder()
                .audio(new byte[]{1, 2, 3})
                .format(AudioFormat.PCM16_MONO_16K)
                .build();

        Optional<ConversationResult> result = service.converse(input);

        assertTrue(result.isPresent());
        assertEquals("transcribed", result.get().userText());
        assertTrue(stt.called);
    }

    @Test
    void returnsEmptyWhenSttHasNoSpeech() {
        StubStt stt = new StubStt("");
        StubLlm llm = new StubLlm("should not be used");
        StubTts tts = new StubTts();
        ConversationService service = new ConversationService(stt, llm, tts);

        ConversationInput input = ConversationInput.builder()
                .audio(new byte[]{1, 2, 3})
                .format(AudioFormat.PCM16_MONO_16K)
                .build();

        Optional<ConversationResult> result = service.converse(input);

        assertTrue(stt.called);
        assertTrue(result.isEmpty());
    }

    private static final class StubStt implements SpeechToTextClient {
        private final String response;
        private boolean called;

        private StubStt(String response) {
            this.response = response;
        }

        @Override
        public String transcribe(byte[] audio, AudioFormat format) {
            called = true;
            assertNotNull(audio);
            assertNotNull(format);
            return response;
        }
    }

    private static final class StubLlm implements LanguageModelClient {
        private final String reply;
        private List<ConversationMessage> captured = new ArrayList<>();

        private StubLlm(String reply) {
            this.reply = reply;
        }

        @Override
        public String chat(List<ConversationMessage> messages) {
            captured = new ArrayList<>(messages);
            return reply;
        }
    }

    private static final class StubTts implements TextToSpeechClient {
        @Override
        public byte[] synthesize(String text, String voice) {
            return text.getBytes();
        }

        @Override
        public AudioFormat outputFormat() {
            return AudioFormat.PCM16_MONO_16K;
        }
    }
}
