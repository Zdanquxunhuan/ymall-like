package com.ymall.platform.infra.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ConsumerTemplate<T> {
    private static final Logger log = LoggerFactory.getLogger(ConsumerTemplate.class);

    public void onMessage(T payload) {
        try {
            handle(payload);
            log.info("rocketmq consume success payload={}", payload);
        } catch (Exception ex) {
            log.error("rocketmq consume failed payload={}", payload, ex);
            throw ex;
        }
    }

    protected abstract void handle(T payload);
}
