package com.jiang.consultant.controller;

import com.jiang.consultant.service.EdgeTtsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TtsControllerTest {

    @Test
    void shouldReturnMp3BytesWhenSynthesizeSuccess() throws Exception {
        EdgeTtsService edgeTtsService = mock(EdgeTtsService.class);
        when(edgeTtsService.synthesize("你好，世界", "zh-CN-XiaoxiaoNeural", 5))
                .thenReturn(new byte[]{1, 2, 3});

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TtsController(edgeTtsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/tts/synthesize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"你好，世界","voice":"zh-CN-XiaoxiaoNeural","rate":5}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"speech.mp3\""))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));

        verify(edgeTtsService).synthesize("你好，世界", "zh-CN-XiaoxiaoNeural", 5);
    }

    @Test
    void shouldRejectBlankText() throws Exception {
        EdgeTtsService edgeTtsService = mock(EdgeTtsService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TtsController(edgeTtsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/tts/synthesize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"   ","voice":"zh-CN-XiaoxiaoNeural","rate":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("text 不能为空"));

        verifyNoInteractions(edgeTtsService);
    }
}