package com.ymall.platform.infra.mq;

import com.ymall.platform.infra.exception.BizException;
import com.ymall.platform.infra.exception.ErrorCode;
import com.ymall.platform.infra.trace.TraceIdUtil;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

public class ProducerTemplate {
    private static final Logger log = LoggerFactory.getLogger(ProducerTemplate.class);

    private final RocketMQTemplate rocketMQTemplate;

    public ProducerTemplate(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    public void send(String destination, String key, Object payload) {
        send(destination, key, payload, TraceIdUtil.currentTraceId());
    }

    public void send(String destination, String key, Object payload, String traceId) {
        try {
            Message<Object> message = MessageBuilder.withPayload(payload)
                    .setHeader(RocketMQHeaders.KEYS, key)
                    .setHeader(TraceIdUtil.TRACE_ID_KEY, traceId)
                    .build();
            rocketMQTemplate.syncSend(destination, message, 3000, 1);
            log.info("rocketmq send success destination={} key={}", destination, key);
        } catch (Exception ex) {
            log.error("rocketmq send failed destination={} key={}", destination, key, ex);
            throw new BizException(ErrorCode.MQ_SEND_FAILED, "消息发送失败");
        }
    }
}
