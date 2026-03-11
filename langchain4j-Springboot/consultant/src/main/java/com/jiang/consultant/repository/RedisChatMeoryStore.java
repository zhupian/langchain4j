package com.jiang.consultant.repository;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
public class RedisChatMeoryStore implements ChatMemoryStore {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        //从redis中获取会话消息
        String json = stringRedisTemplate.opsForValue().get(memoryId);
        //转成对象
        List<ChatMessage> chatMessages = ChatMessageDeserializer.messagesFromJson(json);
        return  chatMessages;
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
