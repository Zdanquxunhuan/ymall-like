package com.ymall.payment.infrastructure.config;

import com.ymall.platform.infra.mq.ProducerTemplate;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqConfig {
    @Bean
    public ProducerTemplate producerTemplate(RocketMQTemplate rocketMQTemplate) {
        return new ProducerTemplate(rocketMQTemplate);
    }
}
