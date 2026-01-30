package com.ymall.platform.infra.idempotency;

import java.time.Duration;
import java.util.function.Supplier;

public interface IdempotencyService {
    <T> T execute(String key, Duration ttl, Class<T> type, Supplier<T> supplier);
}
