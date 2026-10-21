package com.neram.pos.config;

import com.neram.pos.service.NotificationStreamService;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JPA lifecycle callback shared by Bill, BillItem and DiningTable. Any insert/update/delete of those rows tells
 * connected POS terminals (via the SSE "orders-changed" signal) to re-read /api/tables and /api/bills/active.
 * The signal is sent only after the surrounding transaction commits and is coalesced to one per transaction.
 *
 * Hibernate may create its own instance of this listener, so the service is held statically.
 */
@Component
public class OrderChangeListener {

    private static volatile NotificationStreamService streamService;

    @Autowired
    public void setStreamService(NotificationStreamService service) {
        OrderChangeListener.streamService = service;
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    public void onChange(Object entity) {
        NotificationStreamService service = streamService;
        if (service != null) {
            service.publishOrdersChangedAfterCommit();
        }
    }
}
