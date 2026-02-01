package com.ymall.inventory.infrastructure.mapper;

import com.ymall.inventory.domain.MqConsumeLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;

@Mapper
public interface MqConsumeLogMapper {
    @Insert("INSERT IGNORE INTO t_mq_consume_log (event_id, consumer_group, status, created_at) "
            + "VALUES (#{eventId}, #{consumerGroup}, #{status}, #{createdAt})")
    int insert(MqConsumeLog log);

    @Select("SELECT COUNT(1) FROM t_mq_consume_log "
            + "WHERE consumer_group = #{consumerGroup} AND created_at >= #{since}")
    long countSince(@Param("consumerGroup") String consumerGroup, @Param("since") Instant since);
}
