package com.ymall.demo;

import com.ymall.platform.infra.idempotency.IdempotencyService;
import com.ymall.platform.infra.ratelimit.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoControllerTest {

    private final MockMvc mockMvc;

    DemoControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void idempotentReturnsSameResult() throws Exception {
        String key = "k-1";
        String first = mockMvc.perform(post("/demo/idempotent")
                        .header("idempotency_key", key)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(post("/demo/idempotent")
                        .header("idempotency_key", key)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void ratelimitAllowsWithinThreshold() throws Exception {
        mockMvc.perform(get("/demo/ratelimit").header("X-User-Id", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void ratelimitBlocksWhenExceeded() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/demo/ratelimit").header("X-User-Id", "block"));
        }
        mockMvc.perform(get("/demo/ratelimit").header("X-User-Id", "block"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("RATE-429"));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        IdempotencyService idempotencyService() {
            return new IdempotencyService() {
                private final Map<String, Object> store = new ConcurrentHashMap<>();

                @Override
                public <T> T execute(String key, Duration ttl, Class<T> type, Supplier<T> supplier) {
                    return type.cast(store.computeIfAbsent(key, k -> supplier.get()));
                }
            };
        }

        @Bean
        RateLimiter rateLimiter() {
            return new RateLimiter() {
                private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

                @Override
                public boolean allow(String key, long rate, long capacity) {
                    return counters.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet() <= 2;
                }
            };
        }
    }
}
