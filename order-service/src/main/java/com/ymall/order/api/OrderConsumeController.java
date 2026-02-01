package com.ymall.order.api;

import com.ymall.order.application.MqConsumeLogService;
import com.ymall.platform.infra.exception.BizException;
import com.ymall.platform.infra.exception.ErrorCode;
import com.ymall.platform.infra.model.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/orders/consumes")
public class OrderConsumeController {
    private final MqConsumeLogService consumeLogService;

    public OrderConsumeController(MqConsumeLogService consumeLogService) {
        this.consumeLogService = consumeLogService;
    }

    @GetMapping("/count")
    public Result<Map<String, Object>> count(@RequestParam(defaultValue = "order-service-group") String consumerGroup,
                                             @RequestParam(required = false) String since) {
        Instant sinceInstant = Instant.EPOCH;
        if (since != null && !since.isBlank()) {
            try {
                sinceInstant = Instant.parse(since);
            } catch (Exception ex) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "since 参数需为 ISO-8601 时间");
            }
        }
        long count = consumeLogService.countSince(consumerGroup, sinceInstant);
        return Result.ok(Map.of("consumerGroup", consumerGroup, "since", sinceInstant.toString(), "count", count));
    }
}
