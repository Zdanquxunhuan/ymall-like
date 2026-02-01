package com.ymall.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymall.payment.api.dto.CreatePaymentRequest;
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
class PaymentControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    @Test
    void idempotentCreatePaymentByOrderNo() throws Exception {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setOrderNo("O-100");
        request.setAmount(new BigDecimal("88.00"));
        request.setChannel("MOCK");
        request.setClientRequestId("pay-create-1");

        String payload = objectMapper.writeValueAsString(request);

        String first = mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String second = mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstPayNo = objectMapper.readTree(first).path("data").path("payNo").asText();
        String secondPayNo = objectMapper.readTree(second).path("data").path("payNo").asText();
        org.junit.jupiter.api.Assertions.assertEquals(firstPayNo, secondPayNo);
    }
}
