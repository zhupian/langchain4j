package com.jiang.consultant.service;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.ByteString;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class EdgeTtsService {

    private static final String DEFAULT_VOICE = "zh-CN-XiaoxiaoNeural";
    private static final int MAX_TEXT_LENGTH = 3_000;
    private static final String TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    private static final String SEC_MS_GEC_VERSION = "1-143.0.3650.75";
    private static final String ACCEPT_ENCODING = "gzip, deflate, br, zstd";
    private static final String ACCEPT_LANGUAGE = "zh-CN,zh;q=0.9,en;q=0.8";
    private static final long WINDOWS_EPOCH_SECONDS = 11_644_473_600L;
    private static final long SEC_MS_GEC_WINDOW_SECONDS = 300L;
    private static final long WINDOWS_FILE_TIME_MULTIPLIER = 10_000_000L;
    private static final DateTimeFormatter EDGE_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern(
                            "EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'",
                            Locale.ENGLISH
                    )
                    .withZone(ZoneOffset.UTC);

    // 微软 Edge 朗读的 WebSocket 地址（免费、无需密钥）
    private static final String WS_URL =
        "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
        + "?TrustedClientToken=" + TRUSTED_CLIENT_TOKEN;

    private static final String ORIGIN =
        "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold";

    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private volatile long clockSkewSeconds = 0L;

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

        try {
            return doSynthesize(normalizedText, normalizedVoice, normalizedRate);
        } catch (EdgeTtsForbiddenException forbiddenException) {
            if (adjustClockSkew(forbiddenException.serverDateHeader())) {
                log.warn("Edge-TTS 首次请求被 403 拒绝，已根据服务端时间校准时钟后重试一次");
                try {
                    return doSynthesize(normalizedText, normalizedVoice, normalizedRate);
                } catch (EdgeTtsForbiddenException retryException) {
                    throw new RuntimeException("语音合成失败：Edge 服务拒绝连接（403 Forbidden）", retryException);
                }
            }
            throw new RuntimeException("语音合成失败：Edge 服务拒绝连接（403 Forbidden）", forbiddenException);
        }
    }

    private byte[] doSynthesize(String normalizedText, String normalizedVoice, int normalizedRate) {
        String connId = uuid();
        String requestId = uuid();
        String timestamp = edgeTimestamp();

        Request request = new Request.Builder()
                .url(buildWebsocketUrl(connId))
                .header("Origin", ORIGIN)
                .header("User-Agent", USER_AGENT)
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .header("Accept-Encoding", ACCEPT_ENCODING)
                .header("Accept-Language", ACCEPT_LANGUAGE)
                .header("Cookie", "muid=" + generateMuid() + ";")
                .build();

        CompletableFuture<byte[]> future = new CompletableFuture<>();
        ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();

        client.newWebSocket(request, new WebSocketListener() {

            @Override
            public void onOpen(@NonNull WebSocket ws, @NonNull Response response) {
                String config =
                    "X-Timestamp:" + timestamp + "\r\n"
                    + "Content-Type:application/json; charset=utf-8\r\n"
                    + "Path:speech.config\r\n\r\n"
                    + "{\"context\":{\"synthesis\":{\"audio\":{"
                    + "\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\","
                    + "\"wordBoundaryEnabled\":\"false\"},"
                    + "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n";
                ws.send(config);

                String rateStr = (normalizedRate >= 0 ? "+" : "") + normalizedRate + "%";
                String ssml =
                    "X-RequestId:" + requestId + "\r\n"
                    + "Content-Type:application/ssml+xml\r\n"
                    + "X-Timestamp:" + timestamp + "Z\r\n"
                    + "Path:ssml\r\n\r\n"
                    + "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' "
                    + "xml:lang='zh-CN'>"
                    + "<voice name='" + normalizedVoice + "'>"
                    + "<prosody pitch='+0Hz' rate='" + rateStr + "' volume='+0%'>"
                    + escapeXml(normalizedText)
                    + "</prosody></voice></speak>";
                ws.send(ssml);

                log.info("Edge-TTS 请求已发送, voice={}, textLength={}", normalizedVoice, normalizedText.length());
            }

            @Override
            public void onMessage(@NonNull WebSocket ws, @NonNull ByteString bytes) {
                try {
                    byte[] data = bytes.toByteArray();
                    if (data.length < 2) {
                        return;
                    }

                    int headerLen = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                    if (headerLen >= data.length) {
                        return;
                    }

                    String header = new String(data, 2, headerLen, StandardCharsets.UTF_8);
                    if (header.contains("Path:audio") && data.length > 2 + headerLen) {
                        audioBuffer.write(data, 2 + headerLen, data.length - 2 - headerLen);
                    }
                } catch (Exception e) {
                    log.error("解析音频数据出错", e);
                }
            }

            @Override
            public void onMessage(@NonNull WebSocket ws, @NonNull String text) {
                if (text.contains("Path:turn.end")) {
                    ws.close(1000, "done");
                    future.complete(audioBuffer.toByteArray());
                }
            }

            @Override
            public void onFailure(@NonNull WebSocket ws, @NonNull Throwable t,
                                  @Nullable Response response) {
                if (response != null && response.code() == 403) {
                    String serverDateHeader = response.header("Date");
                    log.warn("Edge-TTS WebSocket 被拒绝: status=403, date={}", serverDateHeader);
                    future.completeExceptionally(new EdgeTtsForbiddenException(serverDateHeader, t));
                    return;
                }
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
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof EdgeTtsForbiddenException forbiddenException) {
                throw forbiddenException;
            }
            throw new RuntimeException("语音合成超时或失败", cause);
        } catch (TimeoutException e) {
            throw new RuntimeException("语音合成超时或失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("语音合成超时或失败", e);
        }
    }

    private String buildWebsocketUrl(String connectionId) {
        return WS_URL
                + "&ConnectionId=" + connectionId
                + "&Sec-MS-GEC=" + generateSecMsGec()
                + "&Sec-MS-GEC-Version=" + SEC_MS_GEC_VERSION;
    }

    private String generateSecMsGec() {
        long unixTimestamp = Instant.now().getEpochSecond() + clockSkewSeconds;
        long windowsFileTimeSeconds = unixTimestamp + WINDOWS_EPOCH_SECONDS;
        windowsFileTimeSeconds -= windowsFileTimeSeconds % SEC_MS_GEC_WINDOW_SECONDS;
        long windowsFileTimeTicks = windowsFileTimeSeconds * WINDOWS_FILE_TIME_MULTIPLIER;
        String raw = windowsFileTimeTicks + TRUSTED_CLIENT_TOKEN;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.US_ASCII));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format(Locale.ROOT, "%02X", value));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new RuntimeException("生成 Edge-TTS 鉴权参数失败", e);
        }
    }

    private String generateMuid() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private String edgeTimestamp() {
        return EDGE_TIMESTAMP_FORMATTER.format(Instant.now().plusSeconds(clockSkewSeconds));
    }

    private boolean adjustClockSkew(@Nullable String serverDateHeader) {
        if (!StringUtils.hasText(serverDateHeader)) {
            return false;
        }
        try {
            long serverEpochSecond = ZonedDateTime.parse(serverDateHeader, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant()
                    .getEpochSecond();
            long localEpochSecond = Instant.now().getEpochSecond();
            clockSkewSeconds = serverEpochSecond - localEpochSecond;
            return true;
        } catch (Exception exception) {
            log.warn("解析 Edge-TTS 服务端 Date 头失败: {}", serverDateHeader, exception);
            return false;
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

    private static final class EdgeTtsForbiddenException extends RuntimeException {

        private final String serverDateHeader;

        private EdgeTtsForbiddenException(@Nullable String serverDateHeader, Throwable cause) {
            super("Edge-TTS 返回 403 Forbidden", cause);
            this.serverDateHeader = serverDateHeader;
        }

        public String serverDateHeader() {
            return serverDateHeader;
        }
    }
}