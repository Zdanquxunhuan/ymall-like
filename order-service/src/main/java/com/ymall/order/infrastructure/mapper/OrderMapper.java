package com.ymall.order.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymall.order.domain.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {
    @Update("UPDATE t_order SET status = #{toStatus}, version = version + 1, updated_at = NOW() "
            + "WHERE order_no = #{orderNo} AND status = #{fromStatus} AND version = #{version} AND deleted = 0")
    int updateStatusCas(@Param("orderNo") String orderNo,
                        @Param("fromStatus") String fromStatus,
                        @Param("toStatus") String toStatus,
                        @Param("version") Long version);

    @Update("UPDATE t_order SET status = #{toStatus}, version = version + 1, updated_at = NOW() "
            + "WHERE order_no = #{orderNo} AND status = #{fromStatus} AND deleted = 0")
    int updateStatusByStatus(@Param("orderNo") String orderNo,
                             @Param("fromStatus") String fromStatus,
                             @Param("toStatus") String toStatus);
}
