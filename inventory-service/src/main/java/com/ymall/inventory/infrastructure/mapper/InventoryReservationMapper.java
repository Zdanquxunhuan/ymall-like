package com.ymall.inventory.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymall.inventory.domain.InventoryReservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface InventoryReservationMapper extends BaseMapper<InventoryReservation> {
    @Select("SELECT order_no, sku_id, warehouse_id, qty, status, version, created_at, updated_at, deleted "
            + "FROM t_inventory_reservation WHERE order_no = #{orderNo} AND deleted = 0")
    List<InventoryReservation> findByOrderNo(@Param("orderNo") String orderNo);

    @Select("SELECT order_no, sku_id, warehouse_id, qty, status, version, created_at, updated_at, deleted "
            + "FROM t_inventory_reservation WHERE order_no = #{orderNo} AND sku_id = #{skuId} "
            + "AND warehouse_id = #{warehouseId} AND deleted = 0 LIMIT 1")
    InventoryReservation findOne(@Param("orderNo") String orderNo,
                                 @Param("skuId") Long skuId,
                                 @Param("warehouseId") Long warehouseId);

    @Update("UPDATE t_inventory_reservation SET status = #{toStatus}, version = version + 1, updated_at = NOW() "
            + "WHERE order_no = #{orderNo} AND sku_id = #{skuId} AND warehouse_id = #{warehouseId} "
            + "AND status = #{fromStatus} AND version = #{version} AND deleted = 0")
    int updateStatusCas(@Param("orderNo") String orderNo,
                        @Param("skuId") Long skuId,
                        @Param("warehouseId") Long warehouseId,
                        @Param("fromStatus") String fromStatus,
                        @Param("toStatus") String toStatus,
                        @Param("version") Long version);

    @Select("SELECT order_no, sku_id, warehouse_id, qty, status, version, created_at, updated_at, deleted "
            + "FROM t_inventory_reservation WHERE status = 'RESERVED' AND created_at <= #{cutoff} "
            + "AND deleted = 0 ORDER BY created_at ASC LIMIT #{limit}")
    List<InventoryReservation> findTimeoutReservations(@Param("cutoff") Instant cutoff,
                                                       @Param("limit") int limit);
}
