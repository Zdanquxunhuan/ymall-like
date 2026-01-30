package com.ymall.platform.infra.outbox;

import com.ymall.platform.infra.mq.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class OutboxRelayService {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    private final OutboxRepository outboxRepository;
    private final ProducerTemplate producerTemplate;

    public OutboxRelayService(OutboxRepository outboxRepository, ProducerTemplate producerTemplate) {
        this.outboxRepository = outboxRepository;
        this.producerTemplate = producerTemplate;
    }

    public void relayOnce(int batchSize) {
        List<OutboxMessage> messages = outboxRepository.findPending(batchSize);
        for (OutboxMessage message : messages) {
            try {
                String destination = message.getTopic() + ":" + message.getTag();
                producerTemplate.send(destination, message.getMessageKey(), message.getPayload());
                outboxRepository.markSuccess(message.getId());
            } catch (Exception ex) {
                log.error("outbox relay failed id={}", message.getId(), ex);
                outboxRepository.markFailed(message.getId(), ex.getMessage());
            }
        }
    }
}
