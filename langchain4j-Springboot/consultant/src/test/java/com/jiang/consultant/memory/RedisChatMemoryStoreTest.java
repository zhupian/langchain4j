package com.jiang.consultant.memory;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RedisChatMemoryStoreTest {

    @Test
    void shouldReturnEmptyListWhenNoHistoryExists() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("session-1")).thenReturn(null);

        RedisChatMemoryStore store = new RedisChatMemoryStore(redisTemplate);

        assertThat(store.getMessages("session-1")).isEmpty();
    }
}