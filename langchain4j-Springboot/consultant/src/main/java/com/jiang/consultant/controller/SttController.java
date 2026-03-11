package com.jiang.consultant.controller;

import com.jiang.consultant.controller.dto.SttResponse;
import com.jiang.consultant.service.WhisperSttService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/stt")
public class SttController {

    private static final long MAX_FILE_SIZE = 25L * 1024 * 1024;

    private final WhisperSttService whisperSttService;

    public SttController(WhisperSttService whisperSttService) {
        this.whisperSttService = whisperSttService;
    }

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SttResponse transcribe(@RequestPart("file") MultipartFile file,
                                  @RequestParam(required = false) String language,
                                  @RequestParam(required = false) String prompt) {
        validateFile(file);
        String text = whisperSttService.transcribe(file, language, prompt);
        return new SttResponse(text);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("音频文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("音频文件不能超过 25MB");
        }
    }
}