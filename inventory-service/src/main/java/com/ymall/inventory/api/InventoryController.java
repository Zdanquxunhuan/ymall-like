package com.ymall.inventory.api;

import com.ymall.inventory.api.dto.InventoryReserveRequest;
import com.ymall.inventory.api.dto.InventoryReservationView;
import com.ymall.inventory.api.dto.InventoryView;
import com.ymall.inventory.application.InventoryApplicationService;
import com.ymall.inventory.application.InventoryReserveCommand;
import com.ymall.inventory.application.InventoryReserveResult;
import com.ymall.inventory.domain.Inventory;
import com.ymall.inventory.domain.InventoryReservation;
import com.ymall.platform.infra.exception.BizException;
import com.ymall.platform.infra.exception.ErrorCode;
import com.ymall.platform.infra.idempotency.IdempotencyService;
import com.ymall.platform.infra.model.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/inventory")
public class InventoryController {
    private final InventoryApplicationService inventoryService;
    private final IdempotencyService idempotencyService;

    public InventoryController(InventoryApplicationService inventoryService,
                               IdempotencyService idempotencyService) {
        this.inventoryService = inventoryService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/{skuId}")
    public Result<InventoryView> getInventory(@PathVariable("skuId") Long skuId,
                                              @RequestParam("warehouseId") Long warehouseId) {
        Inventory inventory = inventoryService.getInventory(skuId, warehouseId);
        if (inventory == null) {
            throw new BizException(ErrorCode.INVENTORY_NOT_FOUND, "库存不存在");
        }
        return Result.ok(toInventoryView(inventory));
    }

    @GetMapping("/reservations")
    public Result<List<InventoryReservationView>> getReservations(@RequestParam("orderNo") String orderNo) {
        List<InventoryReservationView> views = inventoryService.getReservationsByOrderNo(orderNo)
                .stream()
                .map(this::toReservationView)
                .collect(Collectors.toList());
        return Result.ok(views);
    }

    @PostMapping("/reservations/try")
    public Result<InventoryReservationView> tryReserve(
            @RequestHeader(value = "idempotency_key", required = false) String idempotencyKey,
            @Valid @RequestBody InventoryReserveRequest request) {
        String resolvedKey = resolveIdempotencyKey(idempotencyKey, request.getClientRequestId());
        InventoryReserveResult result = idempotencyService.execute(
                resolvedKey,
                Duration.ofMinutes(5),
                InventoryReserveResult.class,
                () -> inventoryService.tryReserveAndRecord(toCommand(request))
        );
        if (!result.isSuccess()) {
            if (ErrorCode.INVENTORY_INSUFFICIENT.getCode().equals(result.getErrorCode())) {
                throw new BizException(ErrorCode.INVENTORY_INSUFFICIENT, result.getErrorReason());
            }
            if (ErrorCode.VALIDATION_ERROR.getCode().equals(result.getErrorCode())) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, result.getErrorReason());
            }
            throw new BizException(ErrorCode.INVENTORY_RESERVE_FAILED, result.getErrorReason());
        }
        return Result.ok(toReservationView(result.getReservation()));
    }

    private String resolveIdempotencyKey(String headerKey, String clientRequestId) {
        String key = headerKey != null && !headerKey.isBlank() ? headerKey : clientRequestId;
        if (key == null || key.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "idempotency_key 或 clientRequestId 不能为空");
        }
        return key;
    }

    private InventoryReserveCommand toCommand(InventoryReserveRequest request) {
        return new InventoryReserveCommand(
                request.getOrderNo(),
                request.getSkuId(),
                request.getWarehouseId(),
                request.getQty()
        );
    }

    private InventoryView toInventoryView(Inventory inventory) {
        InventoryView view = new InventoryView();
        view.setSkuId(inventory.getSkuId());
        view.setWarehouseId(inventory.getWarehouseId());
        view.setAvailableQty(inventory.getAvailableQty());
        view.setReservedQty(inventory.getReservedQty());
        view.setUpdatedAt(inventory.getUpdatedAt());
        return view;
    }

    private InventoryReservationView toReservationView(InventoryReservation reservation) {
        InventoryReservationView view = new InventoryReservationView();
        view.setOrderNo(reservation.getOrderNo());
        view.setSkuId(reservation.getSkuId());
        view.setWarehouseId(reservation.getWarehouseId());
        view.setQty(reservation.getQty());
        view.setStatus(reservation.getStatus());
        view.setCreatedAt(reservation.getCreatedAt());
        view.setUpdatedAt(reservation.getUpdatedAt());
        return view;
    }
}
