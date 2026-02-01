package com.ymall.order.infrastructure.mapper;

import com.ymall.order.domain.OutboxEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OutboxEventMapper {
    @Insert("INSERT INTO t_outbox_event (event_id, biz_key, topic, tag, payload_json, status, retry_count, next_retry_at, created_at) "
            + "VALUES (#{eventId}, #{bizKey}, #{topic}, #{tag}, #{payloadJson}, #{status}, #{retryCount}, #{nextRetryAt}, #{createdAt})")
    int insert(OutboxEvent event);

    @Select("SELECT event_id, biz_key, topic, tag, payload_json, status, retry_count, next_retry_at, created_at "
            + "FROM t_outbox_event "
            + "WHERE status IN ('NEW','FAILED') AND (next_retry_at IS NULL OR next_retry_at <= NOW()) "
            + "ORDER BY created_at ASC LIMIT #{limit}")
    List<OutboxEvent> findPending(@Param("limit") int limit);

    @Update("UPDATE t_outbox_event SET status = 'SENT' WHERE event_id = #{eventId}")
    int markSuccess(@Param("eventId") String eventId);

    @Update("UPDATE t_outbox_event SET status = 'FAILED', retry_count = retry_count + 1, "
            + "next_retry_at = DATE_ADD(NOW(), INTERVAL #{delaySeconds} SECOND) WHERE event_id = #{eventId}")
    int markFailed(@Param("eventId") String eventId, @Param("delaySeconds") int delaySeconds);
}
