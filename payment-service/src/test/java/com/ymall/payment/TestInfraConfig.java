package com.ymall.payment;

import com.ymall.platform.infra.idempotency.IdempotencyService;
import com.ymall.platform.infra.ratelimit.RateLimiter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@TestConfiguration
public class TestInfraConfig {
    @Bean
    public IdempotencyService idempotencyService() {
        return new IdempotencyService() {
            private final Map<String, Object> cache = new ConcurrentHashMap<>();

            @Override
            public <T> T execute(String key, Duration ttl, Class<T> type, Supplier<T> supplier) {
                return type.cast(cache.computeIfAbsent(key, ignored -> supplier.get()));
            }
        };
    }

    @Bean
    public RateLimiter rateLimiter() {
        return (key, rate, capacity) -> true;
    }
}
