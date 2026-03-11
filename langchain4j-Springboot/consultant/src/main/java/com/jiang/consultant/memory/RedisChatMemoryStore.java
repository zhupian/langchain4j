package com.jiang.consultant.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class RedisChatMemoryStore implements ChatMemoryStore {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisChatMemoryStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        //从redis中获取会话消息
        String json = stringRedisTemplate.opsForValue().get(memoryId.toString());
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        //转成对象
        return ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> list) {
        //更新会话消息
        //转成json对象
        String json = ChatMessageSerializer.messagesToJson(list);
        //存入redis
        stringRedisTemplate.opsForValue().set(memoryId.toString(), json,24, TimeUnit.DAYS);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        //删除会话消息
        stringRedisTemplate.delete(memoryId.toString());
    }
}
