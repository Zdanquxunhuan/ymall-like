package com.ymall.order.infrastructure.outbox;

import com.ymall.order.domain.OutboxEvent;
import com.ymall.order.infrastructure.mapper.OutboxEventMapper;
import com.ymall.platform.infra.mq.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxRelayWorker {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelayWorker.class);

    private final OutboxEventMapper outboxEventMapper;
    private final ProducerTemplate producerTemplate;
    private final String fixedTag;
    private final int maxRetries;
    private final int retryBaseSeconds;
    private final int retryMaxSeconds;

    public OutboxRelayWorker(OutboxEventMapper outboxEventMapper,
                             ProducerTemplate producerTemplate,
                             @Value("${order.outbox.tag:OrderEvent}") String fixedTag,
                             @Value("${order.outbox.max-retries:6}") int maxRetries,
                             @Value("${order.outbox.retry-base-seconds:2}") int retryBaseSeconds,
                             @Value("${order.outbox.retry-max-seconds:300}") int retryMaxSeconds) {
        this.outboxEventMapper = outboxEventMapper;
        this.producerTemplate = producerTemplate;
        this.fixedTag = fixedTag;
        this.maxRetries = maxRetries;
        this.retryBaseSeconds = retryBaseSeconds;
        this.retryMaxSeconds = retryMaxSeconds;
    }

    public void relayOnce(int batchSize) {
        List<OutboxEvent> pending = outboxEventMapper.findPending(batchSize);
        for (OutboxEvent event : pending) {
            if (outboxEventMapper.markProcessing(event.getEventId()) == 0) {
                continue;
            }
            String destination = event.getTopic() + ":" + fixedTag;
            String traceId = event.getTraceId() == null ? "unknown" : event.getTraceId();
            try {
                producerTemplate.send(destination, event.getEventId(), event.getPayloadJson(), traceId);
                outboxEventMapper.markSuccess(event.getEventId());
                log.info("outbox relay success eventId={} destination={} traceId={}",
                        event.getEventId(), destination, traceId);
            } catch (Exception ex) {
                handleFailure(event, destination, ex);
            }
        }
    }

    private void handleFailure(OutboxEvent event, String destination, Exception ex) {
        String traceId = event.getTraceId() == null ? "unknown" : event.getTraceId();
        int currentRetry = event.getRetryCount() == null ? 0 : event.getRetryCount();
        int nextRetry = currentRetry + 1;
        if (nextRetry >= maxRetries) {
            outboxEventMapper.markDead(event.getEventId());
            log.error("ALARM outbox relay dead-letter eventId={} destination={} retryCount={} traceId={} at={}",
                    event.getEventId(), destination, nextRetry, traceId, Instant.now(), ex);
            return;
        }
        int delaySeconds = computeDelaySeconds(nextRetry);
        outboxEventMapper.markRetry(event.getEventId(), delaySeconds);
        log.warn("outbox relay retry scheduled eventId={} destination={} retryCount={} delaySeconds={} traceId={}",
                event.getEventId(), destination, nextRetry, delaySeconds, traceId, ex);
    }

    private int computeDelaySeconds(int retryCount) {
        long delay = (long) retryBaseSeconds * (1L << Math.max(0, retryCount - 1));
        return (int) Math.min(delay, retryMaxSeconds);
    }
}
