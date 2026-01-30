package com.ymall.platform.infra.mq;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisConsumerIdempotencyService implements ConsumerIdempotencyService {
    private static final String PREFIX = "mq:consume:";
    private static final Duration TTL = Duration.ofDays(3);

    private final StringRedisTemplate redisTemplate;

    public RedisConsumerIdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryAcquire(String messageKey) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(PREFIX + messageKey, "PENDING", TTL));
    }

    @Override
    public void markSuccess(String messageKey) {
        redisTemplate.opsForValue().set(PREFIX + messageKey, "DONE", TTL);
    }
}
