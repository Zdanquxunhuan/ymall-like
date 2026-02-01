package com.ymall.order.infrastructure.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelayScheduler {
    private final OutboxRelayService outboxRelayService;
    private final int batchSize;

    public OutboxRelayScheduler(OutboxRelayService outboxRelayService,
                                @Value("${order.outbox.batch-size:50}") int batchSize) {
        this.outboxRelayService = outboxRelayService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${order.outbox.relay-interval-ms:2000}")
    public void relay() {
        outboxRelayService.relayOnce(batchSize);
    }
}
