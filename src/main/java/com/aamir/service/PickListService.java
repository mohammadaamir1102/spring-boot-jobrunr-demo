package com.aamir.service;

import com.aamir.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PickListService {

    /**
     * Generates a pick-list (warehouse "walk order") for a single order.
     * This is the unit of work executed once PER ORDER inside the BATCH job
     * (see BulkPickListBatchJobRequest) — JobRunr creates one child job per
     * item in the batch, all of which can be spread across every worker node.
     */
    public void generatePickListFor(Order order) {
        log.info("Generating pick-list for order {} (SKU {}, qty {}) in warehouse {}",
                order.getId(), order.getSku(), order.getQuantity(), order.getWarehouseCode());
        // simulate: compute bin location, aisle sequence, printer dispatch, etc.
    }

    /**
     * Runs once the whole batch of child jobs finishes successfully.
     * This is the CHAINED / continuation job.
     */
    public void validateAllPickListsGenerated(int totalOrders) {
        log.info("Batch complete: validated pick-lists for {} orders", totalOrders);
    }
}