package com.ymall.order.infrastructure.mq;

import com.ymall.platform.infra.mq.ConsumerIdempotencyService;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@Profile("!test")
@RocketMQMessageListener(topic = "order-events", consumerGroup = "order-service-group",
        selectorExpression = "OrderCreated||OrderCanceled")
public class OrderEventConsumer implements RocketMQListener<MessageExt> {
    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ConsumerIdempotencyService idempotencyService;

    public OrderEventConsumer(ConsumerIdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Override
    public void onMessage(MessageExt message) {
        String messageKey = message.getKeys();
        if (!idempotencyService.tryAcquire(messageKey)) {
            log.info("rocketmq consume skip duplicate key={}", messageKey);
            return;
        }
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        log.info("rocketmq consume order event key={} tags={} payload={}", messageKey, message.getTags(), payload);
        idempotencyService.markSuccess(messageKey);
    }
}
