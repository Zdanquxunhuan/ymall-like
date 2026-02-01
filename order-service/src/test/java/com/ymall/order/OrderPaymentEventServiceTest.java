package com.ymall.order;

import com.ymall.order.application.OrderPaymentEventService;
import com.ymall.order.application.PaymentEventPayload;
import com.ymall.order.domain.Order;
import com.ymall.order.infrastructure.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.Instant;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {OrderServiceApplication.class, TestInfraConfig.class})
class OrderPaymentEventServiceTest {
    @Autowired
    private OrderPaymentEventService orderPaymentEventService;

    @Autowired
    private OrderMapper orderMapper;

    @MockBean
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    @Test
    void paymentSucceededUpdatesOrderToPaid() {
        Order order = new Order();
        order.setOrderNo("O-PAY-1");
        order.setUserId(1L);
        order.setAmount(new BigDecimal("88.00"));
        order.setStatus("STOCK_RESERVED");
        order.setClientRequestId("req-pay-1");
        orderMapper.insert(order);

        PaymentEventPayload payload = new PaymentEventPayload();
        payload.setEventId("evt-pay-1");
        payload.setEventType("PaymentSucceeded");
        payload.setOrderNo(order.getOrderNo());
        payload.setPayNo("P-1");
        payload.setAmount(order.getAmount());
        payload.setSchemaVersion("v1");
        payload.setOccurredAt(Instant.now());

        orderPaymentEventService.handlePaymentSucceeded(payload);

        Order refreshed = orderMapper.selectById(order.getOrderNo());
        org.junit.jupiter.api.Assertions.assertEquals("PAID", refreshed.getStatus());
    }
}
