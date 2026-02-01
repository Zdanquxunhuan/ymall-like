package com.ymall.payment.application;

import com.ymall.payment.domain.PayOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentReconcileScheduler {
    private static final Logger log = LoggerFactory.getLogger(PaymentReconcileScheduler.class);

    private final PaymentApplicationService paymentApplicationService;
    private final int batchSize;

    public PaymentReconcileScheduler(PaymentApplicationService paymentApplicationService,
                                     @Value("${payment.reconcile.batch-size:50}") int batchSize) {
        this.paymentApplicationService = paymentApplicationService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${payment.reconcile.interval-ms:5000}")
    public void reconcile() {
        List<PayOrder> timeoutOrders = paymentApplicationService.findTimeoutPayingOrders();
        int processed = 0;
        for (PayOrder order : timeoutOrders) {
            if (processed >= batchSize) {
                break;
            }
            try {
                paymentApplicationService.reconcilePayOrder(order);
            } catch (Exception ex) {
                log.error("reconcile payment failed payNo={} orderNo={}", order.getPayNo(), order.getOrderNo(), ex);
            }
            processed++;
        }
    }
}
