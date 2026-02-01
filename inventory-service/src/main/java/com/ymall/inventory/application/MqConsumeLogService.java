package com.ymall.inventory.application;

import com.ymall.inventory.domain.MqConsumeLog;
import com.ymall.inventory.infrastructure.mapper.MqConsumeLogMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class MqConsumeLogService {
    private final MqConsumeLogMapper consumeLogMapper;

    public MqConsumeLogService(MqConsumeLogMapper consumeLogMapper) {
        this.consumeLogMapper = consumeLogMapper;
    }

    public boolean tryInsert(String eventId, String consumerGroup) {
        MqConsumeLog log = new MqConsumeLog();
        log.setEventId(eventId);
        log.setConsumerGroup(consumerGroup);
        log.setStatus("CONSUMED");
        log.setCreatedAt(Instant.now());
        return consumeLogMapper.insert(log) > 0;
    }

    public long countSince(String consumerGroup, Instant since) {
        return consumeLogMapper.countSince(consumerGroup, since);
    }
}
