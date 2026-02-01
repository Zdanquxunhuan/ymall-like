package com.ymall.payment.infrastructure.mapper;

import com.ymall.payment.domain.PayCallbackLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PayCallbackLogMapper {
    @Insert("INSERT INTO t_pay_callback_log (pay_no, raw_payload, signature_valid, created_at) "
            + "VALUES (#{payNo}, #{rawPayload}, #{signatureValid}, #{createdAt})")
    int insert(PayCallbackLog log);
}
