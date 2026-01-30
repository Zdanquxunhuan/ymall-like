package com.ymall.platform.infra.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymall.platform.infra.exception.BizException;
import com.ymall.platform.infra.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class RedisIdempotencyService implements IdempotencyService {
    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyService.class);
    private static final String PREFIX = "idem:";
    private static final String PENDING = "PENDING";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisIdempotencyService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> T execute(String key, Duration ttl, Class<T> type, Supplier<T> supplier) {
        String redisKey = PREFIX + key;
        Boolean set = redisTemplate.opsForValue().setIfAbsent(redisKey, PENDING, ttl);
        if (Boolean.TRUE.equals(set)) {
            T result = supplier.get();
            try {
                redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(result), ttl);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize idempotent result", e);
            }
            return result;
        }
        String cached = redisTemplate.opsForValue().get(redisKey);
        int retries = 10;
        while (PENDING.equals(cached) && retries-- > 0) {
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            cached = redisTemplate.opsForValue().get(redisKey);
        }
        if (cached == null || PENDING.equals(cached)) {
            throw new BizException(ErrorCode.IDEMPOTENT_CONFLICT, "请求处理中，请稍后重试");
        }
        try {
            return objectMapper.readValue(cached, type);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.IDEMPOTENT_CONFLICT, "幂等结果解析失败");
        }
    }
}
