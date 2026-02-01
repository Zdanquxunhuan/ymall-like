package com.ymall.inventory.infrastructure.redis;

import com.ymall.inventory.domain.Inventory;
import com.ymall.inventory.infrastructure.mapper.InventoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InventoryRedisInitializer {
    private static final Logger log = LoggerFactory.getLogger(InventoryRedisInitializer.class);

    private final InventoryMapper inventoryMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public InventoryRedisInitializer(InventoryMapper inventoryMapper, StringRedisTemplate stringRedisTemplate) {
        this.inventoryMapper = inventoryMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void preloadInventory() {
        List<Inventory> inventories = inventoryMapper.selectList(null);
        for (Inventory inventory : inventories) {
            if (inventory.getDeleted() != null && inventory.getDeleted() != 0) {
                continue;
            }
            String key = inventoryKey(inventory.getWarehouseId(), inventory.getSkuId());
            Boolean set = stringRedisTemplate.opsForValue().setIfAbsent(
                    key, String.valueOf(inventory.getAvailableQty()));
            if (Boolean.TRUE.equals(set)) {
                log.info("inventory redis preload key={} qty={}", key, inventory.getAvailableQty());
            }
        }
    }

    private String inventoryKey(Long warehouseId, Long skuId) {
        return "inv:" + warehouseId + ":" + skuId;
    }
}
