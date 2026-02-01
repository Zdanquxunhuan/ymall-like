package com.ymall.payment.infrastructure.mapper;

import com.ymall.payment.domain.PayStateFlow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PayStateFlowMapper {
    @Insert("INSERT INTO t_pay_state_flow (pay_no, order_no, from_status, to_status, event, event_id, trace_id, ignored_reason, created_at) "
            + "VALUES (#{payNo}, #{orderNo}, #{fromStatus}, #{toStatus}, #{event}, #{eventId}, #{traceId}, #{ignoredReason}, #{createdAt})")
    int insert(PayStateFlow flow);
}
