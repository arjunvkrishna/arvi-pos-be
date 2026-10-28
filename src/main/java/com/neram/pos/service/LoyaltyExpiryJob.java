package com.neram.pos.service;

import com.neram.pos.util.BusinessDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Lapses the points of customers who have not bought within the validity period, so the balances the screens show are the real ones. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoyaltyExpiryJob {

    private final CustomerService customers;

    /** Expires the lapsed points of the cafe the thread is working for. Started once a day per cafe by {@link TenantMaintenanceScheduler}. */
    public int expireLapsedPoints() {
        return run(BusinessDate.now());
    }

    /** @return the number of customers whose points were expired */
    int run(LocalDate today) {
        int expired = 0;
        for (Long id : customers.idsDueForExpiry(today)) {
            try {
                if (customers.expireOne(id, today) > 0) expired++;
            } catch (Exception e) {
                log.warn("Could not expire points for customer {}: {}", id, e.getMessage());
            }
        }
        if (expired > 0) log.info("Expired the loyalty points of {} customer(s)", expired);
        return expired;
    }
}
