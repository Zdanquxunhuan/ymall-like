package com.ymall.payment.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymall.payment.domain.PayOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface PayOrderMapper extends BaseMapper<PayOrder> {
    @Select("SELECT * FROM t_pay_order WHERE order_no = #{orderNo} AND deleted = 0")
    PayOrder findByOrderNo(@Param("orderNo") String orderNo);

    @Update("UPDATE t_pay_order SET status = #{toStatus}, version = version + 1, updated_at = NOW() "
            + "WHERE pay_no = #{payNo} AND status = #{fromStatus} AND version = #{version} AND deleted = 0")
    int updateStatusCas(@Param("payNo") String payNo,
                        @Param("fromStatus") String fromStatus,
                        @Param("toStatus") String toStatus,
                        @Param("version") Long version);

    @Update("UPDATE t_pay_order SET status = #{toStatus}, version = version + 1, updated_at = NOW() "
            + "WHERE pay_no = #{payNo} AND status = #{fromStatus} AND deleted = 0")
    int updateStatusByStatus(@Param("payNo") String payNo,
                             @Param("fromStatus") String fromStatus,
                             @Param("toStatus") String toStatus);

    @Select("SELECT * FROM t_pay_order WHERE status = 'PAYING' AND updated_at <= #{deadline} AND deleted = 0")
    List<PayOrder> findTimeoutPaying(@Param("deadline") Instant deadline);
}
