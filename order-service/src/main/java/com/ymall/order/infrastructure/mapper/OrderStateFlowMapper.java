package com.ymall.order.infrastructure.mapper;

import com.ymall.order.domain.OrderStateFlow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderStateFlowMapper {
    @Insert("INSERT INTO t_order_state_flow (order_no, from_status, to_status, event, event_id, trace_id, created_at) "
            + "VALUES (#{orderNo}, #{fromStatus}, #{toStatus}, #{event}, #{eventId}, #{traceId}, #{createdAt})")
    int insert(OrderStateFlow flow);
}
