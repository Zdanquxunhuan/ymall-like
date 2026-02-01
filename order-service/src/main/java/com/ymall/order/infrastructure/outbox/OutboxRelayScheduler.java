package com.ymall.order.infrastructure.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelayScheduler {
    private final OutboxRelayWorker outboxRelayWorker;
    private final int batchSize;

    public OutboxRelayScheduler(OutboxRelayWorker outboxRelayWorker,
                                @Value("${order.outbox.batch-size:50}") int batchSize) {
        this.outboxRelayWorker = outboxRelayWorker;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${order.outbox.relay-interval-ms:500}")
    public void relay() {
        outboxRelayWorker.relayOnce(batchSize);
    }
}
