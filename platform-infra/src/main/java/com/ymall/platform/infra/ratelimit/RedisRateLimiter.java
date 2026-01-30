package com.ymall.platform.infra.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisRateLimiter implements RateLimiter {
    private static final String SCRIPT = "local key = KEYS[1] "
            + "local rate = tonumber(ARGV[1]) "
            + "local capacity = tonumber(ARGV[2]) "
            + "local now = tonumber(ARGV[3]) "
            + "local tokens = tonumber(redis.call('GET', key .. ':tokens') or capacity) "
            + "local last = tonumber(redis.call('GET', key .. ':ts') or now) "
            + "local delta = math.max(0, now - last) "
            + "local filled = math.min(capacity, tokens + delta * rate) "
            + "local allowed = filled >= 1 "
            + "if allowed then filled = filled - 1 end "
            + "redis.call('SET', key .. ':tokens', filled) "
            + "redis.call('SET', key .. ':ts', now) "
            + "redis.call('EXPIRE', key .. ':tokens', math.ceil(capacity / rate) + 1) "
            + "redis.call('EXPIRE', key .. ':ts', math.ceil(capacity / rate) + 1) "
            + "return allowed";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Boolean> script;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(SCRIPT, Boolean.class);
    }

    @Override
    public boolean allow(String key, long rate, long capacity) {
        long nowSeconds = System.currentTimeMillis() / 1000;
        Boolean result = redisTemplate.execute(script, List.of(key), String.valueOf(rate), String.valueOf(capacity), String.valueOf(nowSeconds));
        return Boolean.TRUE.equals(result);
    }
}
