package com.ymall.platform.infra.mq;

public interface ConsumerIdempotencyService {
    boolean tryAcquire(String messageKey);

    void markSuccess(String messageKey);
}
