package com.ymall.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymall.order.api.dto.CreateOrderRequest;
import com.ymall.order.api.dto.CreateOrderItemRequest;
import com.ymall.order.domain.Order;
import com.ymall.order.infrastructure.mapper.OrderMapper;
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
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(classes = {OrderServiceApplication.class, TestInfraConfig.class})
class OrderControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderMapper orderMapper;

    @MockBean
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    @Test
    void idempotentCreateOrder() throws Exception {
        CreateOrderRequest request = buildRequest("req-1");
        String payload = objectMapper.writeValueAsString(request);

        String first = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String second = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstOrderNo = objectMapper.readTree(first).path("data").path("orderNo").asText();
        String secondOrderNo = objectMapper.readTree(second).path("data").path("orderNo").asText();
        org.junit.jupiter.api.Assertions.assertEquals(firstOrderNo, secondOrderNo);
    }

    @Test
    void repeatCancelOrder() throws Exception {
        CreateOrderRequest request = buildRequest("req-2");
        String payload = objectMapper.writeValueAsString(request);

        String response = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String orderNo = objectMapper.readTree(response).path("data").path("orderNo").asText();

        mockMvc.perform(post("/orders/{orderNo}/cancel", orderNo)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", equalTo("CANCELED")));

        mockMvc.perform(post("/orders/{orderNo}/cancel", orderNo)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", equalTo("CANCELED")));
    }

    @Test
    void cancelOrderWithInvalidState() throws Exception {
        CreateOrderRequest request = buildRequest("req-3");
        String payload = objectMapper.writeValueAsString(request);

        String response = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String orderNo = objectMapper.readTree(response).path("data").path("orderNo").asText();

        Order order = orderMapper.selectById(orderNo);
        order.setStatus("PAID");
        orderMapper.updateById(order);

        mockMvc.perform(post("/orders/{orderNo}/cancel", orderNo)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("ORDER-409")));
    }

    private CreateOrderRequest buildRequest(String clientRequestId) {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setSkuId(1001L);
        item.setQty(1);
        item.setTitleSnapshot("测试商品");
        item.setPriceSnapshot(new BigDecimal("88.00"));
        item.setPromoSnapshotJson("{}");

        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(1L);
        request.setAmount(new BigDecimal("88.00"));
        request.setClientRequestId(clientRequestId);
        request.setItems(List.of(item));
        return request;
    }
}
