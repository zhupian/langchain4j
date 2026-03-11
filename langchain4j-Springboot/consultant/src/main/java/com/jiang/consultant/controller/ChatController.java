package com.jiang.consultant.controller;

import com.jiang.consultant.ai.ConsultantService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class ChatController {

    private final ConsultantService consultantService;

    public ChatController(ConsultantService consultantService) {
        this.consultantService = consultantService;
    }

    @GetMapping(value = "/chat", produces = "text/plain;charset=UTF-8")
    public Flux<String> chat(@RequestParam String memoryId, @RequestParam String message) {
        if (!StringUtils.hasText(memoryId)) {
            return Flux.error(new IllegalArgumentException("memoryId 不能为空"));
        }
        if (!StringUtils.hasText(message)) {
            return Flux.error(new IllegalArgumentException("message 不能为空"));
        }
        return consultantService.chatStream(memoryId.trim(), message.trim());
    }
}
