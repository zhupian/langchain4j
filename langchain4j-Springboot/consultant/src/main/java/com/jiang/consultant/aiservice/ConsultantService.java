package com.jiang.consultant.aiservice;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;


@AiService(
        //表示我要手动装配
        wiringMode = AiServiceWiringMode.EXPLICIT,
        //指定模型
        chatModel = "openAiChatModel",
        streamingChatModel = "openAiStreamingChatModel",
//        chatMemory = "chatMemory"//配置会话记忆对象
        chatMemoryProvider = "chatMemoryProvider"//配置会话记忆对象
)
//@AiService
public interface ConsultantService {
    //用于聊天的方法
    //public String chat(String message);
    @SystemMessage(fromResource = "system.txt")
    public  Flux<String> chatStream(
            @MemoryId String memoryId,
            @UserMessage String message
    );
}
