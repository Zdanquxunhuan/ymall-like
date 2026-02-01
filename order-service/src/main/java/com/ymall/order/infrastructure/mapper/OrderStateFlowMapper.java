package com.ymall.order.infrastructure.mapper;

import com.ymall.order.domain.OrderStateFlow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderStateFlowMapper {
    @Insert("INSERT INTO t_order_state_flow (order_no, from_status, to_status, event, event_id, trace_id, ignored_reason, created_at) "
            + "VALUES (#{orderNo}, #{fromStatus}, #{toStatus}, #{event}, #{eventId}, #{traceId}, #{ignoredReason}, #{createdAt})")
    int insert(OrderStateFlow flow);

    @org.apache.ibatis.annotations.Select("SELECT order_no, from_status, to_status, event, event_id, trace_id, ignored_reason, created_at "
            + "FROM t_order_state_flow WHERE order_no = #{orderNo} ORDER BY created_at ASC")
    java.util.List<OrderStateFlow> findByOrderNo(@org.apache.ibatis.annotations.Param("orderNo") String orderNo);
}
