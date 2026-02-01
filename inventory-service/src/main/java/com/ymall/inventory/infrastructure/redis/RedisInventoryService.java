package com.ymall.inventory.infrastructure.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class RedisInventoryService {
    private static final DefaultRedisScript<Long> TRY_RESERVE_SCRIPT;
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT;

    static {
        TRY_RESERVE_SCRIPT = new DefaultRedisScript<>();
        TRY_RESERVE_SCRIPT.setResultType(Long.class);
        TRY_RESERVE_SCRIPT.setScriptText("""
                local available = tonumber(redis.call('get', KEYS[1]) or '0')
                local qty = tonumber(ARGV[1])
                if redis.call('exists', KEYS[2]) == 1 then
                    return 2
                end
                if available < qty then
                    return 0
                end
                redis.call('decrby', KEYS[1], qty)
                redis.call('set', KEYS[2], qty, 'EX', tonumber(ARGV[2]))
                return 1
                """);
        RELEASE_SCRIPT = new DefaultRedisScript<>();
        RELEASE_SCRIPT.setResultType(Long.class);
        RELEASE_SCRIPT.setScriptText("""
                local qty = redis.call('get', KEYS[2])
                if not qty then
                    return 2
                end
                redis.call('incrby', KEYS[1], qty)
                redis.call('del', KEYS[2])
                return 1
                """);
    }

    private final StringRedisTemplate stringRedisTemplate;

    public RedisInventoryService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public ReserveStatus tryReserve(String inventoryKey, String reservationKey, int qty, Duration ttl) {
        Long result = stringRedisTemplate.execute(TRY_RESERVE_SCRIPT,
                List.of(inventoryKey, reservationKey),
                String.valueOf(qty), String.valueOf(ttl.getSeconds()));
        if (result == null) {
            return ReserveStatus.ERROR;
        }
        if (result == 1L) {
            return ReserveStatus.SUCCESS;
        }
        if (result == 2L) {
            return ReserveStatus.DUPLICATE;
        }
        return ReserveStatus.INSUFFICIENT;
    }

    public ReleaseStatus releaseReservation(String inventoryKey, String reservationKey) {
        Long result = stringRedisTemplate.execute(RELEASE_SCRIPT,
                List.of(inventoryKey, reservationKey));
        if (result == null) {
            return ReleaseStatus.ERROR;
        }
        if (result == 1L) {
            return ReleaseStatus.RELEASED;
        }
        return ReleaseStatus.NOT_FOUND;
    }

    public enum ReserveStatus {
        SUCCESS,
        INSUFFICIENT,
        DUPLICATE,
        ERROR
    }

    public enum ReleaseStatus {
        RELEASED,
        NOT_FOUND,
        ERROR
    }
}
