package com.jiang.consultant.controller;

import com.jiang.consultant.aiservice.ConsultantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class ChatController {

//    @Autowired
//    private OpenAiChatModel openAiChatModel;

//    @RequestMapping("/chat")
//    public String chat(String message) {
//        //用户将来传递的问题
//        String result = openAiChatModel.chat(message);
//        return result;
//    }

    @Autowired
    private ConsultantService consultantService;

//    @RequestMapping("/chat")
//    public String chat(String message){
//        return consultantService.chat(message);
//    }

    @RequestMapping(value="/chat",produces = "text/html;charset=UTF-8")
    public Flux<String> chat(String memoryId,String message){
        return consultantService.chatStream(memoryId,message);
    }
}
