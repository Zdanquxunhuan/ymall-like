package com.ymall.inventory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ymall.inventory.application.InventoryApplicationService;
import com.ymall.inventory.application.InventoryReserveCommand;
import com.ymall.inventory.application.InventoryReserveResult;
import com.ymall.inventory.domain.Inventory;
import com.ymall.inventory.domain.InventoryReservation;
import com.ymall.inventory.domain.InventoryReservationStatus;
import com.ymall.inventory.domain.InventoryTxn;
import com.ymall.inventory.infrastructure.mapper.InventoryMapper;
import com.ymall.inventory.infrastructure.mapper.InventoryReservationMapper;
import com.ymall.inventory.infrastructure.mapper.InventoryTxnMapper;
import com.ymall.inventory.infrastructure.mapper.OutboxEventMapper;
import com.ymall.inventory.infrastructure.redis.RedisInventoryService;
import com.ymall.platform.infra.exception.ErrorCode;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest
@ActiveProfiles("test")
class InventoryApplicationServiceTest {
    @Autowired
    private InventoryApplicationService inventoryService;

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private InventoryReservationMapper reservationMapper;

    @Autowired
    private InventoryTxnMapper txnMapper;

    @Autowired
    private OutboxEventMapper outboxEventMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private RedisInventoryService redisInventoryService;

    @MockBean
    private RocketMQTemplate rocketMQTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM t_outbox_event");
        jdbcTemplate.execute("DELETE FROM t_inventory_state_flow");
        txnMapper.delete(null);
        reservationMapper.delete(null);
        inventoryMapper.delete(null);
        Mockito.reset(redisInventoryService);
    }

    @Test
    void tryReserveSuccessShouldPersistAllRecords() {
        Inventory inventory = new Inventory();
        inventory.setSkuId(1001L);
        inventory.setWarehouseId(1L);
        inventory.setAvailableQty(100);
        inventory.setReservedQty(0);
        inventory.setVersion(0L);
        inventoryMapper.insert(inventory);

        Mockito.when(redisInventoryService.tryReserve(anyString(), anyString(), anyInt(), any()))
                .thenReturn(RedisInventoryService.ReserveStatus.SUCCESS);

        InventoryReserveCommand command = new InventoryReserveCommand("O100", 1001L, 1L, 2);
        InventoryReserveResult result = inventoryService.tryReserveAndRecord(command);

        assertThat(result.isSuccess()).isTrue();
        InventoryReservation reservation = reservationMapper.findOne("O100", 1001L, 1L);
        assertThat(reservation).isNotNull();
        assertThat(reservation.getStatus()).isEqualTo(InventoryReservationStatus.RESERVED.name());
        Inventory updated = inventoryMapper.findOne(1001L, 1L);
        assertThat(updated.getAvailableQty()).isEqualTo(98);
        assertThat(updated.getReservedQty()).isEqualTo(2);
        List<InventoryTxn> txns = txnMapper.selectList(new LambdaQueryWrapper<>());
        assertThat(txns).hasSize(1);
        assertThat(outboxEventMapper.findPending(10)).isNotEmpty();
    }

    @Test
    void tryReserveInsufficientShouldPublishFailureEvent() {
        Mockito.when(redisInventoryService.tryReserve(anyString(), anyString(), anyInt(), any()))
                .thenReturn(RedisInventoryService.ReserveStatus.INSUFFICIENT);

        InventoryReserveCommand command = new InventoryReserveCommand("O200", 1001L, 1L, 2);
        InventoryReserveResult result = inventoryService.tryReserveAndRecord(command);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.INVENTORY_INSUFFICIENT.getCode());
        assertThat(outboxEventMapper.findPending(10)).isNotEmpty();
    }

    @Test
    void releaseReservationShouldUpdateInventoryAndStatus() {
        Inventory inventory = new Inventory();
        inventory.setSkuId(1001L);
        inventory.setWarehouseId(1L);
        inventory.setAvailableQty(90);
        inventory.setReservedQty(10);
        inventory.setVersion(0L);
        inventoryMapper.insert(inventory);

        InventoryReservation reservation = new InventoryReservation();
        reservation.setOrderNo("O300");
        reservation.setSkuId(1001L);
        reservation.setWarehouseId(1L);
        reservation.setQty(10);
        reservation.setStatus(InventoryReservationStatus.RESERVED.name());
        reservation.setVersion(0L);
        reservationMapper.insert(reservation);

        InventoryReservation stored = reservationMapper.findOne("O300", 1001L, 1L);
        boolean released = inventoryService.releaseReservation(stored, "TIMEOUT_RELEASE");

        assertThat(released).isTrue();
        InventoryReservation updatedReservation = reservationMapper.findOne("O300", 1001L, 1L);
        assertThat(updatedReservation.getStatus()).isEqualTo(InventoryReservationStatus.RELEASED.name());
        Inventory updatedInventory = inventoryMapper.findOne(1001L, 1L);
        assertThat(updatedInventory.getAvailableQty()).isEqualTo(100);
        assertThat(updatedInventory.getReservedQty()).isEqualTo(0);
    }
}
