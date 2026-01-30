package com.ymall.platform.infra.mq;

import com.ymall.platform.infra.exception.BizException;
import com.ymall.platform.infra.exception.ErrorCode;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProducerTemplate {
    private static final Logger log = LoggerFactory.getLogger(ProducerTemplate.class);

    private final RocketMQTemplate rocketMQTemplate;

    public ProducerTemplate(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    public void send(String destination, String key, Object payload) {
        try {
            rocketMQTemplate.syncSend(destination, payload, 3000, 1);
            log.info("rocketmq send success destination={} key={}", destination, key);
        } catch (Exception ex) {
            log.error("rocketmq send failed destination={} key={}", destination, key, ex);
            throw new BizException(ErrorCode.MQ_SEND_FAILED, "消息发送失败");
        }
    }
}
