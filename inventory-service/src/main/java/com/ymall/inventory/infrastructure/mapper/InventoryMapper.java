package com.ymall.inventory.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymall.inventory.domain.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {
    @Select("SELECT sku_id, warehouse_id, available_qty, reserved_qty, version, created_at, updated_at, deleted "
            + "FROM t_inventory WHERE sku_id = #{skuId} AND warehouse_id = #{warehouseId} AND deleted = 0 LIMIT 1")
    Inventory findOne(@Param("skuId") Long skuId, @Param("warehouseId") Long warehouseId);

    @Update("UPDATE t_inventory SET available_qty = available_qty - #{qty}, reserved_qty = reserved_qty + #{qty}, "
            + "version = version + 1, updated_at = NOW() "
            + "WHERE sku_id = #{skuId} AND warehouse_id = #{warehouseId} AND available_qty >= #{qty} "
            + "AND version = #{version} AND deleted = 0")
    int reserve(@Param("skuId") Long skuId,
                @Param("warehouseId") Long warehouseId,
                @Param("qty") Integer qty,
                @Param("version") Long version);

    @Update("UPDATE t_inventory SET available_qty = available_qty + #{qty}, reserved_qty = reserved_qty - #{qty}, "
            + "version = version + 1, updated_at = NOW() "
            + "WHERE sku_id = #{skuId} AND warehouse_id = #{warehouseId} AND reserved_qty >= #{qty} "
            + "AND version = #{version} AND deleted = 0")
    int release(@Param("skuId") Long skuId,
                @Param("warehouseId") Long warehouseId,
                @Param("qty") Integer qty,
                @Param("version") Long version);
}
