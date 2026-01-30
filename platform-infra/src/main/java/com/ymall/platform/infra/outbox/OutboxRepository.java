package com.ymall.platform.infra.outbox;

import java.util.List;

public interface OutboxRepository {
    List<OutboxMessage> findPending(int limit);

    void markSuccess(Long id);

    void markFailed(Long id, String reason);
}
