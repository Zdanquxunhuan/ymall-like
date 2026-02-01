package com.ymall.order.infrastructure.client;

import com.ymall.platform.infra.trace.TraceIdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Component
public class PaymentClient {
    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PaymentClient(RestTemplate restTemplate,
                         @Value("${order.payment.base-url:http://localhost:8084}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public void createPayment(String orderNo, BigDecimal amount, String requestId) {
        PaymentCreateRequest request = new PaymentCreateRequest();
        request.setOrderNo(orderNo);
        request.setAmount(amount);
        request.setChannel("MOCK");
        request.setClientRequestId(requestId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("idempotency_key", requestId);
        headers.add(TraceIdUtil.TRACE_ID_KEY, TraceIdUtil.currentTraceId());

        String url = baseUrl + "/payments";
        try {
            restTemplate.postForEntity(url, new HttpEntity<>(request, headers), String.class);
            log.info("payment create requested orderNo={} requestId={}", orderNo, requestId);
        } catch (Exception ex) {
            log.error("payment create failed orderNo={} requestId={}", orderNo, requestId, ex);
        }
    }
}
