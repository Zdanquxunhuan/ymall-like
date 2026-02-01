package com.ymall.order.infrastructure.mapper;

import com.ymall.order.domain.OrderItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderItemMapper {
    @Insert({"<script>",
            "INSERT INTO t_order_item (order_no, sku_id, qty, title_snapshot, price_snapshot, promo_snapshot_json)",
            "VALUES",
            "<foreach collection='items' item='item' separator=','>",
            "(#{item.orderNo}, #{item.skuId}, #{item.qty}, #{item.titleSnapshot}, #{item.priceSnapshot}, #{item.promoSnapshotJson})",
            "</foreach>",
            "</script>"})
    int insertBatch(@Param("items") List<OrderItem> items);

    @Select("SELECT order_no, sku_id, qty, title_snapshot, price_snapshot, promo_snapshot_json "
            + "FROM t_order_item WHERE order_no = #{orderNo}")
    List<OrderItem> findByOrderNo(@Param("orderNo") String orderNo);
}
