package com.ymall.inventory.infrastructure.scheduler;

import com.ymall.inventory.application.InventoryApplicationService;
import com.ymall.inventory.domain.InventoryReservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "inventory.reservation-timeout", name = "enabled", havingValue = "true")
public class ReservationTimeoutScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReservationTimeoutScheduler.class);

    private final InventoryApplicationService inventoryService;
    private final long timeoutMinutes;
    private final int batchSize;

    public ReservationTimeoutScheduler(InventoryApplicationService inventoryService,
                                       @Value("${inventory.reservation-timeout.minutes:15}") long timeoutMinutes,
                                       @Value("${inventory.reservation-timeout.batch-size:50}") int batchSize) {
        this.inventoryService = inventoryService;
        this.timeoutMinutes = timeoutMinutes;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${inventory.reservation-timeout.interval-ms:60000}")
    public void scanTimeout() {
        Instant cutoff = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);
        List<InventoryReservation> reservations = inventoryService.findTimeoutReservations(cutoff, batchSize);
        for (InventoryReservation reservation : reservations) {
            try {
                boolean released = inventoryService.releaseReservation(reservation, "TIMEOUT_RELEASE");
                if (released) {
                    inventoryService.syncRedisReservationRelease(reservation);
                    log.info("reservation timeout released orderNo={} skuId={} warehouseId={}",
                            reservation.getOrderNo(), reservation.getSkuId(), reservation.getWarehouseId());
                }
            } catch (Exception ex) {
                log.error("reservation timeout release failed orderNo={} skuId={} warehouseId={}",
                        reservation.getOrderNo(), reservation.getSkuId(), reservation.getWarehouseId(), ex);
            }
        }
    }
}
