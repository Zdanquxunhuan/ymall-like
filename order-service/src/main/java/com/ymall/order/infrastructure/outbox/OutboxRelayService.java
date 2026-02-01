package com.ymall.order.infrastructure.outbox;

import com.ymall.order.domain.OutboxEvent;
import com.ymall.order.infrastructure.mapper.OutboxEventMapper;
import com.ymall.platform.infra.mq.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OutboxRelayService {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    private final OutboxEventMapper outboxEventMapper;
    private final ProducerTemplate producerTemplate;
    private final int retryDelaySeconds;

    public OutboxRelayService(OutboxEventMapper outboxEventMapper,
                              ProducerTemplate producerTemplate,
                              @Value("${order.outbox.retry-delay-seconds:10}") int retryDelaySeconds) {
        this.outboxEventMapper = outboxEventMapper;
        this.producerTemplate = producerTemplate;
        this.retryDelaySeconds = retryDelaySeconds;
    }

    public void relayOnce(int batchSize) {
        List<OutboxEvent> pending = outboxEventMapper.findPending(batchSize);
        for (OutboxEvent event : pending) {
            String destination = event.getTopic() + ":" + event.getTag();
            try {
                producerTemplate.send(destination, event.getBizKey(), event.getPayloadJson());
                outboxEventMapper.markSuccess(event.getEventId());
                log.info("outbox relay success eventId={} destination={}", event.getEventId(), destination);
            } catch (Exception ex) {
                outboxEventMapper.markFailed(event.getEventId(), retryDelaySeconds);
                log.error("outbox relay failed eventId={} destination={}", event.getEventId(), destination, ex);
            }
        }
    }
}
