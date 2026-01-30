package com.ymall.demo.api;

import com.ymall.platform.infra.exception.BizException;
import com.ymall.platform.infra.exception.ErrorCode;
import com.ymall.platform.infra.idempotency.IdempotencyService;
import com.ymall.platform.infra.model.Result;
import com.ymall.platform.infra.ratelimit.RateLimiter;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/demo")
public class DemoController {
    private final IdempotencyService idempotencyService;
    private final RateLimiter rateLimiter;

    public DemoController(IdempotencyService idempotencyService, RateLimiter rateLimiter) {
        this.idempotencyService = idempotencyService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/idempotent")
    public Result<Map<String, String>> idempotent(@RequestHeader("idempotency_key") @NotBlank String key) {
        Map<String, String> payload = idempotencyService.execute(key, Duration.ofMinutes(5), Map.class,
                () -> Map.of("requestId", key, "result", UUID.randomUUID().toString()));
        return Result.ok(payload);
    }

    @GetMapping("/ratelimit")
    public Result<Map<String, String>> ratelimit(
        @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        String key = "demo:ratelimit:/demo/ratelimit:" + userId;
        if (!rateLimiter.allow(key, 200, 200)) {
            throw new BizException(ErrorCode.RATE_LIMITED, "触发限流");
        }
        return Result.ok(Map.of("status", "ok"));
    }
}
