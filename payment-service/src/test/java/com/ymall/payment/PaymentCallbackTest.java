package com.ymall.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymall.payment.api.dto.CreatePaymentRequest;
import com.ymall.payment.api.dto.PaymentCallbackRequest;
import com.ymall.payment.application.PaymentSignatureService;
import com.ymall.payment.infrastructure.mapper.OutboxEventMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(classes = {PaymentServiceApplication.class, TestInfraConfig.class})
class PaymentCallbackTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentSignatureService paymentSignatureService;

    @Autowired
    private OutboxEventMapper outboxEventMapper;

    @MockBean
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    @Test
    void callbackIdempotentAndEmitOutboxOnce() throws Exception {
        CreatePaymentRequest createRequest = new CreatePaymentRequest();
        createRequest.setOrderNo("O-200");
        createRequest.setAmount(new BigDecimal("99.00"));
        createRequest.setChannel("MOCK");
        createRequest.setClientRequestId("pay-create-2");

        String createPayload = objectMapper.writeValueAsString(createRequest);
        String createResp = mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode createNode = objectMapper.readTree(createResp).path("data");
        String payNo = createNode.path("payNo").asText();

        PaymentCallbackRequest callbackRequest = new PaymentCallbackRequest();
        callbackRequest.setPayNo(payNo);
        callbackRequest.setOrderNo("O-200");
        callbackRequest.setAmount(new BigDecimal("99.00"));
        callbackRequest.setStatus("SUCCESS");
        callbackRequest.setClientRequestId("cb-1");

        String signPayload = callbackRequest.getPayNo() + "|" + callbackRequest.getOrderNo() + "|"
                + callbackRequest.getAmount().toPlainString() + "|" + callbackRequest.getStatus();
        String signature = paymentSignatureService.sign(signPayload);
        String callbackPayload = objectMapper.writeValueAsString(callbackRequest);

        mockMvc.perform(post("/payments/mock-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signature)
                        .content(callbackPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", equalTo("SUCCESS")));

        mockMvc.perform(post("/payments/mock-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signature)
                        .content(callbackPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", equalTo("SUCCESS")));

        org.junit.jupiter.api.Assertions.assertEquals(1, outboxEventMapper.countByBizKey(payNo));
    }
}
