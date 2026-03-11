package com.jiang.consultant.controller;

import com.jiang.consultant.service.WhisperSttService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SttControllerTest {

    @Test
    void shouldReturnTranscribedTextWhenUploadSuccess() throws Exception {
        WhisperSttService whisperSttService = mock(WhisperSttService.class);
        when(whisperSttService.transcribe(any(), eq("zh"), eq("会议纪要"))).thenReturn("你好，这是转写结果");

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SttController(whisperSttService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "speech.webm",
                "audio/webm",
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/stt/transcribe")
                        .file(file)
                        .param("language", "zh")
                        .param("prompt", "会议纪要"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("你好，这是转写结果"));

        verify(whisperSttService).transcribe(any(), eq("zh"), eq("会议纪要"));
    }

    @Test
    void shouldRejectEmptyAudioFile() throws Exception {
        WhisperSttService whisperSttService = mock(WhisperSttService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SttController(whisperSttService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "speech.webm",
                "audio/webm",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/stt/transcribe").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("音频文件不能为空"));

        verifyNoInteractions(whisperSttService);
    }
}