package com.halliday.ai.orchestrator.web;

import com.halliday.ai.common.audio.AudioFormat;
import com.halliday.ai.common.conversation.ConversationMessage;
import com.halliday.ai.common.conversation.ConversationResult;
import com.halliday.ai.common.conversation.ConversationRole;
import com.halliday.ai.orchestrator.service.ConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConversationController.class)
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConversationService conversationService;

    @Test
    void returnsConversationResponse() throws Exception {
        ConversationResult result = ConversationResult.builder()
                .userText("hello")
                .assistantText("hi there")
                .assistantAudio("hi there".getBytes())
                .audioFormat(AudioFormat.PCM16_MONO_16K)
                .history(List.of(
                        new ConversationMessage(ConversationRole.USER, "hello"),
                        new ConversationMessage(ConversationRole.ASSISTANT, "hi there")
                ))
                .build();

        given(conversationService.converse(any())).willReturn(Optional.of(result));

        mockMvc.perform(post("/api/conversation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistantText").value("hi there"))
                .andExpect(jsonPath("$.history[0].role").value("user"));
    }

    @Test
    void returnsNoContentWhenNoSpeechDetected() throws Exception {
        given(conversationService.converse(any())).willReturn(Optional.empty());

        mockMvc.perform(post("/api/conversation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audioBase64\":\"AQ==\",\"sampleRate\":16000,\"channels\":1,\"bitDepth\":16}"))
                .andExpect(status().isNoContent());
    }
}
