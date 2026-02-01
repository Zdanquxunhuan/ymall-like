package com.ymall.inventory.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymall.inventory.domain.InventoryTxn;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryTxnMapper extends BaseMapper<InventoryTxn> {
}
