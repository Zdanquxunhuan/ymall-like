package com.ymall.payment;

import com.ymall.payment.application.PaymentApplicationService;
import com.ymall.payment.domain.PayOrder;
import com.ymall.payment.infrastructure.mapper.OutboxEventMapper;
import com.ymall.payment.infrastructure.mapper.PayOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {PaymentServiceApplication.class, TestInfraConfig.class})
class PaymentReconcileTest {
    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private PayOrderMapper payOrderMapper;

    @Autowired
    private OutboxEventMapper outboxEventMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    @Test
    void reconcileTimeoutPayingOrder() {
        String payNo = findEvenHashPayNo();
        PayOrder order = new PayOrder();
        order.setPayNo(payNo);
        order.setOrderNo("O-300");
        order.setAmount(new BigDecimal("66.00"));
        order.setStatus("PAYING");
        order.setChannel("MOCK");
        payOrderMapper.insert(order);

        Instant oldTime = Instant.now().minusSeconds(5);
        jdbcTemplate.update("UPDATE t_pay_order SET updated_at = ? WHERE pay_no = ?", oldTime, payNo);

        List<PayOrder> timeoutOrders = paymentApplicationService.findTimeoutPayingOrders();
        org.junit.jupiter.api.Assertions.assertFalse(timeoutOrders.isEmpty());

        paymentApplicationService.reconcilePayOrder(timeoutOrders.get(0));

        PayOrder refreshed = payOrderMapper.selectById(payNo);
        org.junit.jupiter.api.Assertions.assertEquals("SUCCESS", refreshed.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(1, outboxEventMapper.countByBizKey(payNo));
    }

    private String findEvenHashPayNo() {
        int idx = 0;
        while (true) {
            String candidate = "P-RECON-" + idx++;
            if (Math.abs(candidate.hashCode()) % 2 == 0) {
                return candidate;
            }
        }
    }
}
