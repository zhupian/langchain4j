package com.jiang.consultant.controller;

import com.jiang.consultant.controller.dto.TtsRequest;
import com.jiang.consultant.service.EdgeTtsService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tts")
public class TtsController {

    private static final MediaType AUDIO_MPEG = MediaType.parseMediaType("audio/mpeg");

    private final EdgeTtsService edgeTtsService;

    public TtsController(EdgeTtsService edgeTtsService) {
        this.edgeTtsService = edgeTtsService;
    }

    /**
     * 文字转语音接口
     */
    @PostMapping(value = "/synthesize", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> synthesize(@RequestBody TtsRequest req) {
        validateRequest(req);

        byte[] audio = edgeTtsService.synthesize(
                req.text(),
                req.voice(),
                req.rate() == null ? 0 : req.rate()
        );

        return buildAudioResponse(audio, "attachment; filename=\"speech.mp3\"");
    }

    /**
     * 流式播放（浏览器可直接 <audio src="..."> 播放）
     */
    @GetMapping("/play")
    public ResponseEntity<byte[]> play(
            @RequestParam String text,
            @RequestParam(defaultValue = "zh-CN-XiaoxiaoNeural") String voice,
            @RequestParam(defaultValue = "0") int rate) {

        byte[] audio = edgeTtsService.synthesize(text, voice, rate);

        return buildAudioResponse(audio, null);
    }

    private ResponseEntity<byte[]> buildAudioResponse(byte[] audio, String contentDisposition) {
        return ResponseEntity.ok()
                .contentType(AUDIO_MPEG)
                .headers(headers -> {
                    if (contentDisposition != null) {
                        headers.add("Content-Disposition", contentDisposition);
                    }
                })
                .body(audio);
    }

    private void validateRequest(TtsRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (req.text() == null || req.text().isBlank()) {
            throw new IllegalArgumentException("text 不能为空");
        }
    }

    /**
     * 可用音色列表
     */
    @GetMapping("/voices")
    public List<Map<String, String>> voices() {
        return List.of(
            Map.of("id", "zh-CN-XiaoxiaoNeural",  "name", "晓晓（女·温暖）"),
            Map.of("id", "zh-CN-XiaoyiNeural",     "name", "晓依（女·活泼）"),
            Map.of("id", "zh-CN-YunxiNeural",      "name", "云希（男·少年）"),
            Map.of("id", "zh-CN-YunjianNeural",     "name", "云健（男·沉稳）"),
            Map.of("id", "zh-CN-YunyangNeural",     "name", "云扬（男·新闻）"),
            Map.of("id", "zh-CN-liaoning-XiaobeiNeural", "name", "晓北（东北话）"),
            Map.of("id", "zh-TW-HsiaoChenNeural",  "name", "曉臻（台湾女）"),
            Map.of("id", "en-US-JennyNeural",       "name", "Jenny（英文女）"),
            Map.of("id", "en-US-GuyNeural",          "name", "Guy（英文男）"),
            Map.of("id", "ja-JP-NanamiNeural",       "name", "七海（日文女）"),
            Map.of("id", "ko-KR-SunHiNeural",        "name", "선히（韩文女）")
        );
    }
}