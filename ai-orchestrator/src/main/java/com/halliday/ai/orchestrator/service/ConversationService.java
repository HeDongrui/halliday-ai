package com.halliday.ai.orchestrator.service;

import com.halliday.ai.common.conversation.ConversationInput;
import com.halliday.ai.common.conversation.ConversationMessage;
import com.halliday.ai.common.conversation.ConversationResult;
import com.halliday.ai.common.conversation.ConversationRole;
import com.halliday.ai.common.exception.AiServiceException;
import com.halliday.ai.llm.core.LanguageModelClient;
import com.halliday.ai.stt.core.SpeechToTextClient;
import com.halliday.ai.tts.core.TextToSpeechClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ConversationService {

    private final SpeechToTextClient speechToTextClient;
    private final LanguageModelClient languageModelClient;
    private final TextToSpeechClient textToSpeechClient;

    public ConversationService(SpeechToTextClient speechToTextClient,
                               LanguageModelClient languageModelClient,
                               TextToSpeechClient textToSpeechClient) {
        this.speechToTextClient = speechToTextClient;
        this.languageModelClient = languageModelClient;
        this.textToSpeechClient = textToSpeechClient;
    }

    public Optional<ConversationResult> converse(ConversationInput input) {
        Optional<String> maybeUserText = resolveUserText(input);
        if (maybeUserText.isEmpty()) {
            return Optional.empty();
        }
        String userText = maybeUserText.get();
        List<ConversationMessage> history = new ArrayList<>(input.history());
        history.add(new ConversationMessage(ConversationRole.USER, userText));

        String assistantReply = languageModelClient.chat(history);
        history.add(new ConversationMessage(ConversationRole.ASSISTANT, assistantReply));

        byte[] audio = textToSpeechClient.synthesize(assistantReply, null);

        ConversationResult result = ConversationResult.builder()
                .userText(userText)
                .assistantText(assistantReply)
                .assistantAudio(audio)
                .audioFormat(textToSpeechClient.outputFormat())
                .history(history)
                .build();
        return Optional.of(result);
    }

    private Optional<String> resolveUserText(ConversationInput input) {
        Optional<String> provided = input.textOverride()
                .map(String::trim)
                .filter(text -> !text.isEmpty());
        if (provided.isPresent()) {
            return provided;
        }
        if (input.audio().isPresent()) {
            byte[] audio = input.audio().orElseThrow();
            String transcription = speechToTextClient.transcribe(
                    audio,
                    input.format().orElseThrow(() ->
                            new AiServiceException("Audio format must be supplied for transcription")));
            String normalized = transcription == null ? "" : transcription.trim();
            return normalized.isEmpty() ? Optional.empty() : Optional.of(normalized);
        }
        throw new AiServiceException("No audio or text provided");
    }
}
