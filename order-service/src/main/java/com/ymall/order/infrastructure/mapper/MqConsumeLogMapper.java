package com.ymall.order.infrastructure.mapper;

import com.ymall.order.domain.MqConsumeLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

@Mapper
public interface MqConsumeLogMapper {
    @Insert("INSERT IGNORE INTO t_mq_consume_log (event_id, consumer_group, status, created_at) "
            + "VALUES (#{eventId}, #{consumerGroup}, #{status}, #{createdAt})")
    int insert(MqConsumeLog log);

    @Select("SELECT COUNT(1) FROM t_mq_consume_log "
            + "WHERE consumer_group = #{consumerGroup} AND created_at >= #{since}")
    long countSince(@Param("consumerGroup") String consumerGroup, @Param("since") Instant since);

    @Update("UPDATE t_mq_consume_log SET status = #{status} "
            + "WHERE event_id = #{eventId} AND consumer_group = #{consumerGroup}")
    int updateStatus(@Param("eventId") String eventId,
                     @Param("consumerGroup") String consumerGroup,
                     @Param("status") String status);
}
