package com.ymall.platform.infra.ratelimit;

public interface RateLimiter {
    boolean allow(String key, long rate, long capacity);
}
