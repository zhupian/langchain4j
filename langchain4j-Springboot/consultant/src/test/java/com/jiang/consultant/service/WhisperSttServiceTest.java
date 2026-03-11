package com.jiang.consultant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WhisperSttServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnFriendlyMessageWhenTranscriptionsEndpointNotSupported() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/audio/transcriptions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = "404 page not found".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();

        WhisperSttService service = new WhisperSttService(new ObjectMapper());
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        ReflectionTestUtils.setField(service, "apiKey", "");
        ReflectionTestUtils.setField(service, "modelName", "whisper-1");
        ReflectionTestUtils.setField(service, "defaultLanguage", "zh");
        ReflectionTestUtils.setField(service, "defaultPrompt", "");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "speech.wav",
                "audio/wav",
                new byte[]{1, 2, 3, 4}
        );

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.transcribe(file, "zh", null));

        assertEquals("语音转写失败：当前后端模型服务不支持 /audio/transcriptions 接口，请切换到 Whisper/OpenAI 兼容转写服务",
                exception.getMessage());
    }
}