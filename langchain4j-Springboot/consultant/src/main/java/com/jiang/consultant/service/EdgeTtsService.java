package com.jiang.consultant.service;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.ByteString;
import org.springframework.stereotype.Service;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class EdgeTtsService {

    private static final String DEFAULT_VOICE = "zh-CN-XiaoxiaoNeural";
    private static final int MAX_TEXT_LENGTH = 3_000;

    // 微软 Edge 朗读的 WebSocket 地址（免费、无需密钥）
    private static final String WS_URL =
        "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
        + "?TrustedClientToken=6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        + "&ConnectionId=";

    private static final String ORIGIN =
        "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold";

    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    /**
     * 文字转语音
     * @param text  要合成的文本
     * @param voice 音色名称，如 zh-CN-XiaoxiaoNeural
     * @param rate  语速百分比，如 0 表示正常，+50 加速，-50 减速
     * @return MP3 音频字节数组
     */
    public byte[] synthesize(String text, String voice, int rate) {
        String normalizedText = normalizeText(text);
        String normalizedVoice = normalizeVoice(voice);
        int normalizedRate = normalizeRate(rate);

        String connId    = uuid();
        String requestId = uuid();

        Request request = new Request.Builder()
                .url(WS_URL + connId)
                .header("Origin", ORIGIN)
                .header("User-Agent", USER_AGENT)
                .build();

        CompletableFuture<byte[]> future = new CompletableFuture<>();
        ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();

        String finalVoice = normalizedVoice;
        int finalRate = normalizedRate;

        client.newWebSocket(request, new WebSocketListener() {

            @Override
            public void onOpen(@NonNull WebSocket ws, @NonNull Response response) {

                // ① 发送音频配置
                String config =
                    "Content-Type:application/json; charset=utf-8\r\n"
                    + "Path:speech.config\r\n\r\n"
                    + "{\"context\":{\"synthesis\":{\"audio\":{"
                    + "\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\","
                    + "\"wordBoundaryEnabled\":\"false\"},"
                    + "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}";
                ws.send(config);

                // ② 发送 SSML 文本
                String rateStr = (finalRate >= 0 ? "+" : "") + finalRate + "%";
                String ssml =
                    "X-RequestId:" + requestId + "\r\n"
                    + "Content-Type:application/ssml+xml\r\n"
                    + "Path:ssml\r\n\r\n"
                    + "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' "
                    + "xml:lang='zh-CN'>"
                    + "<voice name='" + finalVoice + "'>"
                    + "<prosody pitch='+0Hz' rate='" + rateStr + "' volume='+0%'>"
                    + escapeXml(normalizedText)
                    + "</prosody></voice></speak>";
                ws.send(ssml);

                log.info("Edge-TTS 请求已发送, voice={}, textLength={}", finalVoice, normalizedText.length());
            }

            @Override
            public void onMessage(@NonNull WebSocket ws, @NonNull ByteString bytes) {
                // ③ 接收二进制音频数据
                try {
                    byte[] data = bytes.toByteArray();
                    if (data.length < 2) return;

                    // 前 2 字节 = 头部长度（大端序）
                    int headerLen = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);

                    if (data.length > 2 + headerLen) {
                        String header = new String(data, 2, headerLen, StandardCharsets.UTF_8);
                        if (header.contains("Path:audio")) {
                            // 头部之后就是真正的音频数据
                            audioBuffer.write(data, 2 + headerLen,
                                    data.length - 2 - headerLen);
                        }
                    }
                } catch (Exception e) {
                    log.error("解析音频数据出错", e);
                }
            }

            @Override
            public void onMessage(@NonNull WebSocket ws, @NonNull String text) {
                // ④ 收到 turn.end 表示合成完毕
                if (text.contains("Path:turn.end")) {
                    ws.close(1000, "done");
                    future.complete(audioBuffer.toByteArray());
                }
            }

            @Override
            public void onFailure(@NonNull WebSocket ws, @NonNull Throwable t,
                                  @Nullable Response response) {
                log.error("Edge-TTS WebSocket 连接失败", t);
                future.completeExceptionally(t);
            }

            @Override
            public void onClosed(@NonNull WebSocket ws, int code, @NonNull String reason) {
                if (!future.isDone()) {
                    future.complete(audioBuffer.toByteArray());
                }
            }
        });

        try {
            byte[] audio = future.get(60, TimeUnit.SECONDS);
            if (audio.length == 0) {
                throw new RuntimeException("语音合成完成，但未收到音频数据");
            }
            return audio;
        } catch (Exception e) {
            throw new RuntimeException("语音合成超时或失败", e);
        }
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("text 不能为空");
        }
        String normalizedText = text.trim();
        if (normalizedText.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("text 长度不能超过 " + MAX_TEXT_LENGTH + " 个字符");
        }
        return normalizedText;
    }

    private String normalizeVoice(String voice) {
        if (!StringUtils.hasText(voice)) {
            return DEFAULT_VOICE;
        }
        return voice.trim();
    }

    private int normalizeRate(int rate) {
        return Math.max(-100, Math.min(100, rate));
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String escapeXml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}