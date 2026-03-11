package com.jiang.consultant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class WhisperSttService {

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper objectMapper;

    @Value("${app.stt.enabled:true}")
    private boolean enabled;

    @Value("${app.stt.base-url:${langchain4j.open-ai.chat-model.base-url}}")
    private String baseUrl;

    @Value("${app.stt.api-key:${API-KEY}}")
    private String apiKey;

    @Value("${app.stt.model-name:whisper-1}")
    private String modelName;

    @Value("${app.stt.default-language:zh}")
    private String defaultLanguage;

    @Value("${app.stt.default-prompt:}")
    private String defaultPrompt;

    public WhisperSttService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String transcribe(MultipartFile file, @Nullable String language, @Nullable String prompt) {
        if (!enabled) {
            throw new RuntimeException("后端 STT 未启用，请先开启 app.stt.enabled");
        }

        String resolvedLanguage = StringUtils.hasText(language) ? language.trim() : defaultLanguage;
        String resolvedPrompt = StringUtils.hasText(prompt) ? prompt.trim() : defaultPrompt;
        String fileName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "speech.webm";

        try {
            RequestBody fileBody = RequestBody.create(file.getBytes(), parseMediaType(file.getContentType()));
            MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", fileName, fileBody)
                    .addFormDataPart("model", modelName)
                    .addFormDataPart("response_format", "json");

            if (StringUtils.hasText(resolvedLanguage)) {
                multipartBuilder.addFormDataPart("language", resolvedLanguage);
            }
            if (StringUtils.hasText(resolvedPrompt)) {
                multipartBuilder.addFormDataPart("prompt", resolvedPrompt);
            }

            Request.Builder requestBuilder = new Request.Builder()
                    .url(buildTranscriptionsUrl())
                    .post(multipartBuilder.build())
                    .header("Accept", "application/json");

            if (StringUtils.hasText(apiKey)) {
                requestBuilder.header("Authorization", "Bearer " + apiKey.trim());
            }

            long start = System.currentTimeMillis();
            try (Response response = client.newCall(requestBuilder.build()).execute()) {
                String responseBody = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    throw new RuntimeException(buildErrorMessage(response.code(), responseBody));
                }
                String text = extractText(responseBody);
                if (!StringUtils.hasText(text)) {
                    throw new RuntimeException("语音转写成功，但未返回识别文本");
                }
                log.info("后端 STT 转写成功, fileName={}, bytes={}, durationMs={}",
                        fileName, file.getSize(), System.currentTimeMillis() - start);
                return text.trim();
            }
        } catch (IOException exception) {
            throw new RuntimeException("调用后端 STT 服务失败，请检查 Whisper/OpenAI 兼容接口是否可用", exception);
        }
    }

    private String buildTranscriptionsUrl() {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBaseUrl + "/audio/transcriptions";
    }

    private MediaType parseMediaType(@Nullable String contentType) {
        MediaType mediaType = MediaType.parse(contentType == null ? "application/octet-stream" : contentType);
        return mediaType == null ? MediaType.parse("application/octet-stream") : mediaType;
    }

    private String extractText(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.hasNonNull("text")) {
                return root.get("text").asText();
            }
            if (root.has("data") && root.get("data").hasNonNull("text")) {
                return root.get("data").get("text").asText();
            }
        } catch (Exception exception) {
            log.warn("解析 STT 响应 JSON 失败，将退回纯文本处理");
        }
        return responseBody.trim();
    }

    private String buildErrorMessage(int statusCode, String responseBody) {
        if (statusCode == 404) {
            return "语音转写失败：当前后端模型服务不支持 /audio/transcriptions 接口，请切换到 Whisper/OpenAI 兼容转写服务";
        }
        String message = extractJsonMessage(responseBody);
        if (StringUtils.hasText(message)) {
            return "语音转写失败：" + message;
        }
        return "语音转写失败：STT 服务返回状态码 " + statusCode;
    }

    private String extractJsonMessage(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.hasNonNull("message")) {
                return root.get("message").asText();
            }
            if (root.has("error")) {
                JsonNode errorNode = root.get("error");
                if (errorNode.isTextual()) {
                    return errorNode.asText();
                }
                if (errorNode.hasNonNull("message")) {
                    return errorNode.get("message").asText();
                }
            }
        } catch (Exception exception) {
            log.debug("STT 错误响应不是标准 JSON: {}", responseBody);
        }
        return responseBody.trim();
    }
}